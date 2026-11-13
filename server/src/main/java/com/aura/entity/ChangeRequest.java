package com.aura.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "change_requests")
@Getter
@Setter
public class ChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @Column(name = "target_state", nullable = false)
    private Boolean targetState;

    @Column(name = "target_rollout", nullable = false)
    private Integer targetRollout = 100;

    @Column(name = "target_rules", columnDefinition = "TEXT")
    private String targetRules;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED, APPLIED

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public ChangeRequest() {
    }

    @Builder
    public ChangeRequest(
            UUID id,
            Project project,
            Feature feature,
            Environment environment,
            User requester,
            User reviewer,
            Boolean targetState,
            Integer targetRollout,
            String targetRules,
            String reason,
            String status,
            LocalDateTime createdAt,
            LocalDateTime reviewedAt) {
        this.id = id;
        this.project = project;
        this.feature = feature;
        this.environment = environment;
        this.requester = requester;
        this.reviewer = reviewer;
        this.targetState = targetState;
        this.targetRollout = (targetRollout != null) ? targetRollout : 100;
        this.targetRules = targetRules;
        this.reason = reason;
        this.status = (status != null && !status.isBlank()) ? status : "PENDING";
        this.createdAt = createdAt;
        this.reviewedAt = reviewedAt;
    }
}
