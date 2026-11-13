package com.aura.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureRequest {
    @NotBlank(message = "Key is required")
    private String key;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @Builder.Default
    private Boolean defaultEnabled = false;

    private String githubBranch;

    private Integer githubPrNumber;
}
