package com.aura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubStatusResponse {
    private boolean connected;
    private String repoOwner;
    private String repoName;
    private String repoFullName;
    private String repoUrl;
    private String description;
    private String defaultBranch;
    private boolean isPrivate;
    private int starsCount;
    private int forksCount;
    private int openIssuesCount;
    private LocalDateTime connectedAt;
    private LocalDateTime updatedAt;
}
