package com.aura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDeliveryDto {
    private UUID id;
    private UUID webhookId;
    private UUID projectId;
    private String eventType;
    private String payloadJson;
    private Integer responseStatus;
    private String responseBody;
    private Long durationMs;
    private Boolean success;
    private Integer attemptNumber;
    private LocalDateTime createdAt;
}
