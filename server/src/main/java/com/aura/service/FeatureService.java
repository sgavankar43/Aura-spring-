package com.aura.service;

import com.aura.dto.EvaluationResponse;
import com.aura.dto.FeatureRequest;
import com.aura.dto.FeatureRowDto;
import com.aura.entity.*;
import com.aura.exception.ApiException;
import com.aura.repository.*;
import com.aura.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FeatureService {

    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final FeatureRepository featureRepository;
    private final FeatureStateRepository featureStateRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public FeatureService(
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            FeatureRepository featureRepository,
            FeatureStateRepository featureStateRepository,
            UserRepository userRepository,
            AuditLogService auditLogService) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.featureRepository = featureRepository;
        this.featureStateRepository = featureStateRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<FeatureRowDto> getFeaturesForProject(UUID projectId) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        List<Environment> environments = environmentRepository.findByProjectIdOrderBySortOrderAsc(project.getId());
        List<Feature> features = featureRepository.findByProjectId(project.getId());

        return features.stream().map(feature -> {
            Map<String, Boolean> states = new HashMap<>();

            for (Environment env : environments) {
                Boolean stateValue = featureStateRepository.findByFeatureIdAndEnvironmentId(feature.getId(), env.getId())
                        .map(FeatureState::getEnabled)
                        .orElse(feature.getDefaultEnabled());
                states.put(env.getSlug(), stateValue);
            }

            return FeatureRowDto.builder()
                    .id(feature.getId())
                    .key(feature.getKey())
                    .name(feature.getName())
                    .description(feature.getDescription())
                    .defaultEnabled(feature.getDefaultEnabled())
                    .githubBranch(feature.getGithubBranch())
                    .githubPrNumber(feature.getGithubPrNumber())
                    .states(states)
                    .build();
        }).toList();
    }

    @Transactional
    public FeatureRowDto createFeature(UUID projectId, FeatureRequest request, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        User user = userRepository.findById(principal.getId()).orElse(null);

        if (featureRepository.existsByProjectIdAndKey(projectId, request.getKey())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Feature flag with key '" + request.getKey() + "' already exists");
        }

        boolean defaultEnabled = Boolean.TRUE.equals(request.getDefaultEnabled());

        Feature feature = Feature.builder()
                .project(project)
                .key(request.getKey())
                .name(request.getName())
                .description(request.getDescription())
                .defaultEnabled(defaultEnabled)
                .build();

        feature = featureRepository.save(feature);

        List<Environment> environments = environmentRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        Map<String, Boolean> states = new HashMap<>();

        for (Environment env : environments) {
            FeatureState state = FeatureState.builder()
                    .feature(feature)
                    .environment(env)
                    .enabled(defaultEnabled)
                    .build();
            featureStateRepository.save(state);
            states.put(env.getSlug(), defaultEnabled);
        }

        auditLogService.logAction(project, user, "FEATURE_CREATED", "Feature", feature.getId().toString(), Map.of("key", feature.getKey(), "featureKey", feature.getKey(), "name", feature.getName()));

        return FeatureRowDto.builder()
                .id(feature.getId())
                .key(feature.getKey())
                .name(feature.getName())
                .description(feature.getDescription())
                .defaultEnabled(feature.getDefaultEnabled())
                .githubBranch(feature.getGithubBranch())
                .githubPrNumber(feature.getGithubPrNumber())
                .states(states)
                .build();
    }

    @Transactional
    public FeatureRowDto linkFeatureToGithub(UUID projectId, UUID featureId, String branch, Integer prNumber, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        User user = userRepository.findById(principal.getId()).orElse(null);

        Feature feature = featureRepository.findById(featureId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Feature not found"));

        if (!feature.getProject().getId().equals(projectId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Feature does not belong to this project");
        }

        feature.setGithubBranch(branch != null && !branch.isBlank() ? branch.trim() : null);
        feature.setGithubPrNumber(prNumber);
        feature = featureRepository.save(feature);

        Map<String, Object> meta = new HashMap<>();
        meta.put("featureKey", feature.getKey());
        meta.put("key", feature.getKey());
        if (branch != null) meta.put("branch", branch);
        if (prNumber != null) meta.put("prNumber", prNumber);

        auditLogService.logAction(project, user, "FEATURE_GITHUB_LINKED", "Feature", feature.getId().toString(), meta);

        List<Environment> environments = environmentRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        Map<String, Boolean> states = new HashMap<>();
        for (Environment env : environments) {
            Boolean stateValue = featureStateRepository.findByFeatureIdAndEnvironmentId(feature.getId(), env.getId())
                    .map(FeatureState::getEnabled)
                    .orElse(feature.getDefaultEnabled());
            states.put(env.getSlug(), stateValue);
        }

        return FeatureRowDto.builder()
                .id(feature.getId())
                .key(feature.getKey())
                .name(feature.getName())
                .description(feature.getDescription())
                .defaultEnabled(feature.getDefaultEnabled())
                .githubBranch(feature.getGithubBranch())
                .githubPrNumber(feature.getGithubPrNumber())
                .states(states)
                .build();
    }

    @Transactional
    public void toggleFlag(UUID projectId, String envSlug, String key, Boolean enabled, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        User user = userRepository.findById(principal.getId()).orElse(null);

        Feature feature = featureRepository.findByProjectIdAndKey(projectId, key)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Feature with key '" + key + "' not found"));

        Environment environment = environmentRepository.findByProjectIdAndSlug(projectId, envSlug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Environment with slug '" + envSlug + "' not found"));

        FeatureState state = featureStateRepository.findByFeatureIdAndEnvironmentId(feature.getId(), environment.getId())
                .orElseGet(() -> FeatureState.builder()
                        .feature(feature)
                        .environment(environment)
                        .enabled(feature.getDefaultEnabled())
                        .build());

        state.setEnabled(enabled);
        featureStateRepository.save(state);

        auditLogService.logAction(
                project,
                user,
                "FLAG_TOGGLED",
                "FeatureState",
                state.getId().toString(),
                Map.of("featureKey", key, "envSlug", envSlug, "enabled", enabled)
        );
    }

    @Transactional(readOnly = true)
    public EvaluationResponse evaluateFlags(String apiKey, String envSlug) {
        Project project = projectRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or unrecognized Project API Key"));

        if (project.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Project has been archived");
        }

        Environment environment = environmentRepository.findByProjectIdAndSlug(project.getId(), envSlug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Environment '" + envSlug + "' not found for project"));

        List<Feature> features = featureRepository.findByProjectId(project.getId());
        Map<String, Boolean> flags = new HashMap<>();

        for (Feature feature : features) {
            Boolean enabled = featureStateRepository.findByFeatureIdAndEnvironmentId(feature.getId(), environment.getId())
                    .map(FeatureState::getEnabled)
                    .orElse(feature.getDefaultEnabled());
            flags.put(feature.getKey(), enabled);
        }

        return EvaluationResponse.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .environment(environment.getSlug())
                .flags(flags)
                .evaluatedAt(LocalDateTime.now())
                .build();
    }
}
