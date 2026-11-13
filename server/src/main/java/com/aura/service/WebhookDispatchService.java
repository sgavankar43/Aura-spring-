package com.aura.service;

import com.aura.dto.CreateWebhookRequest;
import com.aura.dto.WebhookDto;
import com.aura.dto.WebhookTestResultDto;
import com.aura.entity.Project;
import com.aura.entity.User;
import com.aura.entity.Webhook;
import com.aura.event.FlagStateChangedEvent;
import com.aura.exception.ApiException;
import com.aura.repository.ProjectRepository;
import com.aura.repository.UserRepository;
import com.aura.repository.WebhookRepository;
import com.aura.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class WebhookDispatchService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatchService.class);

    private final WebhookRepository webhookRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebhookDispatchService(
            WebhookRepository webhookRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            AuditLogService auditLogService) {
        this.webhookRepository = webhookRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Transactional(readOnly = true)
    public List<WebhookDto> getWebhooks(UUID projectId) {
        return webhookRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public WebhookDto createWebhook(UUID projectId, CreateWebhookRequest request, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        User user = userRepository.findById(principal.getId()).orElse(null);

        Webhook webhook = Webhook.builder()
                .project(project)
                .url(request.getUrl().trim())
                .secret(request.getSecret() != null && !request.getSecret().isBlank() ? request.getSecret().trim() : UUID.randomUUID().toString().replace("-", ""))
                .description(request.getDescription())
                .events(request.getEvents() != null && !request.getEvents().isBlank() ? request.getEvents() : "flag.toggle,flag.rollout,flag.archived")
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        webhook = webhookRepository.save(webhook);

        auditLogService.logAction(
                project,
                user,
                "WEBHOOK_CREATED",
                "Webhook",
                webhook.getId().toString(),
                Map.of("url", webhook.getUrl(), "events", webhook.getEvents())
        );

        return toDto(webhook);
    }

    @Transactional
    public void deleteWebhook(UUID projectId, UUID webhookId, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        Webhook webhook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Webhook not found"));

        if (!webhook.getProject().getId().equals(projectId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Webhook does not belong to this project");
        }

        User user = userRepository.findById(principal.getId()).orElse(null);
        webhookRepository.delete(webhook);

        auditLogService.logAction(
                project,
                user,
                "WEBHOOK_DELETED",
                "Webhook",
                webhookId.toString(),
                Map.of("url", webhook.getUrl())
        );
    }

    @Transactional
    public WebhookTestResultDto sendTestPing(UUID projectId, UUID webhookId, UserPrincipal principal) {
        Webhook webhook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Webhook not found"));

        if (!webhook.getProject().getId().equals(projectId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Webhook does not belong to this project");
        }

        Map<String, Object> testPayload = Map.of(
                "event", "ping",
                "projectId", projectId.toString(),
                "timestamp", LocalDateTime.now().toString(),
                "message", "Aura Outbound Webhook Test Event",
                "system", "Aura Feature Flag Platform"
        );

        long start = System.currentTimeMillis();
        try {
            String jsonPayload = objectMapper.writeValueAsString(testPayload);
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(webhook.getUrl()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Aura-Webhook-Dispatcher/1.0")
                    .header("X-Aura-Event", "ping")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

            if (webhook.getSecret() != null) {
                reqBuilder.header("X-Aura-Signature", computeHmacSha256(jsonPayload, webhook.getSecret()));
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            webhook.setLastStatus(response.statusCode());
            webhook.setLastTrigger(LocalDateTime.now());
            webhookRepository.save(webhook);

            return WebhookTestResultDto.builder()
                    .success(response.statusCode() >= 200 && response.statusCode() < 300)
                    .statusCode(response.statusCode())
                    .responseBody(response.body() != null && response.body().length() > 500 ? response.body().substring(0, 500) + "..." : response.body())
                    .latencyMs(latency)
                    .message("Delivered HTTP " + response.statusCode() + " in " + latency + "ms")
                    .build();

        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            webhook.setLastStatus(500);
            webhook.setLastTrigger(LocalDateTime.now());
            webhookRepository.save(webhook);

            return WebhookTestResultDto.builder()
                    .success(false)
                    .statusCode(0)
                    .responseBody(ex.getMessage())
                    .latencyMs(latency)
                    .message("Delivery failed: " + ex.getMessage())
                    .build();
        }
    }

    /**
     * Listens to flag changes and dispatches outbound webhooks asynchronously.
     */
    @Async
    @EventListener
    public void onFlagStateChanged(FlagStateChangedEvent event) {
        try {
            List<Webhook> activeWebhooks = webhookRepository.findByProjectIdAndEnabledTrue(event.getProjectId());
            if (activeWebhooks.isEmpty()) return;

            Map<String, Object> payload = Map.of(
                    "event", "flag.toggle",
                    "projectId", event.getProjectId().toString(),
                    "environment", event.getEnvSlug(),
                    "featureKey", event.getFeatureKey(),
                    "enabled", event.getEnabled(),
                    "rolloutPercentage", event.getRolloutPercentage(),
                    "rules", event.getRules() != null ? event.getRules() : "[]",
                    "timestamp", LocalDateTime.now().toString()
            );

            String jsonPayload = objectMapper.writeValueAsString(payload);

            for (Webhook wh : activeWebhooks) {
                dispatchToEndpoint(wh, jsonPayload, "flag.toggle");
            }
        } catch (Exception ex) {
            log.warn("Error dispatching outbound webhooks for event {}: {}", event.getFeatureKey(), ex.getMessage());
        }
    }

    private void dispatchToEndpoint(Webhook webhook, String jsonPayload, String eventType) {
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(webhook.getUrl()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Aura-Webhook-Dispatcher/1.0")
                    .header("X-Aura-Event", eventType)
                    .timeout(Duration.ofSeconds(6))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

            if (webhook.getSecret() != null) {
                reqBuilder.header("X-Aura-Signature", computeHmacSha256(jsonPayload, webhook.getSecret()));
            }

            HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            webhook.setLastStatus(resp.statusCode());
            webhook.setLastTrigger(LocalDateTime.now());
            webhookRepository.save(webhook);
        } catch (Exception ex) {
            log.warn("Failed delivering webhook to {}: {}", webhook.getUrl(), ex.getMessage());
        }
    }

    private String computeHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmac);
        } catch (Exception ex) {
            return "sha256-error";
        }
    }

    private WebhookDto toDto(Webhook wh) {
        return WebhookDto.builder()
                .id(wh.getId())
                .projectId(wh.getProject().getId())
                .url(wh.getUrl())
                .description(wh.getDescription())
                .events(wh.getEvents())
                .enabled(wh.getEnabled())
                .lastStatus(wh.getLastStatus())
                .lastTrigger(wh.getLastTrigger())
                .createdAt(wh.getCreatedAt())
                .build();
    }
}
