package com.aura.service;

import com.aura.dto.*;
import com.aura.entity.GithubIntegration;
import com.aura.entity.Project;
import com.aura.entity.User;
import com.aura.exception.ApiException;
import com.aura.repository.GithubIntegrationRepository;
import com.aura.repository.ProjectRepository;
import com.aura.repository.UserRepository;
import com.aura.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Service
public class GithubService {

    private static final Logger log = LoggerFactory.getLogger(GithubService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final GithubIntegrationRepository githubRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GithubService(
            GithubIntegrationRepository githubRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            AuditLogService auditLogService) {
        this.githubRepository = githubRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
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
        if (repoNode == null || repoNode.has("message")) {
            String msg = (repoNode != null && repoNode.has("message"))
                    ? repoNode.get("message").asText()
                    : "Repository not found or access denied";
            throw new ApiException(HttpStatus.BAD_REQUEST, "GitHub error: " + msg);
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
        String endpoint = "/repos/" + integration.getRepoOwner() + "/" + integration.getRepoName() + "/branches?per_page=100";
        JsonNode branchesNode = fetchGithubJson(endpoint, integration.getAccessToken());

        List<GithubBranchDto> branches = new ArrayList<>();
        if (branchesNode != null && branchesNode.isArray()) {
            for (JsonNode b : branchesNode) {
                branches.add(GithubBranchDto.builder()
                        .name(b.path("name").asText())
                        .commitSha(b.path("commit").path("sha").asText())
                        .isProtected(b.path("protected").asBoolean(false))
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

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() >= 400) {
                log.warn("GitHub API error {} for {}: {}", response.statusCode(), apiPath, response.body());
                try {
                    return objectMapper.readTree(response.body());
                } catch (Exception ignored) {
                    return null;
                }
            }

            return objectMapper.readTree(response.body());
        } catch (Exception ex) {
            log.error("Exception calling GitHub API {}: {}", apiPath, ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Failed to connect to GitHub API: " + ex.getMessage());
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
