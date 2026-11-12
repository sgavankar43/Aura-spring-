package com.aura.controller;

import com.aura.entity.Feature;
import com.aura.entity.GithubIntegration;
import com.aura.repository.FeatureRepository;
import com.aura.repository.GithubIntegrationRepository;
import com.aura.service.AuditLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
public class GithubAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GithubAppWebhookController.class);

    private final GithubIntegrationRepository githubRepository;
    private final FeatureRepository featureRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public GithubAppWebhookController(
            GithubIntegrationRepository githubRepository,
            FeatureRepository featureRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.githubRepository = githubRepository;
        this.featureRepository = featureRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * Webhook endpoint for GitHub App events (pull_request, installation, etc.)
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleGithubWebhook(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "ping") String event,
            @RequestBody String rawPayload
    ) {
        log.info("Received GitHub Webhook Event: {}", event);

        try {
            JsonNode payload = objectMapper.readTree(rawPayload);

            if ("ping".equalsIgnoreCase(event)) {
                return ResponseEntity.ok(Map.of("status", "pong", "message", "GitHub App Webhook active!"));
            }

            if ("pull_request".equalsIgnoreCase(event)) {
                handlePullRequestEvent(payload);
            } else if ("installation".equalsIgnoreCase(event)) {
                handleInstallationEvent(payload);
            }

        } catch (Exception e) {
            log.error("Error processing GitHub webhook event: {}", event, e);
        }

        return ResponseEntity.ok(Map.of("status", "received", "event", event));
    }

    /**
     * OAuth and App installation redirect callback
     */
    @GetMapping("/app/callback")
    public ResponseEntity<Map<String, String>> handleAppCallback(
            @RequestParam(required = false) String code,
            @RequestParam(name = "installation_id", required = false) String installationId,
            @RequestParam(name = "setup_action", required = false) String setupAction
    ) {
        log.info("GitHub App installation callback: installationId={}, action={}", installationId, setupAction);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Aura GitHub App installed successfully!",
                "installationId", installationId != null ? installationId : "registered"
        ));
    }

    private void handlePullRequestEvent(JsonNode payload) {
        String action = payload.path("action").asText("");
        JsonNode pr = payload.path("pull_request");
        int prNumber = pr.path("number").asInt();
        String prTitle = pr.path("title").asText();
        boolean merged = pr.path("merged").asBoolean(false);
        String headBranch = pr.path("head").path("ref").asText();
        String baseBranch = pr.path("base").path("ref").asText();

        JsonNode repo = payload.path("repository");
        String repoOwner = repo.path("owner").path("login").asText();
        String repoName = repo.path("name").asText();

        log.info("PR #{} '{}' on {}/{}: action={}, merged={}, head={}, base={}",
                prNumber, prTitle, repoOwner, repoName, action, merged, headBranch, baseBranch);

        // Find projects linked to this repo
        List<GithubIntegration> integrations = githubRepository
                .findByRepoOwnerIgnoreCaseAndRepoNameIgnoreCase(repoOwner, repoName);

        for (GithubIntegration integration : integrations) {
            List<Feature> linkedFeatures = featureRepository
                    .findByProjectIdAndGithubPrNumber(integration.getProject().getId(), prNumber);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("prNumber", prNumber);
            metadata.put("prTitle", prTitle);
            metadata.put("headBranch", headBranch);
            metadata.put("baseBranch", baseBranch);
            metadata.put("merged", merged);
            metadata.put("linkedFlagsCount", linkedFeatures.size());

            String auditAction = merged ? "GITHUB_PR_MERGED" : "GITHUB_PR_" + action.toUpperCase();

            auditLogService.logAction(
                    integration.getProject(),
                    null,
                    auditAction,
                    "PullRequest",
                    String.valueOf(prNumber),
                    metadata
            );
        }
    }

    private void handleInstallationEvent(JsonNode payload) {
        String action = payload.path("action").asText("");
        long installationId = payload.path("installation").path("id").asLong();
        String sender = payload.path("sender").path("login").asText();
        log.info("GitHub App installation {}: id={}, sender={}", action, installationId, sender);
    }
}
