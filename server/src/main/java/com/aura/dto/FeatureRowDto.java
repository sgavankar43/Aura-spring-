package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class FeatureRowDto {
    private UUID id;
    private String key;
    private String name;
    private String description;
    private Boolean defaultEnabled;
    private String githubBranch;
    private Integer githubPrNumber;
    private LocalDateTime archivedAt;
    private LocalDateTime updatedAt;
    private Map<String, Boolean> states;
    private Map<String, Integer> rolloutPercentages;
    private Map<String, String> targetingRules;

    public FeatureRowDto() {
    }

    @Builder
    public FeatureRowDto(
            UUID id,
            String key,
            String name,
            String description,
            Boolean defaultEnabled,
            String githubBranch,
            Integer githubPrNumber,
            LocalDateTime archivedAt,
            LocalDateTime updatedAt,
            Map<String, Boolean> states,
            Map<String, Integer> rolloutPercentages,
            Map<String, String> targetingRules
    ) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.description = description;
        this.defaultEnabled = defaultEnabled;
        this.githubBranch = githubBranch;
        this.githubPrNumber = githubPrNumber;
        this.archivedAt = archivedAt;
        this.updatedAt = updatedAt;
        this.states = states;
        this.rolloutPercentages = rolloutPercentages;
        this.targetingRules = targetingRules;
    }
}
