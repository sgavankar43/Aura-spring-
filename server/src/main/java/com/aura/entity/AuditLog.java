package com.aura.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String entity;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AuditLog() {
    }

    public AuditLog(UUID id, Project project, User user, String action, String entity, String entityId, String metadataJson, LocalDateTime createdAt) {
        this.id = id;
        this.project = project;
        this.user = user;
        this.action = action;
        this.entity = entity;
        this.entityId = entityId;
        this.metadataJson = metadataJson;
        this.createdAt = createdAt;
    }

    public static AuditLogBuilder builder() {
        return new AuditLogBuilder();
    }

    public static class AuditLogBuilder {
        private UUID id;
        private Project project;
        private User user;
        private String action;
        private String entity;
        private String entityId;
        private String metadataJson;
        private LocalDateTime createdAt;

        public AuditLogBuilder id(UUID id) { this.id = id; return this; }
        public AuditLogBuilder project(Project project) { this.project = project; return this; }
        public AuditLogBuilder user(User user) { this.user = user; return this; }
        public AuditLogBuilder action(String action) { this.action = action; return this; }
        public AuditLogBuilder entity(String entity) { this.entity = entity; return this; }
        public AuditLogBuilder entityId(String entityId) { this.entityId = entityId; return this; }
        public AuditLogBuilder metadataJson(String metadataJson) { this.metadataJson = metadataJson; return this; }
        public AuditLogBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public AuditLog build() {
            return new AuditLog(id, project, user, action, entity, entityId, metadataJson, createdAt);
        }
    }
}
