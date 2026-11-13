package com.aura.controller;

import com.aura.dto.FlagCleanupPrResponse;
import com.aura.dto.StaleFlagReportDto;
import com.aura.security.UserPrincipal;
import com.aura.service.StaleFlagService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class StaleFlagController {

    private final StaleFlagService staleFlagService;

    public StaleFlagController(StaleFlagService staleFlagService) {
        this.staleFlagService = staleFlagService;
    }

    @GetMapping("/stale-flags")
    public ResponseEntity<StaleFlagReportDto> getStaleFlagReport(@PathVariable UUID projectId) {
        return ResponseEntity.ok(staleFlagService.generateStaleFlagReport(projectId));
    }

    @PostMapping("/flags/{key}/cleanup-pr")
    public ResponseEntity<FlagCleanupPrResponse> generateCleanupPr(
            @PathVariable UUID projectId,
            @PathVariable String key,
            @RequestParam(defaultValue = "false") boolean createOnGithub,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(staleFlagService.generateCleanupPr(projectId, key, createOnGithub, principal));
    }

    @PostMapping("/flags/{key}/archive")
    public ResponseEntity<Map<String, String>> archiveFlag(
            @PathVariable UUID projectId,
            @PathVariable String key,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        staleFlagService.archiveFlag(projectId, key, principal);
        return ResponseEntity.ok(Map.of("message", "Feature flag '" + key + "' has been archived successfully"));
    }

    @PostMapping("/flags/{key}/unarchive")
    public ResponseEntity<Map<String, String>> unarchiveFlag(
            @PathVariable UUID projectId,
            @PathVariable String key,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        staleFlagService.unarchiveFlag(projectId, key, principal);
        return ResponseEntity.ok(Map.of("message", "Feature flag '" + key + "' has been unarchived successfully"));
    }
}
