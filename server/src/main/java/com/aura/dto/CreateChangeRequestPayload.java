package com.aura.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
