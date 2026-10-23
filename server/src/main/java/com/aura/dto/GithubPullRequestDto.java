package com.aura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubPullRequestDto {
    private int number;
    private String title;
    private String state;
    private String headBranch;
    private String baseBranch;
    private String authorName;
    private String authorAvatar;
    private String createdAt;
    private String htmlUrl;
    private boolean draft;
}
