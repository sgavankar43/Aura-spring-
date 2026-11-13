package com.aura.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteMemberRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Role is required (ADMIN, EDITOR, VIEWER)")
    private String role;

    private Boolean activateImmediately;

    private String tempPassword;

    public InviteMemberRequest(String name, String email, String role) {
        this.name = name;
        this.email = email;
        this.role = role;
    }
}
