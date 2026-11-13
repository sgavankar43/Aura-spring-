package com.aura.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateWebhookRequest {

    @NotBlank
    private String url;

    private String secret;

    private String description;

    private String events; // "flag.toggle,flag.rollout,flag.archived"

    private Boolean enabled = true;
}
