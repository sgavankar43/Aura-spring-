package com.aura.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlagStateChangedEvent {

    private UUID projectId;
    private String envSlug;
    private String featureKey;
    private Boolean enabled;
    private Integer rolloutPercentage;
    private String rules;
}
