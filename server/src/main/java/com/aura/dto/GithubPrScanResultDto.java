package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GithubPrScanResultDto {
    private int prNumber;
    private String prTitle;
    private String headBranch;
    private String baseBranch;
    private String repoOwner;
    private String repoName;
    private int filesTouchedCount;
    private List<FlagPrReferenceDto> referencedFlags;
    private String commentMarkdown;
    private boolean commentPosted;
    private String commentUrl;

    public GithubPrScanResultDto() {
    }

    @Builder
    public GithubPrScanResultDto(
            int prNumber,
            String prTitle,
            String headBranch,
            String baseBranch,
            String repoOwner,
            String repoName,
            int filesTouchedCount,
            List<FlagPrReferenceDto> referencedFlags,
            String commentMarkdown,
            boolean commentPosted,
            String commentUrl
    ) {
        this.prNumber = prNumber;
        this.prTitle = prTitle;
        this.headBranch = headBranch;
        this.baseBranch = baseBranch;
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.filesTouchedCount = filesTouchedCount;
        this.referencedFlags = referencedFlags;
        this.commentMarkdown = commentMarkdown;
        this.commentPosted = commentPosted;
        this.commentUrl = commentUrl;
    }

    @Data
    public static class FlagPrReferenceDto {
        private String key;
        private String name;
        private String description;
        private Map<String, Boolean> states;
        private Map<String, Integer> rolloutPercentages;
        private Map<String, String> targetingRules;
        private List<String> occurrencesInFiles;

        public FlagPrReferenceDto() {
        }

        @Builder
        public FlagPrReferenceDto(
                String key,
                String name,
                String description,
                Map<String, Boolean> states,
                Map<String, Integer> rolloutPercentages,
                Map<String, String> targetingRules,
                List<String> occurrencesInFiles
        ) {
            this.key = key;
            this.name = name;
            this.description = description;
            this.states = states;
            this.rolloutPercentages = rolloutPercentages;
            this.targetingRules = targetingRules;
            this.occurrencesInFiles = occurrencesInFiles;
        }
    }
}
