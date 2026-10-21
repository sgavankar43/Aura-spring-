package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class FeatureRowDto {
    private UUID id;
    private String key;
    private String name;
    private String description;
    private Boolean defaultEnabled;
    private Map<String, Boolean> states;

    public FeatureRowDto() {
    }

    @Builder
    public FeatureRowDto(UUID id, String key, String name, String description, Boolean defaultEnabled, Map<String, Boolean> states) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.description = description;
        this.defaultEnabled = defaultEnabled;
        this.states = states;
    }
}
