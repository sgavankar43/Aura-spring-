package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
public class UserDto {
    private UUID id;
    private String email;
    private String name;
    private String role;

    public UserDto() {
    }

    @Builder
    public UserDto(UUID id, String email, String name, String role) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.role = role;
    }
}
