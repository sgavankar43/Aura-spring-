package com.aura.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "features", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"project_id", "key"})
})
@Getter
@Setter
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "key", nullable = false)
    private String key;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "default_enabled", nullable = false)
    private Boolean defaultEnabled;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "feature", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FeatureState> states = new ArrayList<>();

    public Feature() {
    }

    @Builder
    public Feature(UUID id, Project project, String key, String name, String description, Boolean defaultEnabled, LocalDateTime createdAt, LocalDateTime updatedAt, List<FeatureState> states) {
        this.id = id;
        this.project = project;
        this.key = key;
        this.name = name;
        this.description = description;
        this.defaultEnabled = defaultEnabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.states = (states != null) ? states : new ArrayList<>();
    }
}
