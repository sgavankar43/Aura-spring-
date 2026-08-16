package com.aura.controller;

import com.aura.dto.FeatureRequest;
import com.aura.dto.FeatureRowDto;
import com.aura.dto.FeaturesResponse;
import com.aura.dto.FlagToggleRequest;
import com.aura.security.UserPrincipal;
import com.aura.service.FeatureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureService featureService;

    @GetMapping("/features")
    public ResponseEntity<FeaturesResponse> getFeatures(@PathVariable UUID projectId) {
        return ResponseEntity.ok(FeaturesResponse.builder()
                .features(featureService.getFeaturesForProject(projectId))
                .build());
    }

    @PostMapping("/features")
    public ResponseEntity<FeatureRowDto> createFeature(
            @PathVariable UUID projectId,
            @Valid @RequestBody FeatureRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(featureService.createFeature(projectId, request, principal));
    }

    @PatchMapping("/flags/{envSlug}/{key}")
    public ResponseEntity<Map<String, String>> toggleFlag(
            @PathVariable UUID projectId,
            @PathVariable String envSlug,
            @PathVariable String key,
            @Valid @RequestBody FlagToggleRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        featureService.toggleFlag(projectId, envSlug, key, request.getEnabled(), principal);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
