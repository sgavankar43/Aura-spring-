package com.aura.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
@Getter
@Setter
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_id", nullable = false)
    private Webhook webhook;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(nullable = false)
    private Boolean success = false;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public WebhookDelivery() {
    }

    @Builder
    public WebhookDelivery(
            UUID id,
            Webhook webhook,
            UUID projectId,
            String eventType,
            String payloadJson,
            Integer responseStatus,
            String responseBody,
            Long durationMs,
            Boolean success,
            Integer attemptNumber,
            LocalDateTime createdAt) {
        this.id = id;
        this.webhook = webhook;
        this.projectId = projectId;
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.durationMs = durationMs;
        this.success = (success != null) ? success : false;
        this.attemptNumber = (attemptNumber != null) ? attemptNumber : 1;
        this.createdAt = createdAt;
    }
}
