package com.aura.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EnvironmentRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Slug is required")
    private String slug;

    private String color;
}
