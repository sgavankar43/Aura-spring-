package com.aura.controller;

import com.aura.dto.*;
import com.aura.security.UserPrincipal;
import com.aura.service.FeatureService;
import com.aura.service.GithubService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class GithubController {

    private final GithubService githubService;
    private final FeatureService featureService;

    public GithubController(GithubService githubService, FeatureService featureService) {
        this.githubService = githubService;
        this.featureService = featureService;
    }

    @PostMapping("/github/connect")
    public ResponseEntity<GithubStatusResponse> connectGithub(
            @PathVariable UUID projectId,
            @Valid @RequestBody GithubConnectRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(githubService.connectRepository(projectId, request, principal));
    }

    @GetMapping("/github/status")
    public ResponseEntity<GithubStatusResponse> getGithubStatus(@PathVariable UUID projectId) {
        return ResponseEntity.ok(githubService.getStatus(projectId));
    }

    @DeleteMapping("/github/disconnect")
    public ResponseEntity<Map<String, String>> disconnectGithub(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        githubService.disconnectRepository(projectId, principal);
        return ResponseEntity.ok(Map.of("message", "GitHub repository disconnected successfully"));
    }

    @GetMapping("/github/branches")
    public ResponseEntity<List<GithubBranchDto>> getBranches(@PathVariable UUID projectId) {
        return ResponseEntity.ok(githubService.getBranches(projectId));
    }

    @GetMapping("/github/tags")
    public ResponseEntity<List<GithubTagDto>> getTags(@PathVariable UUID projectId) {
        return ResponseEntity.ok(githubService.getTags(projectId));
    }

    @GetMapping("/github/commits")
    public ResponseEntity<List<GithubCommitDto>> getCommits(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String branch,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(githubService.getCommits(projectId, branch, limit));
    }

    @GetMapping("/github/tree")
    public ResponseEntity<List<GithubContentDto>> getTreeOrContents(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String branch,
            @RequestParam(defaultValue = "") String path
    ) {
        return ResponseEntity.ok(githubService.getTreeOrContents(projectId, branch, path));
    }

    @GetMapping("/github/pulls")
    public ResponseEntity<List<GithubPullRequestDto>> getPullRequests(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "all") String state
    ) {
        return ResponseEntity.ok(githubService.getPullRequests(projectId, state));
    }

    @PatchMapping("/features/{featureId}/github")
    public ResponseEntity<FeatureRowDto> linkFeatureToGithub(
            @PathVariable UUID projectId,
            @PathVariable UUID featureId,
            @RequestBody FeatureGithubLinkRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(featureService.linkFeatureToGithub(
                projectId, featureId, request.getBranch(), request.getPrNumber(), principal
        ));
    }
}
