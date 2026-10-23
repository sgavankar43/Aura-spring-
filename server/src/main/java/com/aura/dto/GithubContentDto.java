package com.aura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubContentDto {
    private String name;
    private String path;
    private String sha;
    private long size;
    private String type; // "file", "dir"
    private String downloadUrl;
    private String content; // text content if file
    private String encoding; // "base64" or "utf-8"
}
