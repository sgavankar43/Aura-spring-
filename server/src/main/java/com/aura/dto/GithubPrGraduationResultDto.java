package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GithubPrGraduationResultDto {
    private int prNumber;
    private String prTitle;
    private String headBranch;
    private String baseBranch;
    private String targetEnvironment;
    private List<String> graduatedFlags;
    private Map<String, Object> graduationDetails;
    private boolean commentPosted;
    private String message;

    public GithubPrGraduationResultDto() {
    }

    @Builder
    public GithubPrGraduationResultDto(
            int prNumber,
            String prTitle,
            String headBranch,
            String baseBranch,
            String targetEnvironment,
            List<String> graduatedFlags,
            Map<String, Object> graduationDetails,
            boolean commentPosted,
            String message
    ) {
        this.prNumber = prNumber;
        this.prTitle = prTitle;
        this.headBranch = headBranch;
        this.baseBranch = baseBranch;
        this.targetEnvironment = targetEnvironment;
        this.graduatedFlags = graduatedFlags;
        this.graduationDetails = graduationDetails;
        this.commentPosted = commentPosted;
        this.message = message;
    }
}
