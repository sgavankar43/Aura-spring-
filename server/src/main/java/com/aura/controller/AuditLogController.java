package com.aura.controller;

import com.aura.dto.PaginatedAuditLogsDto;
import com.aura.service.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<PaginatedAuditLogsDto> getAuditLogs(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(auditLogService.getAuditLogs(projectId, page, limit));
    }

    @GetMapping("/export")
    public ResponseEntity<?> exportAuditLogs(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "csv") String format
    ) {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        if ("json".equalsIgnoreCase(format)) {
            java.util.List<com.aura.dto.AuditLogEntryDto> logs = auditLogService.getAllAuditLogsForExport(projectId);
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"audit-logs-" + projectId + "-" + timestamp + ".json\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(logs);
        }

        String csvData = auditLogService.exportAuditLogsToCsv(projectId);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"audit-logs-" + projectId + "-" + timestamp + ".csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }
}
