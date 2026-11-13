package com.aura.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlagTargetingRequest {

    private Boolean enabled;

    @Min(0)
    @Max(100)
    private Integer rolloutPercentage;

    private String rules;
}
