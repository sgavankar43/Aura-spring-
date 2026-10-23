package com.aura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubTagDto {
    private String name;
    private String commitSha;
    private String tarballUrl;
    private String zipballUrl;
}
