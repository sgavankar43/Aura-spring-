package com.aura.dto;

import lombok.Builder;
import lombok.Data;

@Data
public class EnvironmentResponse {
    private EnvironmentDto environment;

    public EnvironmentResponse() {
    }

    @Builder
    public EnvironmentResponse(EnvironmentDto environment) {
        this.environment = environment;
    }
}
