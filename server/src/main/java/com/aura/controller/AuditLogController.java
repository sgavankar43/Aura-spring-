package com.aura.controller;

import com.aura.dto.PaginatedAuditLogsDto;
import com.aura.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<PaginatedAuditLogsDto> getAuditLogs(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(auditLogService.getAuditLogs(projectId, page, limit));
    }
}
