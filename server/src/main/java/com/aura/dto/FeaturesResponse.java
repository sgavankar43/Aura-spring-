package com.aura.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class FeaturesResponse {
    private List<FeatureRowDto> features;

    public FeaturesResponse() {
    }

    @Builder
    public FeaturesResponse(List<FeatureRowDto> features) {
        this.features = features;
    }
}
