package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class ProjectsResponse {
    private List<ProjectDto> projects;

    public ProjectsResponse() {
    }

    @Builder
    public ProjectsResponse(List<ProjectDto> projects) {
        this.projects = projects;
    }
}
