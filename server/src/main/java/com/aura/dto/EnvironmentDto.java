package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class EnvironmentDto {
    private UUID id;
    private String name;
    private String slug;
    private String color;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID projectId;

    public EnvironmentDto() {
    }

    @Builder
    public EnvironmentDto(UUID id, String name, String slug, String color, Integer sortOrder, LocalDateTime createdAt, LocalDateTime updatedAt, UUID projectId) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.color = color;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.projectId = projectId;
    }
}
