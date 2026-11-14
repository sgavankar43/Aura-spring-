package com.aura.service;

import com.aura.dto.*;
import com.aura.entity.Feature;
import com.aura.entity.GithubAppInstallation;
import com.aura.entity.GithubIntegration;
import com.aura.entity.Project;
import com.aura.entity.User;
import com.aura.exception.ApiException;
import com.aura.repository.FeatureRepository;
import com.aura.repository.GithubAppInstallationRepository;
import com.aura.repository.GithubIntegrationRepository;
import com.aura.repository.ProjectRepository;
import com.aura.repository.UserRepository;
import com.aura.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GithubService {

    private static final Logger log = LoggerFactory.getLogger(GithubService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    @Value("${app.github.token:}")
    private String systemGithubToken;

    private static final long CACHE_TTL_MS = Duration.ofMinutes(5).toMillis();
    private final Map<String, CacheEntry> apiCache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final JsonNode data;
        final long timestamp;

        CacheEntry(JsonNode data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isFresh(long ttlMs) {
            return System.currentTimeMillis() - timestamp < ttlMs;
        }
    }

    private final GithubIntegrationRepository githubRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final FeatureRepository featureRepository;
    private final GithubAppInstallationRepository githubAppInstallationRepository;
    private final AuditLogService auditLogService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GithubService(
            GithubIntegrationRepository githubRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            FeatureRepository featureRepository,
            GithubAppInstallationRepository githubAppInstallationRepository,
            AuditLogService auditLogService) {
        this.githubRepository = githubRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.featureRepository = featureRepository;
        this.githubAppInstallationRepository = githubAppInstallationRepository;
        this.auditLogService = auditLogService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void initDefaultAppInstallations() {
        registerAppInstallationIfNotExists(1001L, "sgavankar43", "sgavankar43/testing");
        registerAppInstallationIfNotExists(1001L, "sgavankar43", "sgavankar43/Aura-spring-");
    }

    public void registerAppInstallationIfNotExists(Long installationId, String accountLogin, String repoFullName) {
        if (!githubAppInstallationRepository.existsByRepoFullNameIgnoreCase(repoFullName)) {
            githubAppInstallationRepository.save(GithubAppInstallation.builder()
                    .installationId(installationId)
                    .accountLogin(accountLogin)
                    .repoFullName(repoFullName)
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public List<String> getInstalledRepositories() {
        return githubAppInstallationRepository.findAll().stream()
                .map(GithubAppInstallation::getRepoFullName)
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional
    public GithubStatusResponse connectRepository(UUID projectId, GithubConnectRequest request, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        User user = userRepository.findById(principal.getId()).orElse(null);

        String[] ownerRepo = parseRepoString(request.getRepository());
        String owner = ownerRepo[0];
        String repo = ownerRepo[1];
        String token = (request.getAccessToken() != null && !request.getAccessToken().isBlank())
                ? request.getAccessToken().trim()
                : null;

        // Verify repository via GitHub API
        JsonNode repoNode = fetchGithubJson("/repos/" + owner + "/" + repo, token);
        boolean isRateLimited = repoNode != null && repoNode.has("message") &&
                repoNode.get("message").asText("").toLowerCase().contains("rate limit");

        String fullName = owner + "/" + repo;
        boolean isInstalledViaApp = githubAppInstallationRepository.existsByRepoFullNameIgnoreCase(fullName);

        if (isRateLimited) {
            // Only allow connect-through-rate-limit for repos already authorized via the Aura GitHub App.
            // Never synthesize write permissions for arbitrary public repositories.
            if (!isInstalledViaApp) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "GitHub API rate limit exceeded while verifying '" + fullName + "'. " +
                        "Install the Aura GitHub App on this repository first, then retry.");
            }
            log.warn("GitHub rate limit hit while connecting {}/{}. Using App-authorized synthetic metadata.", owner, repo);
            ObjectNode synthetic = objectMapper.createObjectNode();
            synthetic.put("name", repo);
            synthetic.put("full_name", fullName);
            synthetic.put("default_branch", "main");
            synthetic.put("private", false);
            synthetic.put("html_url", "https://github.com/" + fullName);
            synthetic.put("description", "Connected GitHub Repository (" + fullName + ")");
            synthetic.put("stargazers_count", 0);
            synthetic.put("forks_count", 0);
            synthetic.put("open_issues_count", 0);
            repoNode = synthetic;
        } else if (repoNode == null || repoNode.has("message")) {
            String msg = (repoNode != null && repoNode.has("message"))
                    ? repoNode.get("message").asText()
                    : "Repository not found or access denied";
            throw new ApiException(HttpStatus.BAD_REQUEST, "GitHub error: " + msg);
        }

        // Security check: Only allow repositories authorized via the Aura GitHub App or verified write credentials
        boolean hasWritePermission = repoNode.has("permissions") &&
                (repoNode.path("permissions").path("push").asBoolean(false) ||
                 repoNode.path("permissions").path("admin").asBoolean(false));

        if (!isInstalledViaApp && !hasWritePermission) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Access denied: Repository '" + fullName + "' has not been authorized through your Aura GitHub App. " +
                    "To prevent unauthorized public repository linking, please install the Aura GitHub App on this repository first.");
        }

        String defaultBranch = (request.getDefaultBranch() != null && !request.getDefaultBranch().isBlank())
                ? request.getDefaultBranch().trim()
                : repoNode.path("default_branch").asText("main");

        GithubIntegration integration = githubRepository.findByProjectId(projectId)
                .orElseGet(() -> GithubIntegration.builder()
                        .project(project)
                        .build());

        integration.setRepoOwner(owner);
        integration.setRepoName(repo);
        integration.setAccessToken(token);
        integration.setDefaultBranch(defaultBranch);

        integration = githubRepository.save(integration);

        auditLogService.logAction(
                project,
                user,
                "GITHUB_CONNECTED",
                "GithubIntegration",
                integration.getId().toString(),
                Map.of("repo", owner + "/" + repo, "branch", defaultBranch)
        );

        return buildStatusResponse(integration, repoNode);
    }

    @Transactional(readOnly = true)
    public GithubStatusResponse getStatus(UUID projectId) {
        Optional<GithubIntegration> opt = githubRepository.findByProjectId(projectId);
        if (opt.isEmpty()) {
            return GithubStatusResponse.builder()
                    .connected(false)
                    .build();
        }

        GithubIntegration integration = opt.get();
        JsonNode repoNode = null;
        try {
            repoNode = fetchGithubJson("/repos/" + integration.getRepoOwner() + "/" + integration.getRepoName(), integration.getAccessToken());
        } catch (Exception ex) {
            log.warn("Failed to fetch live stats for {}/{}: {}", integration.getRepoOwner(), integration.getRepoName(), ex.getMessage());
        }

        return buildStatusResponse(integration, repoNode);
    }

    @Transactional
    public void disconnectRepository(UUID projectId, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        User user = userRepository.findById(principal.getId()).orElse(null);

        GithubIntegration integration = githubRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No GitHub repository connected to this project"));

        String repoDesc = integration.getRepoOwner() + "/" + integration.getRepoName();
        githubRepository.delete(integration);

        auditLogService.logAction(
                project,
                user,
                "GITHUB_DISCONNECTED",
                "GithubIntegration",
                integration.getId().toString(),
                Map.of("repo", repoDesc)
        );
    }

    @Transactional(readOnly = true)
    public List<GithubBranchDto> getBranches(UUID projectId) {
        GithubIntegration integration = getIntegrationOrThrow(projectId);
        String defaultBranch = (integration.getDefaultBranch() != null && !integration.getDefaultBranch().isBlank())
                ? integration.getDefaultBranch().trim()
                : "main";

        List<Feature> features = featureRepository.findByProjectId(projectId);
        Map<String, String> occupiedBranches = new HashMap<>();
        for (Feature f : features) {
            if (f.getGithubBranch() != null && !f.getGithubBranch().isBlank()) {
                occupiedBranches.put(f.getGithubBranch().trim().toLowerCase(), f.getKey());
            }
        }

        String endpoint = "/repos/" + integration.getRepoOwner() + "/" + integration.getRepoName() + "/branches?per_page=100";
        JsonNode branchesNode = fetchGithubJson(endpoint, integration.getAccessToken());

        List<GithubBranchDto> branches = new ArrayList<>();
        if (branchesNode != null && branchesNode.isArray()) {
            for (JsonNode b : branchesNode) {
                String branchName = b.path("name").asText();
                boolean isDefault = branchName.equalsIgnoreCase(defaultBranch)
                        || branchName.equalsIgnoreCase("main")
                        || branchName.equalsIgnoreCase("master");
                String occupiedBy = occupiedBranches.get(branchName.toLowerCase());
                boolean isOccupied = occupiedBy != null;

                branches.add(GithubBranchDto.builder()
                        .name(branchName)
                        .commitSha(b.path("commit").path("sha").asText())
                        .isProtected(b.path("protected").asBoolean(false))
                        .isDefault(isDefault)
                        .isOccupied(isOccupied)
                        .occupiedByFeatureKey(occupiedBy)
                        .build());
            }
        }

        // When GitHub is rate-limited/unreachable, keep UI usable without inventing fake feature branches.
        // Only surface the default branch plus branches already linked to flags in this project.
        if (branches.isEmpty()) {
            log.info("Serving resilient branch fallback for {}/{} (API empty/rate-limited)",
                    integration.getRepoOwner(), integration.getRepoName());
            branches.add(GithubBranchDto.builder()
                    .name(defaultBranch)
                    .commitSha("HEAD")
                    .isProtected(true)
                    .isDefault(true)
                    .isOccupied(false)
                    .build());

            for (Map.Entry<String, String> entry : occupiedBranches.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(defaultBranch)) {
                    continue;
                }
                branches.add(GithubBranchDto.builder()
                        .name(entry.getKey())
                        .commitSha("HEAD")
                        .isProtected(false)
                        .isDefault(false)
                        .isOccupied(true)
                        .occupiedByFeatureKey(entry.getValue())
                        .build());
            }
        }
        return branches;
    }

    @Transactional(readOnly = true)
    public List<GithubTagDto> getTags(UUID projectId) {
        GithubIntegration integration = getIntegrationOrThrow(projectId);
        String endpoint = "/repos/" + integration.getRepoOwner() + "/" + integration.getRepoName() + "/tags?per_page=100";
        JsonNode tagsNode = fetchGithubJson(endpoint, integration.getAccessToken());

        List<GithubTagDto> tags = new ArrayList<>();
        if (tagsNode != null && tagsNode.isArray()) {
            for (JsonNode t : tagsNode) {
                tags.add(GithubTagDto.builder()
                        .name(t.path("name").asText())
                        .commitSha(t.path("commit").path("sha").asText())
                        .tarballUrl(t.path("tarball_url").asText(null))
                        .zipballUrl(t.path("zipball_url").asText(null))
                        .build());
            }
        }

        // Prefer empty over fabricated release tags when GitHub is unavailable.
        return tags;
    }

    @Transactional(readOnly = true)
    public List<GithubCommitDto> getCommits(UUID projectId, String branch, int limit) {
        GithubIntegration integration = getIntegrationOrThrow(projectId);
        String targetBranch = (branch != null && !branch.isBlank()) ? branch : integration.getDefaultBranch();
        int perPage = Math.min(Math.max(1, limit), 50);

        String endpoint = "/repos/" + integration.getRepoOwner() + "/" + integration.getRepoName() +
                "/commits?sha=" + targetBranch + "&per_page=" + perPage;
        JsonNode commitsNode = fetchGithubJson(endpoint, integration.getAccessToken());

        List<GithubCommitDto> commits = new ArrayList<>();
        if (commitsNode != null && commitsNode.isArray()) {
            for (JsonNode c : commitsNode) {
                String sha = c.path("sha").asText("");
                String shortSha = sha.length() >= 7 ? sha.substring(0, 7) : sha;
                JsonNode commitObj = c.path("commit");
                JsonNode authorObj = commitObj.path("author");
                JsonNode gitAuthor = c.path("author");

                commits.add(GithubCommitDto.builder()
                        .sha(sha)
                        .shortSha(shortSha)
                        .message(commitObj.path("message").asText())
                        .authorName(authorObj.path("name").asText("Unknown"))
                        .authorEmail(authorObj.path("email").asText(null))
                        .authorAvatar(gitAuthor.path("avatar_url").asText(null))
                        .date(authorObj.path("date").asText(null))
                        .htmlUrl(c.path("html_url").asText(null))
                        .build());
            }
        }

        // Prefer empty over fabricated commit history when GitHub is unavailable.
        return commits;
    }

    @Transactional(readOnly = true)
    public List<GithubContentDto> getTreeOrContents(UUID projectId, String branch, String path) {
        GithubIntegration integration = getIntegrationOrThrow(projectId);
        String targetBranch = (branch != null && !branch.isBlank()) ? branch : integration.getDefaultBranch();
        String targetPath = (path != null && !path.isBlank()) ? path.trim() : "";
        if (targetPath.startsWith("/")) {
            targetPath = targetPath.substring(1);
        }

        String endpoint = "/repos/" + integration.getRepoOwner() + "/" + integration.getRepoName() +
                "/contents/" + targetPath + "?ref=" + targetBranch;
        JsonNode contentNode = fetchGithubJson(endpoint, integration.getAccessToken());

        List<GithubContentDto> items = new ArrayList<>();
        if (contentNode != null) {
            if (contentNode.isArray()) {
                for (JsonNode item : contentNode) {
                    items.add(GithubContentDto.builder()
                            .name(item.path("name").asText())
                            .path(item.path("path").asText())
                            .sha(item.path("sha").asText())
                            .size(item.path("size").asLong(0))
                            .type(item.path("type").asText("file"))
                            .downloadUrl(item.path("download_url").asText(null))
                            .build());
                }
            } else if (contentNode.isObject()) {
                String contentEncoded = contentNode.path("content").asText("");
                String decoded = null;
                if (!contentEncoded.isBlank()) {
                    try {
                        String cleaned = contentEncoded.replaceAll("\\s+", "");
                        decoded = new String(Base64.getDecoder().decode(cleaned), StandardCharsets.UTF_8);
                    } catch (Exception ignored) {
                        decoded = contentEncoded;
                    }
                }

                items.add(GithubContentDto.builder()
                        .name(contentNode.path("name").asText())
                        .path(contentNode.path("path").asText())
                        .sha(contentNode.path("sha").asText())
                        .size(contentNode.path("size").asLong(0))
                        .type(contentNode.path("type").asText("file"))
                        .downloadUrl(contentNode.path("download_url").asText(null))
                        .content(decoded)
                        .encoding(contentNode.path("encoding").asText("utf-8"))
                        .build());
            }
        }

        // Prefer empty over fabricated tree entries when GitHub is unavailable.
        return items;
    }

    @Transactional(readOnly = true)
    public List<GithubPullRequestDto> getPullRequests(UUID projectId, String state) {
        GithubIntegration integration = getIntegrationOrThrow(projectId);
        String prState = (state != null && !state.isBlank()) ? state : "all";

        String endpoint = "/repos/" + integration.getRepoOwner() + "/" + integration.getRepoName() +
                "/pulls?state=" + prState + "&per_page=50";
        JsonNode prsNode = fetchGithubJson(endpoint, integration.getAccessToken());

        List<GithubPullRequestDto> prs = new ArrayList<>();
        if (prsNode != null && prsNode.isArray()) {
            for (JsonNode pr : prsNode) {
                prs.add(GithubPullRequestDto.builder()
                        .number(pr.path("number").asInt())
                        .title(pr.path("title").asText())
                        .state(pr.path("state").asText())
                        .headBranch(pr.path("head").path("ref").asText())
                        .baseBranch(pr.path("base").path("ref").asText())
                        .authorName(pr.path("user").path("login").asText("Unknown"))
                        .authorAvatar(pr.path("user").path("avatar_url").asText(null))
                        .createdAt(pr.path("created_at").asText(null))
                        .htmlUrl(pr.path("html_url").asText(null))
                        .draft(pr.path("draft").asBoolean(false))
                        .build());
            }
        }

        // Prefer empty over fabricated PRs when GitHub is unavailable.
        return prs;
    }

    private GithubIntegration getIntegrationOrThrow(UUID projectId) {
        return githubRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GitHub repository is not connected for this project"));
    }

    private GithubStatusResponse buildStatusResponse(GithubIntegration integration, JsonNode repoNode) {
        String owner = integration.getRepoOwner();
        String repo = integration.getRepoName();
        String fullName = owner + "/" + repo;

        String description = repoNode != null ? repoNode.path("description").asText(null) : null;
        String htmlUrl = repoNode != null ? repoNode.path("html_url").asText("https://github.com/" + fullName) : "https://github.com/" + fullName;
        boolean isPrivate = repoNode != null && repoNode.path("private").asBoolean(false);
        int stars = repoNode != null ? repoNode.path("stargazers_count").asInt(0) : 0;
        int forks = repoNode != null ? repoNode.path("forks_count").asInt(0) : 0;
        int issues = repoNode != null ? repoNode.path("open_issues_count").asInt(0) : 0;

        return GithubStatusResponse.builder()
                .connected(true)
                .repoOwner(owner)
                .repoName(repo)
                .repoFullName(fullName)
                .repoUrl(htmlUrl)
                .description(description)
                .defaultBranch(integration.getDefaultBranch())
                .isPrivate(isPrivate)
                .starsCount(stars)
                .forksCount(forks)
                .openIssuesCount(issues)
                .connectedAt(integration.getCreatedAt())
                .updatedAt(integration.getUpdatedAt())
                .build();
    }

    public String resolveToken(String userToken) {
        return GithubTokenResolver.resolveToken(userToken, systemGithubToken);
    }

    private JsonNode fetchGithubJson(String apiPath, String token) {
        String effectiveToken = resolveToken(token);
        String cacheKey = apiPath + "::" + (effectiveToken != null ? effectiveToken.hashCode() : "anon");

        CacheEntry cached = apiCache.get(cacheKey);
        if (cached != null && cached.isFresh(CACHE_TTL_MS)) {
            return cached.data;
        }

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_BASE + apiPath))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Aura-Platform/1.0")
                    .GET();

            if (effectiveToken != null && !effectiveToken.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + effectiveToken);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() == 403 || response.statusCode() == 429) {
                String body = response.body();
                boolean isRateLimit = body != null && (body.contains("rate limit") || body.contains("API rate limit exceeded"));
                if (isRateLimit) {
                    log.warn("GitHub API rate limit exceeded on {}. Serving cached or synthetic fallback.", apiPath);
                    if (cached != null) {
                        return cached.data;
                    }
                }
            }

            if (response.statusCode() >= 400) {
                log.warn("GitHub API error {} for {}: {}", response.statusCode(), apiPath, response.body());
                try {
                    return objectMapper.readTree(response.body());
                } catch (Exception ignored) {
                    return null;
                }
            }

            JsonNode parsed = objectMapper.readTree(response.body());
            if (parsed != null && !parsed.has("message")) {
                apiCache.put(cacheKey, new CacheEntry(parsed));
            }
            return parsed;
        } catch (Exception ex) {
            if (cached != null) {
                log.warn("GitHub API call failed for {}, falling back to cached response: {}", apiPath, ex.getMessage());
                return cached.data;
            }
            log.error("Exception calling GitHub API {}: {}", apiPath, ex.getMessage());
            return null;
        }
    }

    private String[] parseRepoString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Repository cannot be empty");
        }
        String clean = raw.trim()
                .replace("https://github.com/", "")
                .replace("http://github.com/", "")
                .replace("github.com/", "");

        if (clean.endsWith(".git")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }

        String[] parts = clean.split("/");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid repository format. Please use 'owner/repo' (e.g. 'facebook/react')");
        }
        return new String[]{parts[0].trim(), parts[1].trim()};
    }
}
