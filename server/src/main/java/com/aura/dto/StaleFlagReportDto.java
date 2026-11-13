package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class StaleFlagReportDto {
    private int totalFlags;
    private int staleCount;
    private int dormantCount;
    private int activeCount;
    private int techDebtScore; // 0 - 100
    private List<StaleFlagItemDto> flags;

    public StaleFlagReportDto() {
    }

    @Builder
    public StaleFlagReportDto(
            int totalFlags,
            int staleCount,
            int dormantCount,
            int activeCount,
            int techDebtScore,
            List<StaleFlagItemDto> flags
    ) {
        this.totalFlags = totalFlags;
        this.staleCount = staleCount;
        this.dormantCount = dormantCount;
        this.activeCount = activeCount;
        this.techDebtScore = techDebtScore;
        this.flags = flags;
    }
}
