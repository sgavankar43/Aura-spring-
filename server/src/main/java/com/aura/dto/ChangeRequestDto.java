package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ChangeRequestDto {
    private UUID id;
    private UUID projectId;
    private UUID featureId;
    private String featureKey;
    private String featureName;
    private UUID environmentId;
    private String environmentSlug;
    private String environmentName;
    private UUID requesterId;
    private String requesterName;
    private String requesterEmail;
    private UUID reviewerId;
    private String reviewerName;
    private Boolean targetState;
    private Integer targetRollout;
    private String targetRules;
    private String reason;
    private String status; // PENDING, APPROVED, REJECTED, APPLIED
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    public ChangeRequestDto() {
    }

    @Builder
    public ChangeRequestDto(
            UUID id,
            UUID projectId,
            UUID featureId,
            String featureKey,
            String featureName,
            UUID environmentId,
            String environmentSlug,
            String environmentName,
            UUID requesterId,
            String requesterName,
            String requesterEmail,
            UUID reviewerId,
            String reviewerName,
            Boolean targetState,
            Integer targetRollout,
            String targetRules,
            String reason,
            String status,
            LocalDateTime createdAt,
            LocalDateTime reviewedAt) {
        this.id = id;
        this.projectId = projectId;
        this.featureId = featureId;
        this.featureKey = featureKey;
        this.featureName = featureName;
        this.environmentId = environmentId;
        this.environmentSlug = environmentSlug;
        this.environmentName = environmentName;
        this.requesterId = requesterId;
        this.requesterName = requesterName;
        this.requesterEmail = requesterEmail;
        this.reviewerId = reviewerId;
        this.reviewerName = reviewerName;
        this.targetState = targetState;
        this.targetRollout = targetRollout;
        this.targetRules = targetRules;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
        this.reviewedAt = reviewedAt;
    }
}
