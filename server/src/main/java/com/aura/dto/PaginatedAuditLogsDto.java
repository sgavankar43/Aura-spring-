package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class PaginatedAuditLogsDto {
    private List<AuditLogEntryDto> data;
    private AuditMeta meta;

    public PaginatedAuditLogsDto() {
    }

    @Builder
    public PaginatedAuditLogsDto(List<AuditLogEntryDto> data, AuditMeta meta) {
        this.data = data;
        this.meta = meta;
    }

    @Data
    public static class AuditMeta {
        private int page;
        private long total;
        private int totalPages;

        public AuditMeta() {
        }

        @Builder
        public AuditMeta(int page, long total, int totalPages) {
            this.page = page;
            this.total = total;
            this.totalPages = totalPages;
        }
    }
}
