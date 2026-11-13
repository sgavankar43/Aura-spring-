package com.aura.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReviewChangeRequestPayload {

    @NotBlank
    private String decision; // "APPROVE" or "REJECT"

    private String comment;
}
