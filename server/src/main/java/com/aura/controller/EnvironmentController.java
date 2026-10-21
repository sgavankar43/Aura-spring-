package com.aura.controller;

import com.aura.dto.EnvironmentRequest;
import com.aura.dto.EnvironmentResponse;
import com.aura.security.UserPrincipal;
import com.aura.service.EnvironmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/environments")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @PostMapping
    public ResponseEntity<EnvironmentResponse> createEnvironment(
            @PathVariable UUID projectId,
            @Valid @RequestBody EnvironmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(EnvironmentResponse.builder()
                .environment(environmentService.createEnvironment(projectId, request, principal))
                .build());
    }
}
