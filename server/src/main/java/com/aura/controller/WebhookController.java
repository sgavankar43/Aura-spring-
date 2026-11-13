package com.aura.controller;

import com.aura.dto.CreateWebhookRequest;
import com.aura.dto.WebhookDeliveryDto;
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

    @GetMapping("/{webhookId}/deliveries")
    public ResponseEntity<List<WebhookDeliveryDto>> getDeliveries(
            @PathVariable UUID projectId,
            @PathVariable UUID webhookId) {
        return ResponseEntity.ok(webhookService.getDeliveries(projectId, webhookId));
    }

    @PostMapping("/{webhookId}/deliveries/{deliveryId}/retry")
    public ResponseEntity<WebhookDeliveryDto> retryDelivery(
            @PathVariable UUID projectId,
            @PathVariable UUID webhookId,
            @PathVariable UUID deliveryId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(webhookService.retryDelivery(projectId, webhookId, deliveryId, principal));
    }
}
