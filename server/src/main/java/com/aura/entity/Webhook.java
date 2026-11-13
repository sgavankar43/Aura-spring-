package com.aura.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhooks")
@Getter
@Setter
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String url;

    private String secret;

    private String description;

    @Column(nullable = false)
    private String events = "flag.toggle,flag.rollout,flag.archived";

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "last_status")
    private Integer lastStatus;

    @Column(name = "last_trigger")
    private LocalDateTime lastTrigger;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Webhook() {
    }

    @Builder
    public Webhook(
            UUID id,
            Project project,
            String url,
            String secret,
            String description,
            String events,
            Boolean enabled,
            Integer lastStatus,
            LocalDateTime lastTrigger,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.project = project;
        this.url = url;
        this.secret = secret;
        this.description = description;
        this.events = (events != null && !events.isBlank()) ? events : "flag.toggle,flag.rollout,flag.archived";
        this.enabled = (enabled != null) ? enabled : true;
        this.lastStatus = lastStatus;
        this.lastTrigger = lastTrigger;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
