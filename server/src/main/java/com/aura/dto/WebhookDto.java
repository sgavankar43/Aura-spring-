package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class WebhookDto {
    private UUID id;
    private UUID projectId;
    private String url;
    private String description;
    private String events;
    private Boolean enabled;
    private Integer lastStatus;
    private LocalDateTime lastTrigger;
    private LocalDateTime createdAt;

    public WebhookDto() {
    }

    @Builder
    public WebhookDto(
            UUID id,
            UUID projectId,
            String url,
            String description,
            String events,
            Boolean enabled,
            Integer lastStatus,
            LocalDateTime lastTrigger,
            LocalDateTime createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.url = url;
        this.description = description;
        this.events = events;
        this.enabled = enabled;
        this.lastStatus = lastStatus;
        this.lastTrigger = lastTrigger;
        this.createdAt = createdAt;
    }
}
