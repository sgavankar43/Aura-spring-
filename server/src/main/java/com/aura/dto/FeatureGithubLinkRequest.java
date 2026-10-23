package com.aura.dto;

import lombok.Data;

@Data
public class FeatureGithubLinkRequest {
    private String branch;
    private Integer prNumber;
}
