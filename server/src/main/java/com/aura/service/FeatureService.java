package com.aura.service;

import com.aura.dto.FeatureRequest;
import com.aura.dto.FeatureRowDto;
import com.aura.entity.*;
import com.aura.exception.ApiException;
import com.aura.repository.*;
import com.aura.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeatureService {

    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final FeatureRepository featureRepository;
    private final FeatureStateRepository featureStateRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

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

        auditLogService.logAction(project, user, "FEATURE_CREATED", "Feature", feature.getId().toString(), Map.of("key", feature.getKey(), "name", feature.getName()));

        return FeatureRowDto.builder()
                .id(feature.getId())
                .key(feature.getKey())
                .name(feature.getName())
                .description(feature.getDescription())
                .defaultEnabled(feature.getDefaultEnabled())
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
}
