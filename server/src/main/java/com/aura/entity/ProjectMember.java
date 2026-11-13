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
@Table(name = "project_members")
@Getter
@Setter
public class ProjectMember {

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
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String role = "EDITOR"; // ADMIN, EDITOR, VIEWER

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, PENDING_INVITE

    @Column(name = "invite_token")
    private String inviteToken;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ProjectMember() {
    }

    @Builder
    public ProjectMember(
            UUID id,
            Project project,
            User user,
            String name,
            String email,
            String role,
            String status,
            String inviteToken,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.project = project;
        this.user = user;
        this.name = name;
        this.email = email;
        this.role = (role != null) ? role : "EDITOR";
        this.status = (status != null) ? status : "ACTIVE";
        this.inviteToken = inviteToken;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
