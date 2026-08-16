package com.aura.service;

import com.aura.dto.EnvironmentDto;
import com.aura.dto.ProjectDto;
import com.aura.dto.ProjectRequest;
import com.aura.entity.Environment;
import com.aura.entity.Project;
import com.aura.entity.User;
import com.aura.exception.ApiException;
import com.aura.repository.EnvironmentRepository;
import com.aura.repository.ProjectRepository;
import com.aura.repository.UserRepository;
import com.aura.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ProjectDto> getAllProjects() {
        return projectRepository.findByArchivedAtIsNull().stream()
                .map(this::mapToProjectDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDto getProjectById(UUID id) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
        return mapToProjectDto(project);
    }

    @Transactional
    public ProjectDto createProject(ProjectRequest request, UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        String apiKey = "aura_pk_live_" + UUID.randomUUID().toString().replace("-", "");

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .apiKey(apiKey)
                .user(user)
                .build();

        project = projectRepository.save(project);

        // Create default environments
        Environment devEnv = Environment.builder()
                .project(project)
                .name("Development")
                .slug("development")
                .color("#3B82F6")
                .sortOrder(1)
                .build();

        Environment stagingEnv = Environment.builder()
                .project(project)
                .name("Staging")
                .slug("staging")
                .color("#F59E0B")
                .sortOrder(2)
                .build();

        Environment prodEnv = Environment.builder()
                .project(project)
                .name("Production")
                .slug("production")
                .color("#10B981")
                .sortOrder(3)
                .build();

        environmentRepository.saveAll(List.of(devEnv, stagingEnv, prodEnv));

        auditLogService.logAction(project, user, "PROJECT_CREATED", "Project", project.getId().toString(), Map.of("name", project.getName()));

        return mapToProjectDto(projectRepository.findById(project.getId()).get());
    }

    @Transactional
    public ProjectDto updateProject(UUID id, ProjectRequest request, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        User user = userRepository.findById(principal.getId()).orElse(null);

        if (request.getName() != null && !request.getName().isBlank()) {
            project.setName(request.getName());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }

        project = projectRepository.save(project);

        auditLogService.logAction(project, user, "PROJECT_UPDATED", "Project", project.getId().toString(), Map.of("name", project.getName()));

        return mapToProjectDto(project);
    }

    @Transactional
    public ProjectDto archiveProject(UUID id, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        User user = userRepository.findById(principal.getId()).orElse(null);

        project.setArchivedAt(LocalDateTime.now());
        project = projectRepository.save(project);

        auditLogService.logAction(project, user, "PROJECT_ARCHIVED", "Project", project.getId().toString(), Map.of("name", project.getName()));

        return mapToProjectDto(project);
    }

    public ProjectDto mapToProjectDto(Project project) {
        List<EnvironmentDto> envDtos = environmentRepository.findByProjectIdOrderBySortOrderAsc(project.getId()).stream()
                .map(env -> EnvironmentDto.builder()
                        .id(env.getId())
                        .name(env.getName())
                        .slug(env.getSlug())
                        .color(env.getColor())
                        .sortOrder(env.getSortOrder())
                        .createdAt(env.getCreatedAt())
                        .updatedAt(env.getUpdatedAt())
                        .projectId(project.getId())
                        .build())
                .toList();

        return ProjectDto.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .apiKey(project.getApiKey())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .archivedAt(project.getArchivedAt())
                .environments(envDtos)
                .features(Collections.emptyList())
                .build();
    }
}
