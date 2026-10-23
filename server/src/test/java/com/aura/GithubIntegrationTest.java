package com.aura;

import com.aura.dto.FeatureRowDto;
import com.aura.dto.GithubStatusResponse;
import com.aura.entity.GithubIntegration;
import com.aura.entity.Project;
import com.aura.entity.User;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
}
