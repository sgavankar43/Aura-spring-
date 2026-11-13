package com.aura.service;

import com.aura.dto.EvaluationContext;
import com.aura.dto.TargetingRule;
import com.aura.entity.Feature;
import com.aura.entity.FeatureState;
import com.aura.util.MurmurHash3;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class TargetingEngine {

    private static final Logger log = LoggerFactory.getLogger(TargetingEngine.class);
    private final ObjectMapper objectMapper;

    public TargetingEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Deterministically evaluate a feature state against an evaluation context.
     */
    public boolean evaluate(Feature feature, FeatureState state, EvaluationContext context) {
        if (state == null) {
            return feature != null && Boolean.TRUE.equals(feature.getDefaultEnabled());
        }

        // Master switch: if disabled for environment, flag is off
        if (!Boolean.TRUE.equals(state.getEnabled())) {
            return false;
        }

        // Check explicit targeting rules if defined
        if (state.getRules() != null && !state.getRules().isBlank() && context != null) {
            try {
                List<TargetingRule> rules = objectMapper.readValue(
                        state.getRules(),
                        new TypeReference<List<TargetingRule>>() {}
                );

                if (rules != null && !rules.isEmpty()) {
                    for (TargetingRule rule : rules) {
                        if (matchesRule(rule, context)) {
                            // Explicit segment match grants flag access
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse targeting rules for feature {}: {}", feature.getKey(), e.getMessage());
            }
        }

        // Percentage Rollout (Canary)
        int rollout = state.getRolloutPercentage() != null ? state.getRolloutPercentage() : 100;
        if (rollout >= 100) {
            return true;
        }
        if (rollout <= 0) {
            return false;
        }

        // Use userId or email for deterministic bucket hashing
        String identifier = null;
        if (context != null) {
            if (context.getUserId() != null && !context.getUserId().isBlank()) {
                identifier = context.getUserId();
            } else if (context.getEmail() != null && !context.getEmail().isBlank()) {
                identifier = context.getEmail();
            }
        }

        if (identifier != null) {
            int bucket = MurmurHash3.getRolloutBucket(identifier, feature.getKey());
            return bucket < rollout;
        }

        // If no user identifier is provided and flag is under canary rollout (<100%), default to false for safety
        return false;
    }

    private boolean matchesRule(TargetingRule rule, EvaluationContext context) {
        if (rule == null || rule.getAttribute() == null || rule.getOperator() == null) {
            return false;
        }

        Object rawVal = context.getAttributeValue(rule.getAttribute());
        if (rawVal == null) {
            return false;
        }

        String attrVal = String.valueOf(rawVal).trim();
        String targetVal = rule.getValue() != null ? rule.getValue().trim() : "";
        String op = rule.getOperator().trim().toLowerCase();

        switch (op) {
            case "equals":
            case "eq":
                return attrVal.equalsIgnoreCase(targetVal);

            case "notequals":
            case "neq":
                return !attrVal.equalsIgnoreCase(targetVal);

            case "contains":
                return attrVal.toLowerCase().contains(targetVal.toLowerCase());

            case "notcontains":
                return !attrVal.toLowerCase().contains(targetVal.toLowerCase());

            case "startswith":
                return attrVal.toLowerCase().startsWith(targetVal.toLowerCase());

            case "endswith":
                return attrVal.toLowerCase().endsWith(targetVal.toLowerCase());

            case "in":
                return Arrays.stream(targetVal.split(","))
                        .map(String::trim)
                        .anyMatch(v -> v.equalsIgnoreCase(attrVal));

            case "notin":
                return Arrays.stream(targetVal.split(","))
                        .map(String::trim)
                        .noneMatch(v -> v.equalsIgnoreCase(attrVal));

            case "greaterthan":
            case "gt":
                try {
                    return Double.parseDouble(attrVal) > Double.parseDouble(targetVal);
                } catch (NumberFormatException e) {
                    return false;
                }

            case "lessthan":
            case "lt":
                try {
                    return Double.parseDouble(attrVal) < Double.parseDouble(targetVal);
                } catch (NumberFormatException e) {
                    return false;
                }

            default:
                log.warn("Unknown targeting rule operator: {}", op);
                return false;
        }
    }
}
