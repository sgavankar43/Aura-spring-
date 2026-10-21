package com.aura.dto;

import lombok.Builder;
import lombok.Data;

@Data
public class ProjectResponse {
    private ProjectDto project;

    public ProjectResponse() {
    }

    @Builder
    public ProjectResponse(ProjectDto project) {
        this.project = project;
    }
}
