package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class StaleFlagItemDto {
    private UUID id;
    private String key;
    private String name;
    private String description;
    private String status; // "READY_FOR_RETIREMENT", "DORMANT_INACTIVE", "IN_ROLLOUT", "HEALTHY_ACTIVE", "ARCHIVED"
    private long daysSinceLastModified;
    private LocalDateTime lastModified;
    private LocalDateTime archivedAt;
    private boolean fullyRolledOut;
    private boolean allDisabled;
    private Map<String, Boolean> states;
    private Map<String, Integer> rolloutPercentages;
    private Map<String, String> targetingRules;
    private int referencedFilesCount;
    private List<String> referencedFiles;
    private String recommendation;

    public StaleFlagItemDto() {
    }

    @Builder
    public StaleFlagItemDto(
            UUID id,
            String key,
            String name,
            String description,
            String status,
            long daysSinceLastModified,
            LocalDateTime lastModified,
            LocalDateTime archivedAt,
            boolean fullyRolledOut,
            boolean allDisabled,
            Map<String, Boolean> states,
            Map<String, Integer> rolloutPercentages,
            Map<String, String> targetingRules,
            int referencedFilesCount,
            List<String> referencedFiles,
            String recommendation
    ) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.description = description;
        this.status = status;
        this.daysSinceLastModified = daysSinceLastModified;
        this.lastModified = lastModified;
        this.archivedAt = archivedAt;
        this.fullyRolledOut = fullyRolledOut;
        this.allDisabled = allDisabled;
        this.states = states;
        this.rolloutPercentages = rolloutPercentages;
        this.targetingRules = targetingRules;
        this.referencedFilesCount = referencedFilesCount;
        this.referencedFiles = referencedFiles;
        this.recommendation = recommendation;
    }
}
