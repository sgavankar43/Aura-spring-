package com.aura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubCommitDto {
    private String sha;
    private String shortSha;
    private String message;
    private String authorName;
    private String authorEmail;
    private String authorAvatar;
    private String date;
    private String htmlUrl;
}
