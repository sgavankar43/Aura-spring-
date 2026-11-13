package com.aura.controller;

import com.aura.dto.CreateWebhookRequest;
import com.aura.dto.WebhookDto;
import com.aura.dto.WebhookTestResultDto;
import com.aura.security.UserPrincipal;
import com.aura.service.WebhookDispatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/webhooks")
public class WebhookController {

    private final WebhookDispatchService webhookService;

    public WebhookController(WebhookDispatchService webhookService) {
        this.webhookService = webhookService;
    }

    @GetMapping
    public ResponseEntity<List<WebhookDto>> getWebhooks(@PathVariable UUID projectId) {
        return ResponseEntity.ok(webhookService.getWebhooks(projectId));
    }

    @PostMapping
    public ResponseEntity<WebhookDto> createWebhook(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateWebhookRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(webhookService.createWebhook(projectId, request, principal));
    }

    @DeleteMapping("/{webhookId}")
    public ResponseEntity<Map<String, String>> deleteWebhook(
            @PathVariable UUID projectId,
            @PathVariable UUID webhookId,
            @AuthenticationPrincipal UserPrincipal principal) {
        webhookService.deleteWebhook(projectId, webhookId, principal);
        return ResponseEntity.ok(Map.of("message", "Webhook deleted successfully"));
    }

    @PostMapping("/{webhookId}/test")
    public ResponseEntity<WebhookTestResultDto> testWebhook(
            @PathVariable UUID projectId,
            @PathVariable UUID webhookId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(webhookService.sendTestPing(projectId, webhookId, principal));
    }
}
