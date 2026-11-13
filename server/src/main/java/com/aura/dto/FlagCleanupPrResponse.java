package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class FlagCleanupPrResponse {
    private String flagKey;
    private String branchName;
    private String prTitle;
    private String prBody;
    private String diffPreview;
    private String prUrl;
    private String status; // "PR_CREATED", "SIMULATED_PREVIEW", "NO_REFERENCES_FOUND"
    private List<String> affectedFiles;

    public FlagCleanupPrResponse() {
    }

    @Builder
    public FlagCleanupPrResponse(
            String flagKey,
            String branchName,
            String prTitle,
            String prBody,
            String diffPreview,
            String prUrl,
            String status,
            List<String> affectedFiles
    ) {
        this.flagKey = flagKey;
        this.branchName = branchName;
        this.prTitle = prTitle;
        this.prBody = prBody;
        this.diffPreview = diffPreview;
        this.prUrl = prUrl;
        this.status = status;
        this.affectedFiles = affectedFiles;
    }
}
