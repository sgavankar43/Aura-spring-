package com.aura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationContext {

    private String userId;
    private String email;

    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();

    public Object getAttributeValue(String attributeName) {
        if ("userId".equalsIgnoreCase(attributeName)) {
            return userId;
        }
        if ("email".equalsIgnoreCase(attributeName)) {
            return email;
        }
        if (attributes != null && attributes.containsKey(attributeName)) {
            return attributes.get(attributeName);
        }
        return null;
    }
}
