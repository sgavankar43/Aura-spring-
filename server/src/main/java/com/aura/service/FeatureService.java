package com.aura.service;

import com.aura.dto.EvaluationContext;
import com.aura.dto.EvaluationResponse;
import com.aura.dto.FeatureRequest;
import com.aura.dto.FeatureRowDto;
import com.aura.dto.FlagTargetingRequest;
import com.aura.entity.*;
import com.aura.event.FlagStateChangedEvent;
import com.aura.exception.ApiException;
import com.aura.repository.*;
import com.aura.security.UserPrincipal;
import org.springframework.context.ApplicationEventPublisher;
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
    private final TargetingEngine targetingEngine;
    private final ApplicationEventPublisher eventPublisher;

    public FeatureService(
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            FeatureRepository featureRepository,
            FeatureStateRepository featureStateRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            TargetingEngine targetingEngine,
            ApplicationEventPublisher eventPublisher) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.featureRepository = featureRepository;
        this.featureStateRepository = featureStateRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.targetingEngine = targetingEngine;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<FeatureRowDto> getFeaturesForProject(UUID projectId) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        List<Environment> environments = environmentRepository.findByProjectIdOrderBySortOrderAsc(project.getId());
        List<Feature> features = featureRepository.findByProjectIdAndArchivedAtIsNull(project.getId());

        return features.stream().map(feature -> {
            Map<String, Boolean> states = new HashMap<>();
            Map<String, Integer> rolloutPercentages = new HashMap<>();
            Map<String, String> targetingRules = new HashMap<>();

            for (Environment env : environments) {
                FeatureState state = featureStateRepository.findByFeatureIdAndEnvironmentId(feature.getId(), env.getId())
                        .orElse(null);

                Boolean stateValue = state != null ? state.getEnabled() : feature.getDefaultEnabled();
                Integer rollout = state != null && state.getRolloutPercentage() != null ? state.getRolloutPercentage() : 100;
                String rules = state != null ? state.getRules() : null;

                states.put(env.getSlug(), stateValue);
                rolloutPercentages.put(env.getSlug(), rollout);
                if (rules != null) {
                    targetingRules.put(env.getSlug(), rules);
                }
            }

            return FeatureRowDto.builder()
                    .id(feature.getId())
                    .key(feature.getKey())
                    .name(feature.getName())
                    .description(feature.getDescription())
                    .defaultEnabled(feature.getDefaultEnabled())
                    .githubBranch(feature.getGithubBranch())
                    .githubPrNumber(feature.getGithubPrNumber())
                    .archivedAt(feature.getArchivedAt())
                    .updatedAt(feature.getUpdatedAt())
                    .states(states)
                    .rolloutPercentages(rolloutPercentages)
                    .targetingRules(targetingRules)
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
        Map<String, Integer> rolloutPercentages = new HashMap<>();
        Map<String, String> targetingRules = new HashMap<>();

        for (Environment env : environments) {
            FeatureState state = FeatureState.builder()
                    .feature(feature)
                    .environment(env)
                    .enabled(defaultEnabled)
                    .rolloutPercentage(100)
                    .build();
            featureStateRepository.save(state);
            states.put(env.getSlug(), defaultEnabled);
            rolloutPercentages.put(env.getSlug(), 100);
        }

        auditLogService.logAction(project, user, "FEATURE_CREATED", "Feature", feature.getId().toString(),
                Map.of("key", feature.getKey(), "featureKey", feature.getKey(), "name", feature.getName()));

        return FeatureRowDto.builder()
                .id(feature.getId())
                .key(feature.getKey())
                .name(feature.getName())
                .description(feature.getDescription())
                .defaultEnabled(feature.getDefaultEnabled())
                .githubBranch(feature.getGithubBranch())
                .githubPrNumber(feature.getGithubPrNumber())
                .archivedAt(feature.getArchivedAt())
                .updatedAt(feature.getUpdatedAt())
                .states(states)
                .rolloutPercentages(rolloutPercentages)
                .targetingRules(targetingRules)
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
        Map<String, Integer> rolloutPercentages = new HashMap<>();
        Map<String, String> targetingRules = new HashMap<>();

        for (Environment env : environments) {
            FeatureState state = featureStateRepository.findByFeatureIdAndEnvironmentId(feature.getId(), env.getId())
                    .orElse(null);

            Boolean stateValue = state != null ? state.getEnabled() : feature.getDefaultEnabled();
            Integer rollout = state != null && state.getRolloutPercentage() != null ? state.getRolloutPercentage() : 100;
            String rules = state != null ? state.getRules() : null;

            states.put(env.getSlug(), stateValue);
            rolloutPercentages.put(env.getSlug(), rollout);
            if (rules != null) {
                targetingRules.put(env.getSlug(), rules);
            }
        }

        return FeatureRowDto.builder()
                .id(feature.getId())
                .key(feature.getKey())
                .name(feature.getName())
                .description(feature.getDescription())
                .defaultEnabled(feature.getDefaultEnabled())
                .githubBranch(feature.getGithubBranch())
                .githubPrNumber(feature.getGithubPrNumber())
                .archivedAt(feature.getArchivedAt())
                .updatedAt(feature.getUpdatedAt())
                .states(states)
                .rolloutPercentages(rolloutPercentages)
                .targetingRules(targetingRules)
                .build();
    }

    @Transactional
    public void toggleFlag(UUID projectId, String envSlug, String key, Boolean enabled, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        User user = (principal != null) ? userRepository.findById(principal.getId()).orElse(null) : null;

        Feature feature = featureRepository.findByProjectIdAndKey(projectId, key)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Feature with key '" + key + "' not found"));

        Environment environment = environmentRepository.findByProjectIdAndSlug(projectId, envSlug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Environment with slug '" + envSlug + "' not found"));

        FeatureState state = featureStateRepository.findByFeatureIdAndEnvironmentId(feature.getId(), environment.getId())
                .orElseGet(() -> FeatureState.builder()
                        .feature(feature)
                        .environment(environment)
                        .enabled(feature.getDefaultEnabled())
                        .rolloutPercentage(100)
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

        // Publish event for real-time SSE broadcast
        eventPublisher.publishEvent(FlagStateChangedEvent.builder()
                .projectId(projectId)
                .envSlug(envSlug)
                .featureKey(key)
                .enabled(enabled)
                .rolloutPercentage(state.getRolloutPercentage())
                .rules(state.getRules())
                .build());
    }

    @Transactional
    public void updateFlagTargeting(UUID projectId, String envSlug, String key, FlagTargetingRequest request, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        User user = (principal != null) ? userRepository.findById(principal.getId()).orElse(null) : null;

        Feature feature = featureRepository.findByProjectIdAndKey(projectId, key)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Feature with key '" + key + "' not found"));

        Environment environment = environmentRepository.findByProjectIdAndSlug(projectId, envSlug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Environment with slug '" + envSlug + "' not found"));

        FeatureState state = featureStateRepository.findByFeatureIdAndEnvironmentId(feature.getId(), environment.getId())
                .orElseGet(() -> FeatureState.builder()
                        .feature(feature)
                        .environment(environment)
                        .enabled(feature.getDefaultEnabled())
                        .rolloutPercentage(100)
                        .build());

        if (request.getEnabled() != null) {
            state.setEnabled(request.getEnabled());
        }
        if (request.getRolloutPercentage() != null) {
            state.setRolloutPercentage(request.getRolloutPercentage());
        }
        if (request.getRules() != null) {
            state.setRules(request.getRules().isBlank() ? null : request.getRules().trim());
        }

        featureStateRepository.save(state);

        Map<String, Object> meta = new HashMap<>();
        meta.put("featureKey", key);
        meta.put("envSlug", envSlug);
        meta.put("enabled", state.getEnabled());
        meta.put("rolloutPercentage", state.getRolloutPercentage());
        if (state.getRules() != null) {
            meta.put("rules", state.getRules());
        }

        auditLogService.logAction(
                project,
                user,
                "FLAG_TARGETING_UPDATED",
                "FeatureState",
                state.getId().toString(),
                meta
        );

        // Publish event for real-time SSE broadcast
        eventPublisher.publishEvent(FlagStateChangedEvent.builder()
                .projectId(projectId)
                .envSlug(envSlug)
                .featureKey(key)
                .enabled(state.getEnabled())
                .rolloutPercentage(state.getRolloutPercentage())
                .rules(state.getRules())
                .build());
    }

    @Transactional(readOnly = true)
    public EvaluationResponse evaluateFlags(String apiKey, String envSlug) {
        return evaluateFlags(apiKey, envSlug, null);
    }

    @Transactional(readOnly = true)
    public EvaluationResponse evaluateFlags(String apiKey, String envSlug, EvaluationContext context) {
        Project project = projectRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or unrecognized Project API Key"));

        if (project.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Project has been archived");
        }

        Environment environment = environmentRepository.findByProjectIdAndSlug(project.getId(), envSlug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Environment '" + envSlug + "' not found for project"));

        List<Feature> features = featureRepository.findByProjectIdAndArchivedAtIsNull(project.getId());
        Map<String, Boolean> flags = new HashMap<>();

        for (Feature feature : features) {
            FeatureState state = featureStateRepository.findByFeatureIdAndEnvironmentId(feature.getId(), environment.getId())
                    .orElse(null);

            boolean isFlagEnabled = targetingEngine.evaluate(feature, state, context);
            flags.put(feature.getKey(), isFlagEnabled);
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
