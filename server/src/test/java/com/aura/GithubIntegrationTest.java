package com.aura;

import com.aura.dto.FeatureRequest;
import com.aura.dto.FeatureRowDto;
import com.aura.dto.GithubConnectRequest;
import com.aura.dto.GithubStatusResponse;
import com.aura.entity.GithubIntegration;
import com.aura.entity.Project;
import com.aura.entity.User;
import com.aura.exception.ApiException;
import com.aura.repository.FeatureRepository;
import com.aura.repository.GithubIntegrationRepository;
import com.aura.repository.ProjectRepository;
import com.aura.repository.UserRepository;
import com.aura.security.UserPrincipal;
import com.aura.service.FeatureService;
import com.aura.service.GithubService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class GithubIntegrationTest {

    @Autowired
    private GithubService githubService;

    @Autowired
    private FeatureService featureService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private GithubIntegrationRepository githubIntegrationRepository;

    private Project testProject;
    private User testUser;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .name("GitHub Tester")
                .email("ghtester-" + UUID.randomUUID() + "@aura.dev")
                .password("hashed_pwd")
                .role("ADMIN")
                .build());

        principal = UserPrincipal.create(testUser);

        testProject = projectRepository.save(Project.builder()
                .name("GH Integration Project")
                .description("Project for testing GitHub integration")
                .apiKey("aura_pk_test_" + UUID.randomUUID())
                .user(testUser)
                .build());
    }

    @Test
    @DisplayName("Verify getStatus returns connected=false when no repo is configured")
    void testGetStatusNotConnected() {
        GithubStatusResponse status = githubService.getStatus(testProject.getId());
        assertThat(status).isNotNull();
        assertThat(status.isConnected()).isFalse();
    }

    @Test
    @DisplayName("Verify disconnect removes integration from database")
    void testDisconnectRepository() {
        githubIntegrationRepository.save(GithubIntegration.builder()
                .project(testProject)
                .repoOwner("aura")
                .repoName("core")
                .defaultBranch("main")
                .build());

        assertThat(githubIntegrationRepository.existsByProjectId(testProject.getId())).isTrue();

        githubService.disconnectRepository(testProject.getId(), principal);

        assertThat(githubIntegrationRepository.existsByProjectId(testProject.getId())).isFalse();
        GithubStatusResponse status = githubService.getStatus(testProject.getId());
        assertThat(status.isConnected()).isFalse();
    }

    @Test
    @DisplayName("Verify feature flag can be linked and unlinked to a GitHub branch and PR")
    void testLinkFeatureToGithub() {
        // Create a feature flag
        com.aura.entity.Feature feature = featureRepository.save(com.aura.entity.Feature.builder()
                .project(testProject)
                .key("auth-refactor")
                .name("Authentication Refactor")
                .defaultEnabled(false)
                .build());

        // Link to branch and PR #101
        FeatureRowDto linkedDto = featureService.linkFeatureToGithub(
                testProject.getId(),
                feature.getId(),
                "feature/auth-refactor",
                101,
                principal
        );

        assertThat(linkedDto).isNotNull();
        assertThat(linkedDto.getGithubBranch()).isEqualTo("feature/auth-refactor");
        assertThat(linkedDto.getGithubPrNumber()).isEqualTo(101);

        // Verify persisted entity
        com.aura.entity.Feature updated = featureRepository.findById(feature.getId()).orElseThrow();
        assertThat(updated.getGithubBranch()).isEqualTo("feature/auth-refactor");
        assertThat(updated.getGithubPrNumber()).isEqualTo(101);

        // Unlink
        FeatureRowDto unlinkedDto = featureService.linkFeatureToGithub(
                testProject.getId(),
                feature.getId(),
                null,
                null,
                principal
        );

        assertThat(unlinkedDto.getGithubBranch()).isNull();
        assertThat(unlinkedDto.getGithubPrNumber()).isNull();
    }

    @Test
    @DisplayName("Verify feature flag CANNOT be linked to main or master production branches")
    void testCannotLinkToDefaultMainBranch() {
        com.aura.entity.Feature feature = featureRepository.save(com.aura.entity.Feature.builder()
                .project(testProject)
                .key("test-main-branch")
                .name("Main Branch Test")
                .defaultEnabled(false)
                .build());

        assertThatThrownBy(() -> featureService.linkFeatureToGithub(
                testProject.getId(),
                feature.getId(),
                "main",
                null,
                principal
        ))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
        .hasMessageContaining("Cannot link feature flag to default/production branch 'main'");

        assertThatThrownBy(() -> featureService.linkFeatureToGithub(
                testProject.getId(),
                feature.getId(),
                "master",
                null,
                principal
        ))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
        .hasMessageContaining("Cannot link feature flag to default/production branch 'master'");
    }

    @Test
    @DisplayName("Verify feature flag CANNOT be linked to an already occupied branch")
    void testCannotLinkToAlreadyOccupiedBranch() {
        // Flag 1 links to feature/fast-checkout-v2
        featureRepository.save(com.aura.entity.Feature.builder()
                .project(testProject)
                .key("fast-checkout")
                .name("Fast Checkout")
                .githubBranch("feature/fast-checkout-v2")
                .defaultEnabled(false)
                .build());

        // Flag 2 tries to link to feature/fast-checkout-v2
        com.aura.entity.Feature flag2 = featureRepository.save(com.aura.entity.Feature.builder()
                .project(testProject)
                .key("second-checkout")
                .name("Second Checkout")
                .defaultEnabled(false)
                .build());

        assertThatThrownBy(() -> featureService.linkFeatureToGithub(
                testProject.getId(),
                flag2.getId(),
                "feature/fast-checkout-v2",
                null,
                principal
        ))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
        .hasMessageContaining("already occupied by feature flag 'fast-checkout'");
    }

    @Test
    @DisplayName("Verify creating feature with unoccupied branch succeeds")
    void testCreateFeatureWithUnoccupiedBranch() {
        FeatureRowDto created = featureService.createFeature(
                testProject.getId(),
                FeatureRequest.builder()
                        .key("new-feature-search")
                        .name("New Feature Search")
                        .githubBranch("feature/ai-copilot-beta")
                        .defaultEnabled(false)
                        .build(),
                principal
        );

        assertThat(created).isNotNull();
        assertThat(created.getKey()).isEqualTo("new-feature-search");
        assertThat(created.getGithubBranch()).isEqualTo("feature/ai-copilot-beta");

        // Now trying to create another feature on the same branch must fail
        assertThatThrownBy(() -> featureService.createFeature(
                testProject.getId(),
                FeatureRequest.builder()
                        .key("another-feature")
                        .name("Another Feature")
                        .githubBranch("feature/ai-copilot-beta")
                        .defaultEnabled(false)
                        .build(),
                principal
        ))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
        .hasMessageContaining("already occupied by feature flag 'new-feature-search'");
    }

    @Test
    @DisplayName("Verify GitHub App authorized repositories listing includes pre-registered repositories")
    void testGithubAppInstalledRepositories() {
        List<String> installed = githubService.getInstalledRepositories();
        assertThat(installed).contains("sgavankar43/testing", "sgavankar43/Aura-spring-");
    }

    @Test
    @DisplayName("Verify connecting arbitrary public repo without GitHub App authorization fails with 403 Forbidden")
    void testConnectUnauthorizedRepoFails() {
        GithubConnectRequest request = new GithubConnectRequest();
        request.setRepository("unauthorized-user/random-public-repo-xyz-999");

        assertThatThrownBy(() -> githubService.connectRepository(testProject.getId(), request, principal))
                .isInstanceOf(ApiException.class);
    }
}
