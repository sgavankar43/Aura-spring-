package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class AuditLogEntryDto {
    private UUID id;
    private String action;
    private String entity;
    private String entityId;
    private UUID userId;
    private String userName;
    private String userEmail;
    private UUID projectId;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;

    public AuditLogEntryDto() {
    }

    @Builder
    public AuditLogEntryDto(UUID id, String action, String entity, String entityId, UUID userId, String userName, String userEmail, UUID projectId, Map<String, Object> metadata, LocalDateTime createdAt) {
        this.id = id;
        this.action = action;
        this.entity = entity;
        this.entityId = entityId;
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.projectId = projectId;
        this.metadata = metadata;
        this.createdAt = createdAt;
    }
}
