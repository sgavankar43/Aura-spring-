package com.aura.service;

import com.aura.dto.EnvironmentDto;
import com.aura.dto.EnvironmentRequest;
import com.aura.entity.Environment;
import com.aura.entity.Feature;
import com.aura.entity.FeatureState;
import com.aura.entity.Project;
import com.aura.entity.User;
import com.aura.exception.ApiException;
import com.aura.repository.EnvironmentRepository;
import com.aura.repository.FeatureRepository;
import com.aura.repository.FeatureStateRepository;
import com.aura.repository.ProjectRepository;
import com.aura.repository.UserRepository;
import com.aura.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnvironmentService {

    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final FeatureRepository featureRepository;
    private final FeatureStateRepository featureStateRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public EnvironmentDto createEnvironment(UUID projectId, EnvironmentRequest request, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        User user = userRepository.findById(principal.getId()).orElse(null);

        if (environmentRepository.existsByProjectIdAndSlug(projectId, request.getSlug())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Environment with slug '" + request.getSlug() + "' already exists");
        }

        List<Environment> existingEnvs = environmentRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        int nextSortOrder = existingEnvs.stream().mapToInt(Environment::getSortOrder).max().orElse(0) + 1;

        Environment env = Environment.builder()
                .project(project)
                .name(request.getName())
                .slug(request.getSlug())
                .color(request.getColor() != null ? request.getColor() : "#6B7280")
                .sortOrder(nextSortOrder)
                .build();

        env = environmentRepository.save(env);

        // Populate feature states for existing features
        List<Feature> features = featureRepository.findByProjectId(projectId);
        for (Feature feature : features) {
            FeatureState state = FeatureState.builder()
                    .feature(feature)
                    .environment(env)
                    .enabled(feature.getDefaultEnabled())
                    .build();
            featureStateRepository.save(state);
        }

        auditLogService.logAction(project, user, "ENVIRONMENT_CREATED", "Environment", env.getId().toString(), Map.of("name", env.getName(), "slug", env.getSlug()));

        return EnvironmentDto.builder()
                .id(env.getId())
                .name(env.getName())
                .slug(env.getSlug())
                .color(env.getColor())
                .sortOrder(env.getSortOrder())
                .createdAt(env.getCreatedAt())
                .updatedAt(env.getUpdatedAt())
                .projectId(projectId)
                .build();
    }
}
