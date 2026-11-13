package com.aura;

import com.aura.dto.*;
import com.aura.entity.*;
import com.aura.repository.*;
import com.aura.security.UserPrincipal;
import com.aura.service.FeatureService;
import com.aura.service.GithubBotService;
import com.aura.service.StaleFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class GithubBotAndStaleFlagTest {

    @Autowired
    private GithubBotService githubBotService;

    @Autowired
    private StaleFlagService staleFlagService;

    @Autowired
    private FeatureService featureService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private FeatureStateRepository featureStateRepository;

    @Autowired
    private GithubIntegrationRepository githubIntegrationRepository;

    @Autowired
    private UserRepository userRepository;

    private Project testProject;
    private User testUser;
    private UserPrincipal principal;
    private Environment devEnv;
    private Environment stageEnv;
    private Environment prodEnv;
    private GithubIntegration integration;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .name("Bot Tester")
                .email("bot-tester-" + UUID.randomUUID() + "@aura.dev")
                .password("secure_hash")
                .role("ADMIN")
                .build());

        principal = UserPrincipal.create(testUser);

        testProject = projectRepository.save(Project.builder()
                .name("Bot Test Project")
                .apiKey("aura_pk_test_" + UUID.randomUUID().toString().replace("-", ""))
                .user(testUser)
                .build());

        devEnv = environmentRepository.save(Environment.builder()
                .project(testProject)
                .name("Development")
                .slug("development")
                .sortOrder(1)
                .build());

        stageEnv = environmentRepository.save(Environment.builder()
                .project(testProject)
                .name("Staging")
                .slug("staging")
                .sortOrder(2)
                .build());

        prodEnv = environmentRepository.save(Environment.builder()
                .project(testProject)
                .name("Production")
                .slug("production")
                .sortOrder(3)
                .build());

        integration = githubIntegrationRepository.save(GithubIntegration.builder()
                .project(testProject)
                .repoOwner("aura-org")
                .repoName("demo-store")
                .defaultBranch("main")
                .build());
    }

    @Test
    @DisplayName("8.1 Automated PR Scan detects referenced flags and formats markdown report")
    void testPrScanAndMarkdownReport() {
        // Create 2 features
        Feature flag1 = featureRepository.save(Feature.builder()
                .project(testProject)
                .key("fast-checkout-v2")
                .name("Fast 1-Click Checkout")
                .defaultEnabled(false)
                .githubBranch("feature/fast-checkout-v2")
                .githubPrNumber(42)
                .build());

        Feature flag2 = featureRepository.save(Feature.builder()
                .project(testProject)
                .key("ai-copilot-beta")
                .name("AI Assistant Copilot")
                .defaultEnabled(false)
                .build());

        featureStateRepository.save(FeatureState.builder()
                .feature(flag1)
                .environment(devEnv)
                .enabled(true)
                .rolloutPercentage(100)
                .build());

        featureStateRepository.save(FeatureState.builder()
                .feature(flag1)
                .environment(prodEnv)
                .enabled(true)
                .rolloutPercentage(25)
                .rules("[{\"attribute\":\"role\",\"operator\":\"equals\",\"value\":\"beta\"}]")
                .build());

        // Scan PR with head branch matching flag1 and title mentioning flag2
        GithubPrScanResultDto result = githubBotService.executePrScanAndComment(
                integration,
                testProject,
                testUser,
                42,
                "feat: implement ai-copilot-beta and fast checkout",
                "Fixes issues with shopping cart",
                "feature/fast-checkout-v2",
                "main",
                false
        );

        assertThat(result).isNotNull();
        assertThat(result.getPrNumber()).isEqualTo(42);
        assertThat(result.getReferencedFlags()).hasSize(2);

        // Check report contains markdown table
        String report = result.getCommentMarkdown();
        assertThat(report).contains("### 🚩 Aura Feature Flags Report");
        assertThat(report).contains("fast-checkout-v2");
        assertThat(report).contains("ai-copilot-beta");
        assertThat(report).contains("25% Canary");
    }

    @Test
    @DisplayName("8.2 Auto-Graduate Rollout on PR Merge promotes canary to 100% and enables flag")
    void testAutoGraduationOnMerge() {
        Feature flag = featureRepository.save(Feature.builder()
                .project(testProject)
                .key("canary-search")
                .name("Vector Search")
                .defaultEnabled(false)
                .githubBranch("feature/canary-search")
                .githubPrNumber(108)
                .build());

        // Initially in 20% canary on production
        FeatureState prodState = featureStateRepository.save(FeatureState.builder()
                .feature(flag)
                .environment(prodEnv)
                .enabled(true)
                .rolloutPercentage(20)
                .build());

        // Simulate PR merge into main branch
        GithubPrGraduationResultDto gradResult = githubBotService.graduateFlagsOnMerge(
                testProject.getId(),
                108,
                "main",
                "feature/canary-search",
                "feat: finalize vector search",
                false,
                principal
        );

        assertThat(gradResult).isNotNull();
        assertThat(gradResult.getGraduatedFlags()).contains("canary-search");
        assertThat(gradResult.getTargetEnvironment()).isEqualTo("production");

        // Reload state from database
        FeatureState updatedState = featureStateRepository.findByFeatureIdAndEnvironmentId(flag.getId(), prodEnv.getId()).orElseThrow();
        assertThat(updatedState.getEnabled()).isTrue();
        assertThat(updatedState.getRolloutPercentage()).isEqualTo(100);
    }

    @Test
    @DisplayName("8.3 Stale Flag Scanner identifies 100% rolled out flags and dormant flags")
    void testStaleFlagScanner() {
        // 1. Stale flag: 100% enabled in all envs with older timestamp
        Feature staleFlag = featureRepository.save(Feature.builder()
                .project(testProject)
                .key("stale-banner")
                .name("Legacy Banner")
                .defaultEnabled(true)
                .createdAt(LocalDateTime.now().minusDays(45))
                .updatedAt(LocalDateTime.now().minusDays(45))
                .build());

        for (Environment env : List.of(devEnv, stageEnv, prodEnv)) {
            featureStateRepository.save(FeatureState.builder()
                    .feature(staleFlag)
                    .environment(env)
                    .enabled(true)
                    .rolloutPercentage(100)
                    .build());
        }

        // 2. Dormant flag: disabled in all envs
        Feature dormantFlag = featureRepository.save(Feature.builder()
                .project(testProject)
                .key("abandoned-experiment")
                .name("Old Experiment")
                .defaultEnabled(false)
                .createdAt(LocalDateTime.now().minusDays(60))
                .updatedAt(LocalDateTime.now().minusDays(60))
                .build());

        for (Environment env : List.of(devEnv, stageEnv, prodEnv)) {
            featureStateRepository.save(FeatureState.builder()
                    .feature(dormantFlag)
                    .environment(env)
                    .enabled(false)
                    .rolloutPercentage(0)
                    .build());
        }

        // Run analysis
        StaleFlagReportDto report = staleFlagService.generateStaleFlagReport(testProject.getId());

        assertThat(report).isNotNull();
        assertThat(report.getTotalFlags()).isEqualTo(2);
        assertThat(report.getStaleCount()).isGreaterThanOrEqualTo(1);
        assertThat(report.getDormantCount()).isGreaterThanOrEqualTo(1);
        assertThat(report.getTechDebtScore()).isLessThan(100);

        StaleFlagItemDto staleItem = report.getFlags().stream()
                .filter(f -> f.getKey().equals("stale-banner"))
                .findFirst().orElseThrow();

        assertThat(staleItem.getStatus()).isEqualTo("READY_FOR_RETIREMENT");
        assertThat(staleItem.isFullyRolledOut()).isTrue();
    }

    @Test
    @DisplayName("8.3 Archiving flag marks it retired and removes from active evaluations")
    void testArchiveAndUnarchiveFlag() {
        Feature flag = featureRepository.save(Feature.builder()
                .project(testProject)
                .key("temp-promo")
                .name("Temporary Promo")
                .defaultEnabled(true)
                .build());

        // Archive flag
        staleFlagService.archiveFlag(testProject.getId(), "temp-promo", principal);

        Feature reloaded = featureRepository.findByProjectIdAndKey(testProject.getId(), "temp-promo").orElseThrow();
        assertThat(reloaded.getArchivedAt()).isNotNull();

        // Active features list should now exclude it
        List<FeatureRowDto> activeList = featureService.getFeaturesForProject(testProject.getId());
        assertThat(activeList.stream().noneMatch(f -> f.getKey().equals("temp-promo"))).isTrue();

        // Unarchive flag
        staleFlagService.unarchiveFlag(testProject.getId(), "temp-promo", principal);
        Feature unarchived = featureRepository.findByProjectIdAndKey(testProject.getId(), "temp-promo").orElseThrow();
        assertThat(unarchived.getArchivedAt()).isNull();

        // Active features list includes it again
        List<FeatureRowDto> restoredList = featureService.getFeaturesForProject(testProject.getId());
        assertThat(restoredList.stream().anyMatch(f -> f.getKey().equals("temp-promo"))).isTrue();
    }

    @Test
    @DisplayName("8.3 Generate Cleanup PR generates diff preview and refactor instructions")
    void testGenerateCleanupPr() {
        Feature flag = featureRepository.save(Feature.builder()
                .project(testProject)
                .key("modern-cart")
                .name("Modern Cart Flow")
                .defaultEnabled(true)
                .build());

        FlagCleanupPrResponse resp = staleFlagService.generateCleanupPr(testProject.getId(), "modern-cart", false, principal);

        assertThat(resp).isNotNull();
        assertThat(resp.getFlagKey()).isEqualTo("modern-cart");
        assertThat(resp.getBranchName()).isEqualTo("aura/retire-flag-modern-cart");
        assertThat(resp.getDiffPreview()).contains("aura.isEnabled('modern-cart')");
        assertThat(resp.getDiffPreview()).contains("+ // Flag 'modern-cart' graduated to 100% active");
        assertThat(resp.getStatus()).isEqualTo("SIMULATED_PREVIEW");
    }
}
