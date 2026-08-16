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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                .projectId(log.getProject() != null ? log.getProject().getId() : null)
                .metadata(metadata)
                .createdAt(log.getCreatedAt())
                .build();
    }
}
