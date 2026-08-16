package com.aura.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FlagToggleRequest {
    @NotNull(message = "enabled parameter is required")
    private Boolean enabled;
}
