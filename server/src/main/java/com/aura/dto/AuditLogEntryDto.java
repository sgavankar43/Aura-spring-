package com.aura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntryDto {
    private UUID id;
    private String action;
    private String entity;
    private String entityId;
    private UUID userId;
    private UUID projectId;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
