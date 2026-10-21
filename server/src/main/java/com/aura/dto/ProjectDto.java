package com.aura.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectDto {
    private UUID id;
    private String name;
    private String description;
    private String apiKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime archivedAt;
    private List<EnvironmentDto> environments;
    private List<Object> features;

    public ProjectDto() {
    }

    @Builder
    public ProjectDto(UUID id, String name, String description, String apiKey, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime archivedAt, List<EnvironmentDto> environments, List<Object> features) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.apiKey = apiKey;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.archivedAt = archivedAt;
        this.environments = environments;
        this.features = features;
    }
}
