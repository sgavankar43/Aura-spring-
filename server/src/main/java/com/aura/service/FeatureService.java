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
import java.util.Optional;
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
    private final ChangeRequestRepository changeRequestRepository;
    private final GithubIntegrationRepository githubIntegrationRepository;

    public FeatureService(
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            FeatureRepository featureRepository,
            FeatureStateRepository featureStateRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            TargetingEngine targetingEngine,
            ApplicationEventPublisher eventPublisher,
            ChangeRequestRepository changeRequestRepository,
            GithubIntegrationRepository githubIntegrationRepository) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.featureRepository = featureRepository;
        this.featureStateRepository = featureStateRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.targetingEngine = targetingEngine;
        this.eventPublisher = eventPublisher;
        this.changeRequestRepository = changeRequestRepository;
        this.githubIntegrationRepository = githubIntegrationRepository;
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

        String requestedBranch = (request.getGithubBranch() != null && !request.getGithubBranch().isBlank())
                ? request.getGithubBranch().trim()
                : null;

        if (requestedBranch != null) {
            validateBranchForFeature(projectId, requestedBranch, null);
        }

        Feature feature = Feature.builder()
                .project(project)
                .key(request.getKey())
                .name(request.getName())
                .description(request.getDescription())
                .defaultEnabled(defaultEnabled)
                .githubBranch(requestedBranch)
                .githubPrNumber(request.getGithubPrNumber())
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
    public void deleteFeature(UUID projectId, String key, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        Feature feature = featureRepository.findByProjectIdAndKey(projectId, key)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Feature flag with key '" + key + "' not found"));

        User user = (principal != null && principal.getId() != null)
                ? userRepository.findById(principal.getId()).orElse(null)
                : null;

        // 1. Delete associated change requests
        changeRequestRepository.deleteByFeatureId(feature.getId());

        // 2. Delete feature states
        featureStateRepository.deleteByFeatureId(feature.getId());

        // 3. Delete the feature entity
        featureRepository.delete(feature);

        // 4. Log audit action
        auditLogService.logAction(project, user, "FEATURE_DELETED", "Feature", feature.getId().toString(),
                Map.of("key", feature.getKey(), "name", feature.getName()));

        // 5. Broadcast real-time SSE stream updates to connected clients (<15ms)
        List<Environment> environments = environmentRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        for (Environment env : environments) {
            eventPublisher.publishEvent(FlagStateChangedEvent.builder()
                    .projectId(project.getId())
                    .envSlug(env.getSlug())
                    .featureKey(feature.getKey())
                    .enabled(false)
                    .rolloutPercentage(0)
                    .rules("[]")
                    .build());
        }
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

        String cleanBranch = (branch != null && !branch.isBlank()) ? branch.trim() : null;
        if (cleanBranch != null) {
            validateBranchForFeature(projectId, cleanBranch, feature.getId());
        }

        feature.setGithubBranch(cleanBranch);
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

    public void validateBranchForFeature(UUID projectId, String branch, UUID currentFeatureId) {
        if (branch == null || branch.isBlank()) {
            return;
        }
        String cleanBranch = branch.trim();

        // 1. Must NOT be the default / production branch (main, master, or configured default branch)
        Optional<GithubIntegration> integrationOpt = githubIntegrationRepository.findByProjectId(projectId);
        String defaultBranch = integrationOpt.map(GithubIntegration::getDefaultBranch).orElse("main");
        if (cleanBranch.equalsIgnoreCase(defaultBranch) || cleanBranch.equalsIgnoreCase("main") || cleanBranch.equalsIgnoreCase("master")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Cannot link feature flag to default/production branch '" + cleanBranch + "'. Features must be linked to a separate feature branch.");
        }

        // 2. Must NOT be already occupied by another feature flag in this project
        List<Feature> existingOccupied = featureRepository.findByProjectIdAndGithubBranch(projectId, cleanBranch);
        for (Feature f : existingOccupied) {
            if (currentFeatureId == null || !f.getId().equals(currentFeatureId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Branch '" + cleanBranch + "' is already occupied by feature flag '" + f.getKey() + "'. Each feature must have its own separate branch.");
            }
        }
    }
}
