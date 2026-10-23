package com.aura.service;

import com.aura.dto.AuditLogEntryDto;
import com.aura.dto.PaginatedAuditLogsDto;
import com.aura.entity.AuditLog;
import com.aura.entity.Project;
import com.aura.entity.User;
import com.aura.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public void logAction(Project project, User user, String action, String entity, String entityId, Map<String, Object> metadata) {
        String metadataJson = null;
        if (metadata != null && !metadata.isEmpty()) {
            try {
                metadataJson = objectMapper.writeValueAsString(metadata);
            } catch (JsonProcessingException ignored) {
            }
        }

        AuditLog auditLog = AuditLog.builder()
                .project(project)
                .user(user)
                .action(action)
                .entity(entity)
                .entityId(entityId)
                .metadataJson(metadataJson)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public PaginatedAuditLogsDto getAuditLogs(UUID projectId, int page, int limit) {
        int pageIndex = Math.max(0, page - 1);
        Page<AuditLog> pageResult = auditLogRepository.findByProjectIdOrderByCreatedAtDesc(
                projectId, PageRequest.of(pageIndex, limit)
        );

        List<AuditLogEntryDto> entries = pageResult.getContent().stream()
                .map(this::mapToDto)
                .toList();

        return PaginatedAuditLogsDto.builder()
                .data(entries)
                .meta(PaginatedAuditLogsDto.AuditMeta.builder()
                        .page(page)
                        .total(pageResult.getTotalElements())
                        .totalPages(pageResult.getTotalPages())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntryDto> getAllAuditLogsForExport(UUID projectId) {
        return auditLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public String exportAuditLogsToCsv(UUID projectId) {
        List<AuditLogEntryDto> logs = getAllAuditLogsForExport(projectId);
        StringBuilder sb = new StringBuilder();

        // CSV Header
        sb.append("ID,Timestamp,Action,Entity,Entity ID,User ID,User Name,User Email,Details,Metadata\n");

        for (AuditLogEntryDto log : logs) {
            sb.append(escapeCsvField(log.getId() != null ? log.getId().toString() : "")).append(",");
            sb.append(escapeCsvField(log.getCreatedAt() != null ? log.getCreatedAt().toString() : "")).append(",");
            sb.append(escapeCsvField(log.getAction())).append(",");
            sb.append(escapeCsvField(log.getEntity())).append(",");
            sb.append(escapeCsvField(log.getEntityId())).append(",");
            sb.append(escapeCsvField(log.getUserId() != null ? log.getUserId().toString() : "")).append(",");
            sb.append(escapeCsvField(log.getUserName() != null ? log.getUserName() : "System")).append(",");
            sb.append(escapeCsvField(log.getUserEmail() != null ? log.getUserEmail() : "N/A")).append(",");
            sb.append(escapeCsvField(formatDetailsForExport(log))).append(",");

            String metaString = "";
            if (log.getMetadata() != null && !log.getMetadata().isEmpty()) {
                try {
                    metaString = objectMapper.writeValueAsString(log.getMetadata());
                } catch (Exception ignored) {
                }
            }
            sb.append(escapeCsvField(metaString)).append("\n");
        }

        return sb.toString();
    }

    private String formatDetailsForExport(AuditLogEntryDto log) {
        Map<String, Object> meta = log.getMetadata() != null ? log.getMetadata() : Collections.emptyMap();
        String action = log.getAction() != null ? log.getAction() : "";

        return switch (action.toUpperCase()) {
            case "FLAG_TOGGLED", "FLAG.TOGGLED" -> {
                Object enabled = meta.get("enabled");
                Object featureKey = meta.getOrDefault("featureKey", meta.get("key"));
                Object envSlug = meta.get("envSlug");
                yield "Toggled " + (featureKey != null ? featureKey : "?") + " to " +
                        (Boolean.TRUE.equals(enabled) ? "ON" : "OFF") +
                        (envSlug != null ? " in " + envSlug : "");
            }
            case "FEATURE_CREATED", "FEATURE.CREATED" -> {
                Object featureKey = meta.getOrDefault("featureKey", meta.get("key"));
                yield "Created feature flag " + (featureKey != null ? featureKey : "?");
            }
            case "ENVIRONMENT_CREATED", "ENVIRONMENT.CREATED" -> {
                Object envName = meta.getOrDefault("name", meta.get("slug"));
                yield "Created environment " + (envName != null ? envName : "?");
            }
            case "PROJECT_CREATED", "PROJECT.CREATED" -> "Created the project";
            case "PROJECT_UPDATED", "PROJECT.UPDATED" -> "Updated project details";
            case "PROJECT_ARCHIVED", "PROJECT.ARCHIVED" -> "Archived the project";
            case "GITHUB_CONNECTED" -> "Connected GitHub repository: " + meta.getOrDefault("repo", "");
            case "GITHUB_DISCONNECTED" -> "Disconnected GitHub repository: " + meta.getOrDefault("repo", "");
            case "FEATURE_GITHUB_LINKED" -> "Linked feature " + meta.getOrDefault("featureKey", "") +
                    " to branch " + meta.getOrDefault("branch", "");
            default -> action;
        };
    }

    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        // Neutralize CSV injection vulnerability
        String clean = field;
        if (!clean.isEmpty() && (clean.startsWith("=") || clean.startsWith("+") || clean.startsWith("-") || clean.startsWith("@"))) {
            clean = "'" + clean;
        }

        if (clean.contains(",") || clean.contains("\"") || clean.contains("\n") || clean.contains("\r")) {
            return "\"" + clean.replace("\"", "\"\"") + "\"";
        }
        return clean;
    }

    private AuditLogEntryDto mapToDto(AuditLog log) {
        Map<String, Object> metadata = null;
        if (log.getMetadataJson() != null) {
            try {
                metadata = objectMapper.readValue(log.getMetadataJson(), new TypeReference<>() {});
            } catch (Exception e) {
                metadata = Collections.emptyMap();
            }
        }

        return AuditLogEntryDto.builder()
                .id(log.getId())
                .action(log.getAction())
                .entity(log.getEntity())
                .entityId(log.getEntityId())
                .userId(log.getUser() != null ? log.getUser().getId() : null)
                .userName(log.getUser() != null ? log.getUser().getName() : null)
                .userEmail(log.getUser() != null ? log.getUser().getEmail() : null)
                .projectId(log.getProject() != null ? log.getProject().getId() : null)
                .metadata(metadata)
                .createdAt(log.getCreatedAt())
                .build();
    }
}
