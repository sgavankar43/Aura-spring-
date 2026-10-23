package com.aura.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GithubConnectRequest {
    @NotBlank(message = "Repository is required (e.g. 'owner/repo' or GitHub URL)")
    private String repository;

    private String accessToken;

    private String defaultBranch;
}
