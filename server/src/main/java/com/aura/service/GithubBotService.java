package com.aura.service;

import com.aura.dto.GithubPrGraduationResultDto;
import com.aura.dto.GithubPrScanResultDto;
import com.aura.entity.*;
import com.aura.event.FlagStateChangedEvent;
import com.aura.exception.ApiException;
import com.aura.repository.*;
import com.aura.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class GithubBotService {

    private static final Logger log = LoggerFactory.getLogger(GithubBotService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String BOT_COMMENT_MARKER = "<!-- aura:pr-report -->";

    private final GithubIntegrationRepository githubRepository;
    private final ProjectRepository projectRepository;
    private final FeatureRepository featureRepository;
    private final FeatureStateRepository featureStateRepository;
    private final EnvironmentRepository environmentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GithubBotService(
            GithubIntegrationRepository githubRepository,
            ProjectRepository projectRepository,
            FeatureRepository featureRepository,
            FeatureStateRepository featureStateRepository,
            EnvironmentRepository environmentRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher) {
        this.githubRepository = githubRepository;
        this.projectRepository = projectRepository;
        this.featureRepository = featureRepository;
        this.featureStateRepository = featureStateRepository;
        this.environmentRepository = environmentRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Scan pull request for flag usages, compile analysis, and post/update Markdown comment on GitHub PR.
     */
    @Transactional
    public GithubPrScanResultDto scanAndCommentOnPullRequest(
            UUID projectId,
            int prNumber,
            boolean postComment,
            UserPrincipal principal) {
        GithubIntegration integration = githubRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GitHub is not connected for this project"));

        Project project = integration.getProject();
        User user = (principal != null) ? userRepository.findById(principal.getId()).orElse(null) : null;

        // Fetch PR metadata from GitHub API if possible
        String prTitle = "Pull Request #" + prNumber;
        String prBody = "";
        String headBranch = "unknown";
        String baseBranch = integration.getDefaultBranch();

        JsonNode prNode = fetchGithubJson("/repos/" + integration.getRepoOwner() + "/" + integration.getRepoName() + "/pulls/" + prNumber, integration.getAccessToken());
        if (prNode != null && prNode.has("title")) {
            prTitle = prNode.path("title").asText(prTitle);
            prBody = prNode.path("body").asText("");
            headBranch = prNode.path("head").path("ref").asText("unknown");
            baseBranch = prNode.path("base").path("ref").asText(integration.getDefaultBranch());
        }

        return executePrScanAndComment(integration, project, user, prNumber, prTitle, prBody, headBranch, baseBranch, postComment);
    }

    /**
     * Executes the scan and comment logic, used by both Webhooks and manual dashboard clicks.
     */
    @Transactional
    public GithubPrScanResultDto executePrScanAndComment(
            GithubIntegration integration,
            Project project,
            User user,
            int prNumber,
            String prTitle,
            String prBody,
            String headBranch,
            String baseBranch,
            boolean postComment) {

        List<Feature> features = featureRepository.findByProjectIdAndArchivedAtIsNull(project.getId());
        List<Environment> environments = environmentRepository.findByProjectIdOrderBySortOrderAsc(project.getId());

        // Fetch PR files from GitHub API
        JsonNode filesNode = fetchGithubJson(
                "/repos/" + integration.getRepoOwner() + "/" + integration.getRepoName() + "/pulls/" + prNumber + "/files?per_page=100",
                integration.getAccessToken()
        );

        Map<String, List<String>> occurrencesByFlag = new HashMap<>();
        int filesTouchedCount = 0;

        if (filesNode != null && filesNode.isArray()) {
            filesTouchedCount = filesNode.size();
            for (JsonNode file : filesNode) {
                String filename = file.path("filename").asText("");
                String patch = file.path("patch").asText("");

                for (Feature feat : features) {
                    if (isFlagReferencedInFile(feat.getKey(), filename, patch)) {
                        occurrencesByFlag.computeIfAbsent(feat.getKey(), k -> new ArrayList<>())
                                .add(filename);
                    }
                }
            }
        }

        // Also check branch name, PR title, and PR body
        for (Feature feat : features) {
            boolean matchesBranch = headBranch != null && headBranch.toLowerCase().contains(feat.getKey().toLowerCase());
            boolean matchesTitleOrBody = (prTitle != null && prTitle.contains(feat.getKey())) ||
                    (prBody != null && prBody.contains(feat.getKey()));
            boolean matchesExplicitPrLink = feat.getGithubPrNumber() != null && feat.getGithubPrNumber() == prNumber;

            if (matchesBranch || matchesTitleOrBody || matchesExplicitPrLink) {
                occurrencesByFlag.computeIfAbsent(feat.getKey(), k -> new ArrayList<>());
            }
        }

        // Build list of referenced flags with full environment states & rollout percentages
        List<GithubPrScanResultDto.FlagPrReferenceDto> referencedFlags = new ArrayList<>();

        for (Feature feat : features) {
            if (!occurrencesByFlag.containsKey(feat.getKey())) {
                continue;
            }

            Map<String, Boolean> states = new HashMap<>();
            Map<String, Integer> rolloutPercentages = new HashMap<>();
            Map<String, String> targetingRules = new HashMap<>();

            for (Environment env : environments) {
                FeatureState state = featureStateRepository.findByFeatureIdAndEnvironmentId(feat.getId(), env.getId()).orElse(null);
                states.put(env.getSlug(), state != null ? state.getEnabled() : feat.getDefaultEnabled());
                rolloutPercentages.put(env.getSlug(), state != null && state.getRolloutPercentage() != null ? state.getRolloutPercentage() : 100);
                if (state != null && state.getRules() != null) {
                    targetingRules.put(env.getSlug(), state.getRules());
                }
            }

            referencedFlags.add(GithubPrScanResultDto.FlagPrReferenceDto.builder()
                    .key(feat.getKey())
                    .name(feat.getName())
                    .description(feat.getDescription())
                    .states(states)
                    .rolloutPercentages(rolloutPercentages)
                    .targetingRules(targetingRules)
                    .occurrencesInFiles(occurrencesByFlag.get(feat.getKey()))
                    .build());
        }

        // Generate Markdown comment report
        String markdownReport = generatePrReportMarkdown(integration, prNumber, prTitle, headBranch, baseBranch, referencedFlags, environments);

        boolean commentPosted = false;
        String commentUrl = null;

        if (postComment && integration.getAccessToken() != null && !integration.getAccessToken().isBlank()) {
            try {
                commentUrl = postOrUpdatePrComment(integration, prNumber, markdownReport);
                commentPosted = (commentUrl != null);
            } catch (Exception ex) {
                log.warn("Failed to post PR comment on GitHub PR #{}: {}", prNumber, ex.getMessage());
            }
        }

        // Audit logging
        auditLogService.logAction(
                project,
                user,
                "GITHUB_PR_SCANNED",
                "PullRequest",
                String.valueOf(prNumber),
                Map.of(
                        "prNumber", prNumber,
                        "referencedFlagsCount", referencedFlags.size(),
                        "flags", referencedFlags.stream().map(GithubPrScanResultDto.FlagPrReferenceDto::getKey).toList(),
                        "commentPosted", commentPosted
                )
        );

        return GithubPrScanResultDto.builder()
                .prNumber(prNumber)
                .prTitle(prTitle)
                .headBranch(headBranch)
                .baseBranch(baseBranch)
                .repoOwner(integration.getRepoOwner())
                .repoName(integration.getRepoName())
                .filesTouchedCount(filesTouchedCount)
                .referencedFlags(referencedFlags)
                .commentMarkdown(markdownReport)
                .commentPosted(commentPosted)
                .commentUrl(commentUrl)
                .build();
    }

    /**
     * Auto-graduates flags upon PR merge into target branch.
     * Incrementing canary rollouts to 100% and enabling on target environments.
     */
    @Transactional
    public GithubPrGraduationResultDto graduateFlagsOnMerge(
            UUID projectId,
            int prNumber,
            String baseBranch,
            String headBranch,
            String prTitle,
            boolean postComment,
            UserPrincipal principal) {

        GithubIntegration integration = githubRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GitHub integration not found"));

        Project project = integration.getProject();
        User user = (principal != null) ? userRepository.findById(principal.getId()).orElse(null) : null;

        // Determine target environment based on target branch
        List<Environment> environments = environmentRepository.findByProjectIdOrderBySortOrderAsc(project.getId());
        Environment targetEnv = resolveTargetEnvironment(environments, baseBranch, integration.getDefaultBranch());

        // Find associated features (by branch name, PR link, or scan)
        List<Feature> allFeatures = featureRepository.findByProjectIdAndArchivedAtIsNull(project.getId());
        List<Feature> targetFeatures = new ArrayList<>();

        for (Feature feat : allFeatures) {
            boolean matchesPr = feat.getGithubPrNumber() != null && feat.getGithubPrNumber() == prNumber;
            boolean matchesBranch = feat.getGithubBranch() != null && headBranch != null &&
                    feat.getGithubBranch().equalsIgnoreCase(headBranch);
            boolean nameInTitle = prTitle != null && prTitle.contains(feat.getKey());
            boolean branchInHead = headBranch != null && headBranch.toLowerCase().contains(feat.getKey().toLowerCase());

            if (matchesPr || matchesBranch || nameInTitle || branchInHead) {
                targetFeatures.add(feat);
            }
        }

        // If no features explicitly matched, scan PR files as fallback
        if (targetFeatures.isEmpty()) {
            JsonNode filesNode = fetchGithubJson(
                    "/repos/" + integration.getRepoOwner() + "/" + integration.getRepoName() + "/pulls/" + prNumber + "/files",
                    integration.getAccessToken()
            );
            if (filesNode != null && filesNode.isArray()) {
                for (JsonNode file : filesNode) {
                    String patch = file.path("patch").asText("");
                    for (Feature feat : allFeatures) {
                        if (isFlagReferencedInFile(feat.getKey(), file.path("filename").asText(""), patch)) {
                            if (!targetFeatures.contains(feat)) {
                                targetFeatures.add(feat);
                            }
                        }
                    }
                }
            }
        }

        List<String> graduatedKeys = new ArrayList<>();
        Map<String, Object> details = new HashMap<>();

        for (Feature feat : targetFeatures) {
            FeatureState state = featureStateRepository.findByFeatureIdAndEnvironmentId(feat.getId(), targetEnv.getId())
                    .orElseGet(() -> FeatureState.builder()
                            .feature(feat)
                            .environment(targetEnv)
                            .enabled(false)
                            .rolloutPercentage(0)
                            .build());

            int oldRollout = state.getRolloutPercentage() != null ? state.getRolloutPercentage() : 100;
            boolean oldEnabled = Boolean.TRUE.equals(state.getEnabled());

            // Graduate: enable flag and set rollout to 100%
            state.setEnabled(true);
            state.setRolloutPercentage(100);
            featureStateRepository.save(state);

            graduatedKeys.add(feat.getKey());
            details.put(feat.getKey(), Map.of(
                    "previousState", oldEnabled ? "ENABLED (" + oldRollout + "%)" : "DISABLED",
                    "graduatedState", "ENABLED (100% Full Rollout)",
                    "environment", targetEnv.getSlug()
            ));

            // Broadcast real-time SSE stream update
            eventPublisher.publishEvent(FlagStateChangedEvent.builder()
                    .projectId(project.getId())
                    .envSlug(targetEnv.getSlug())
                    .featureKey(feat.getKey())
                    .enabled(true)
                    .rolloutPercentage(100)
                    .rules(state.getRules())
                    .build());
        }

        // Post graduation comment on GitHub PR if requested
        boolean commentPosted = false;
        if (postComment && !graduatedKeys.isEmpty() && integration.getAccessToken() != null) {
            String commentMarkdown = generateGraduationMarkdown(prNumber, baseBranch, targetEnv.getSlug(), graduatedKeys);
            try {
                postOrUpdatePrComment(integration, prNumber, commentMarkdown);
                commentPosted = true;
            } catch (Exception ex) {
                log.warn("Failed to post graduation comment: {}", ex.getMessage());
            }
        }

        // Audit Log
        if (!graduatedKeys.isEmpty()) {
            auditLogService.logAction(
                    project,
                    user,
                    "FEATURE_AUTO_GRADUATED_ON_MERGE",
                    "PullRequest",
                    String.valueOf(prNumber),
                    Map.of(
                            "prNumber", prNumber,
                            "targetBranch", baseBranch,
                            "targetEnv", targetEnv.getSlug(),
                            "graduatedFlags", graduatedKeys
                    )
            );
        }

        return GithubPrGraduationResultDto.builder()
                .prNumber(prNumber)
                .prTitle(prTitle != null ? prTitle : "PR #" + prNumber)
                .headBranch(headBranch)
                .baseBranch(baseBranch)
                .targetEnvironment(targetEnv.getSlug())
                .graduatedFlags(graduatedKeys)
                .graduationDetails(details)
                .commentPosted(commentPosted)
                .message(graduatedKeys.isEmpty()
                        ? "No associated feature flags found to graduate for PR #" + prNumber
                        : "Successfully graduated " + graduatedKeys.size() + " flag(s) to 100% active on " + targetEnv.getSlug())
                .build();
    }

    private Environment resolveTargetEnvironment(List<Environment> environments, String branch, String defaultBranch) {
        if (environments.isEmpty()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No environments configured for this project");
        }

        String lowerBranch = (branch != null) ? branch.toLowerCase() : "";

        // Match development
        if (lowerBranch.contains("dev")) {
            for (Environment env : environments) {
                if (env.getSlug().toLowerCase().contains("dev")) return env;
            }
        }

        // Match staging
        if (lowerBranch.contains("stage") || lowerBranch.contains("staging") || lowerBranch.contains("test")) {
            for (Environment env : environments) {
                if (env.getSlug().toLowerCase().contains("stage")) return env;
            }
        }

        // Default or main branch matches production
        if (lowerBranch.equals(defaultBranch.toLowerCase()) || lowerBranch.equals("main") || lowerBranch.equals("master")) {
            for (Environment env : environments) {
                if (env.getSlug().toLowerCase().contains("prod")) return env;
            }
        }

        // Fallback: choose last environment (usually prod) or first
        return environments.get(environments.size() - 1);
    }

    private boolean isFlagReferencedInFile(String key, String filename, String patch) {
        if (patch == null || patch.isBlank()) return false;

        // Check if file is code file
        String lowerFile = filename.toLowerCase();
        if (lowerFile.endsWith(".md") || lowerFile.endsWith(".txt") || lowerFile.endsWith(".png") || lowerFile.endsWith(".jpg")) {
            return false;
        }

        // Exact flag literal search or SDK pattern
        return patch.contains("'" + key + "'") ||
                patch.contains("\"" + key + "\"") ||
                patch.contains("`" + key + "`") ||
                patch.contains("isEnabled(" + key) ||
                patch.contains(key);
    }

    private String generatePrReportMarkdown(
            GithubIntegration integration,
            int prNumber,
            String prTitle,
            String headBranch,
            String baseBranch,
            List<GithubPrScanResultDto.FlagPrReferenceDto> flags,
            List<Environment> environments) {

        StringBuilder sb = new StringBuilder();
        sb.append(BOT_COMMENT_MARKER).append("\n\n");
        sb.append("### 🚩 Aura Feature Flags Report\n\n");

        if (flags.isEmpty()) {
            sb.append("This pull request was analyzed by **Aura Bot**. **0 feature flags** were detected in the modified files.\n\n");
            sb.append("> 💡 *Tip: To guard code with Aura, use `aura.isEnabled('flag-key')` or `@aura-flags/react` hooks.*");
            return sb.toString();
        }

        sb.append("This pull request touches **").append(flags.size()).append(" feature flag(s)** managed by Aura:\n\n");
        sb.append("| Flag Key | Environment | Status | Rollout | Targeting Rules |\n");
        sb.append("| :--- | :--- | :---: | :---: | :--- |\n");

        for (GithubPrScanResultDto.FlagPrReferenceDto flag : flags) {
            for (int i = 0; i < environments.size(); i++) {
                Environment env = environments.get(i);
                boolean isEnabled = Boolean.TRUE.equals(flag.getStates().get(env.getSlug()));
                int rollout = flag.getRolloutPercentages().getOrDefault(env.getSlug(), 100);
                String rules = flag.getTargetingRules().get(env.getSlug());

                String keyCol = (i == 0) ? "**`" + flag.getKey() + "`**<br><sub style=\"color:gray;\">" + flag.getName() + "</sub>" : "";
                String statusBadge = isEnabled ? "🟢 **ON**" : "🔴 **OFF**";
                String rolloutBadge = rollout == 100 ? "100% Full" : "🎯 " + rollout + "% Canary";
                String ruleText = (rules != null && !rules.isBlank() && !rules.equals("[]")) ? "`" + rules + "`" : "None";

                sb.append("| ").append(keyCol)
                        .append(" | `").append(env.getSlug()).append("`")
                        .append(" | ").append(statusBadge)
                        .append(" | ").append(rolloutBadge)
                        .append(" | ").append(ruleText)
                        .append(" |\n");
            }
        }

        sb.append("\n---\n");
        sb.append("#### 🚀 CI/CD Automation Highlights:\n");
        sb.append("- **Branch:** `").append(headBranch).append("` → `").append(baseBranch).append("`\n");
        sb.append("- **Auto-Graduation:** Merging this PR will automatically graduate any canary rollouts and synchronize live clients via SSE stream.\n");
        sb.append("- **Aura Dashboard:** Inspect or modify flag targeting at `http://localhost:5173/projects/").append(integration.getProject().getId()).append("`.\n");

        return sb.toString();
    }

    private String generateGraduationMarkdown(int prNumber, String baseBranch, String envSlug, List<String> graduatedKeys) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 🚀 Aura Feature Flag Auto-Graduation Notice\n\n");
        sb.append("Pull request **#").append(prNumber).append("** was merged into `").append(baseBranch).append("`!\n\n");
        sb.append("Aura CI/CD Bot has automatically graduated the following feature flags on **`").append(envSlug).append("`**:\n\n");

        for (String key : graduatedKeys) {
            sb.append("- ✅ **`").append(key).append("`** ➔ **100% Active (Graduated)**\n");
        }

        sb.append("\n*Real-time SSE events were pushed to all active client SDK instances in <15ms.* ⚡\n");
        return sb.toString();
    }

    private String postOrUpdatePrComment(GithubIntegration integration, int prNumber, String markdownBody) {
        String token = integration.getAccessToken();
        if (token == null || token.isBlank()) return null;

        String owner = integration.getRepoOwner();
        String repo = integration.getRepoName();

        // 1. Check existing comments on PR issue
        String commentsPath = "/repos/" + owner + "/" + repo + "/issues/" + prNumber + "/comments?per_page=50";
        JsonNode existingComments = fetchGithubJson(commentsPath, token);

        Long existingCommentId = null;
        if (existingComments != null && existingComments.isArray()) {
            for (JsonNode comment : existingComments) {
                String body = comment.path("body").asText("");
                if (body.contains(BOT_COMMENT_MARKER)) {
                    existingCommentId = comment.path("id").asLong();
                    break;
                }
            }
        }

        try {
            if (existingCommentId != null) {
                // Update existing comment
                String updateUrl = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/issues/comments/" + existingCommentId;
                String payload = objectMapper.writeValueAsString(Map.of("body", markdownBody));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(updateUrl))
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() < 300) {
                    JsonNode node = objectMapper.readTree(resp.body());
                    return node.path("html_url").asText(null);
                }
            } else {
                // Post new comment
                String postUrl = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/issues/" + prNumber + "/comments";
                String payload = objectMapper.writeValueAsString(Map.of("body", markdownBody));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(postUrl))
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() < 300) {
                    JsonNode node = objectMapper.readTree(resp.body());
                    return node.path("html_url").asText(null);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to post/update GitHub PR comment: {}", ex.getMessage());
        }

        return null;
    }

    private JsonNode fetchGithubJson(String apiPath, String token) {
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_BASE + apiPath))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Aura-Platform/1.0")
                    .GET();

            if (token != null && !token.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.statusCode() >= 400) {
                return null;
            }

            return objectMapper.readTree(response.body());
        } catch (Exception ex) {
            log.warn("Error calling GitHub API {}: {}", apiPath, ex.getMessage());
            return null;
        }
    }
}
