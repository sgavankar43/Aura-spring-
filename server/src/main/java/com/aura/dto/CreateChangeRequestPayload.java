package com.aura.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateChangeRequestPayload {

    @NotBlank
    private String featureKey;

    @NotBlank
    private String environmentSlug;

    @NotNull
    private Boolean targetState;

    private Integer targetRollout;

    private String targetRules;

    private String reason;
}
