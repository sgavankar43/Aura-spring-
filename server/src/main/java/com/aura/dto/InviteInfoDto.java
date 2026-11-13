package com.aura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteInfoDto {
    private String token;
    private UUID projectId;
    private String projectName;
    private String email;
    private String name;
    private String role;
    private String status;
}
