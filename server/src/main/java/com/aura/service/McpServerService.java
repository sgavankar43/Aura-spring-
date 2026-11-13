package com.aura.service;

import com.aura.dto.*;
import com.aura.entity.*;
import com.aura.exception.ApiException;
import com.aura.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class McpServerService {

    private final ProjectRepository projectRepository;
    private final FeatureRepository featureRepository;
    private final EnvironmentRepository environmentRepository;
    private final FeatureService featureService;
    private final StaleFlagService staleFlagService;
    private final ObjectMapper objectMapper;

    public McpServerService(
            ProjectRepository projectRepository,
            FeatureRepository featureRepository,
            EnvironmentRepository environmentRepository,
            FeatureService featureService,
            StaleFlagService staleFlagService) {
        this.projectRepository = projectRepository;
        this.featureRepository = featureRepository;
        this.environmentRepository = environmentRepository;
        this.featureService = featureService;
        this.staleFlagService = staleFlagService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Lists all tools exposed by the Aura MCP Server
     */
    public List<Map<String, Object>> getToolsManifest() {
        return List.of(
                Map.of(
                        "name", "list_flags",
                        "description", "List all feature flags in an Aura project with environment states and canary rollout percentages.",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "apiKey", Map.of("type", "string", "description", "Aura Project API Key (or pass AURA_API_KEY)")
                                ),
                                "required", List.of("apiKey")
                        )
                ),
                Map.of(
                        "name", "get_flag",
                        "description", "Get full details, rollout percentages, and targeting rules for a specific feature flag.",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "apiKey", Map.of("type", "string", "description", "Aura Project API Key"),
                                        "flagKey", Map.of("type", "string", "description", "Unique key identifier of the flag")
                                ),
                                "required", List.of("apiKey", "flagKey")
                        )
                ),
                Map.of(
                        "name", "toggle_flag",
                        "description", "Toggle a feature flag ON or OFF in a target environment (e.g. development, staging, production).",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "apiKey", Map.of("type", "string", "description", "Aura Project API Key"),
                                        "flagKey", Map.of("type", "string", "description", "Flag key to toggle"),
                                        "environment", Map.of("type", "string", "description", "Environment slug (e.g. development, staging, production)"),
                                        "enabled", Map.of("type", "boolean", "description", "True to enable, false to disable")
                                ),
                                "required", List.of("apiKey", "flagKey", "environment", "enabled")
                        )
                ),
                Map.of(
                        "name", "update_rollout",
                        "description", "Update the progressive canary rollout percentage (0-100) or segmentation rules for a flag.",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "apiKey", Map.of("type", "string", "description", "Aura Project API Key"),
                                        "flagKey", Map.of("type", "string", "description", "Flag key"),
                                        "environment", Map.of("type", "string", "description", "Environment slug"),
                                        "rolloutPercentage", Map.of("type", "integer", "description", "Rollout percentage between 0 and 100")
                                ),
                                "required", List.of("apiKey", "flagKey", "environment", "rolloutPercentage")
                        )
                ),
                Map.of(
                        "name", "evaluate_flags",
                        "description", "Evaluate feature flags for a user context (userId, email, role, tier) using MurmurHash3 determinism.",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "apiKey", Map.of("type", "string", "description", "Aura Project API Key"),
                                        "environment", Map.of("type", "string", "description", "Environment slug"),
                                        "userId", Map.of("type", "string", "description", "User identifier for hashing"),
                                        "email", Map.of("type", "string", "description", "User email address")
                                ),
                                "required", List.of("apiKey", "environment")
                        )
                ),
                Map.of(
                        "name", "get_tech_debt",
                        "description", "Analyze technical debt and detect stale flags ready for retirement in the project.",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "apiKey", Map.of("type", "string", "description", "Aura Project API Key")
                                ),
                                "required", List.of("apiKey")
                        )
                ),
                Map.of(
                        "name", "cleanup_flag",
                        "description", "Generate automated code refactoring diffs to retire a stale feature flag and eliminate debt.",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "apiKey", Map.of("type", "string", "description", "Aura Project API Key"),
                                        "flagKey", Map.of("type", "string", "description", "Flag key to retire")
                                ),
                                "required", List.of("apiKey", "flagKey")
                        )
                )
        );
    }

    /**
     * Executes an MCP tool call by name with arguments
     */
    @Transactional
    public Map<String, Object> executeTool(String toolName, Map<String, Object> args) {
        String apiKey = (String) args.get("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing required parameter 'apiKey'");
        }

        Project project = projectRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid Aura Project API Key"));

        switch (toolName) {
            case "list_flags": {
                List<FeatureRowDto> flags = featureService.getFeaturesForProject(project.getId());
                return Map.of("project", project.getName(), "flagsCount", flags.size(), "flags", flags);
            }

            case "get_flag": {
                String flagKey = (String) args.get("flagKey");
                Feature feature = featureRepository.findByProjectIdAndKey(project.getId(), flagKey)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Flag '" + flagKey + "' not found"));
                List<FeatureRowDto> all = featureService.getFeaturesForProject(project.getId());
                FeatureRowDto match = all.stream().filter(f -> f.getKey().equals(flagKey)).findFirst().orElse(null);
                return Map.of("flag", match != null ? match : feature);
            }

            case "toggle_flag": {
                String flagKey = (String) args.get("flagKey");
                String env = (String) args.get("environment");
                Boolean enabled = (Boolean) args.get("enabled");
                featureService.toggleFlag(project.getId(), env, flagKey, enabled, null);
                return Map.of(
                        "success", true,
                        "message", "Flag '" + flagKey + "' toggled to " + (enabled ? "ON" : "OFF") + " in " + env,
                        "flagKey", flagKey,
                        "environment", env,
                        "enabled", enabled
                );
            }

            case "update_rollout": {
                String flagKey = (String) args.get("flagKey");
                String env = (String) args.get("environment");
                Number pct = (Number) args.get("rolloutPercentage");
                int percentage = pct != null ? pct.intValue() : 100;
                FlagTargetingRequest req = FlagTargetingRequest.builder()
                        .rolloutPercentage(percentage)
                        .build();
                featureService.updateFlagTargeting(project.getId(), env, flagKey, req, null);
                return Map.of(
                        "success", true,
                        "message", "Flag '" + flagKey + "' rollout percentage updated to " + percentage + "% in " + env,
                        "flagKey", flagKey,
                        "environment", env,
                        "rolloutPercentage", percentage
                );
            }

            case "evaluate_flags": {
                String env = (String) args.get("environment");
                String userId = (String) args.get("userId");
                String email = (String) args.get("email");
                EvaluationContext ctx = (userId != null || email != null)
                        ? EvaluationContext.builder().userId(userId).email(email).build()
                        : null;
                EvaluationResponse eval = featureService.evaluateFlags(apiKey, env, ctx);
                return Map.of("evaluation", eval);
            }

            case "get_tech_debt": {
                StaleFlagReportDto report = staleFlagService.generateStaleFlagReport(project.getId());
                return Map.of("techDebtReport", report);
            }

            case "cleanup_flag": {
                String flagKey = (String) args.get("flagKey");
                FlagCleanupPrResponse resp = staleFlagService.generateCleanupPr(project.getId(), flagKey, false, null);
                return Map.of("cleanupPr", resp);
            }

            default:
                throw new ApiException(HttpStatus.NOT_FOUND, "Unrecognized MCP tool: " + toolName);
        }
    }
}
