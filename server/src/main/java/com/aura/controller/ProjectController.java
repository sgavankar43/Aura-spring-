package com.aura.controller;

import com.aura.dto.ProjectDto;
import com.aura.dto.ProjectRequest;
import com.aura.dto.ProjectResponse;
import com.aura.dto.ProjectsResponse;
import com.aura.security.UserPrincipal;
import com.aura.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<ProjectsResponse> getAllProjects() {
        return ResponseEntity.ok(ProjectsResponse.builder()
                .projects(projectService.getAllProjects())
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProjectById(@PathVariable UUID id) {
        return ResponseEntity.ok(ProjectResponse.builder()
                .project(projectService.getProjectById(id))
                .build());
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ProjectResponse.builder()
                .project(projectService.createProject(request, principal))
                .build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable UUID id,
            @RequestBody ProjectRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ProjectResponse.builder()
                .project(projectService.updateProject(id, request, principal))
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ProjectResponse> archiveProject(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ProjectResponse.builder()
                .project(projectService.archiveProject(id, principal))
                .build());
    }
}
