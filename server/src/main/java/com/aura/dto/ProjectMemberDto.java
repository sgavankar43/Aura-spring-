package com.aura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberDto {
    private UUID id;
    private UUID projectId;
    private UUID userId;
    private String name;
    private String email;
    private String role; // ADMIN, EDITOR, VIEWER
    private String status; // ACTIVE, PENDING_INVITE
    private String inviteToken;
    private String tempPassword;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
