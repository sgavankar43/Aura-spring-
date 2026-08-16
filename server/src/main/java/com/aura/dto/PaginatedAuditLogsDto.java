package com.aura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedAuditLogsDto {
    private List<AuditLogEntryDto> data;
    private AuditMeta meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditMeta {
        private int page;
        private long total;
        private int totalPages;
    }
}
