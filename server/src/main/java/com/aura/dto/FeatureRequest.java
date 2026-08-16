package com.aura.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FeatureRequest {
    @NotBlank(message = "Key is required")
    private String key;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    private Boolean defaultEnabled = false;
}
