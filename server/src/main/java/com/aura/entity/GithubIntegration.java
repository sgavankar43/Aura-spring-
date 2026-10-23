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
@Table(name = "github_integrations")
@Getter
@Setter
public class GithubIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Column(name = "repo_owner", nullable = false)
    private String repoOwner;

    @Column(name = "repo_name", nullable = false)
    private String repoName;

    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch = "main";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public GithubIntegration() {
    }

    @Builder
    public GithubIntegration(UUID id, Project project, String repoOwner, String repoName, String accessToken, String defaultBranch, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.project = project;
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.accessToken = accessToken;
        this.defaultBranch = (defaultBranch != null && !defaultBranch.isBlank()) ? defaultBranch : "main";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
