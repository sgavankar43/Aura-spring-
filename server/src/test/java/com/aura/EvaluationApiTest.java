package com.aura;

import com.aura.dto.EvaluationResponse;
import com.aura.entity.Environment;
import com.aura.entity.Feature;
import com.aura.entity.FeatureState;
import com.aura.entity.Project;
import com.aura.entity.User;
import com.aura.repository.EnvironmentRepository;
import com.aura.repository.FeatureRepository;
import com.aura.repository.FeatureStateRepository;
import com.aura.repository.ProjectRepository;
import com.aura.repository.UserRepository;
import com.aura.service.FeatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
public class EvaluationApiTest {

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
    private UserRepository userRepository;

    private Project testProject;
    private String testApiKey;
    private Environment devEnv;
    private Environment prodEnv;
    private Feature feature1;
    private Feature feature2;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .name("Eval Tester")
                .email("eval-" + UUID.randomUUID() + "@aura.dev")
                .password("secret123")
                .role("ADMIN")
                .build());

        testApiKey = "aura_pk_eval_" + UUID.randomUUID().toString().replace("-", "");
        testProject = projectRepository.save(Project.builder()
                .name("Eval Test Store")
                .description("Store for evaluation tests")
                .apiKey(testApiKey)
                .user(user)
                .build());

        devEnv = environmentRepository.save(Environment.builder()
                .project(testProject)
                .name("Development")
                .slug("development")
                .color("#3B82F6")
                .sortOrder(1)
                .build());

        prodEnv = environmentRepository.save(Environment.builder()
                .project(testProject)
                .name("Production")
                .slug("production")
                .color("#10B981")
                .sortOrder(2)
                .build());

        feature1 = featureRepository.save(Feature.builder()
                .project(testProject)
                .key("dark-mode")
                .name("Dark Mode")
                .defaultEnabled(false)
                .build());

        feature2 = featureRepository.save(Feature.builder()
                .project(testProject)
                .key("fast-checkout")
                .name("Fast Checkout")
                .defaultEnabled(false)
                .build());

        // dev: dark-mode=true, fast-checkout=true
        featureStateRepository.save(FeatureState.builder()
                .feature(feature1)
                .environment(devEnv)
                .enabled(true)
                .build());
        featureStateRepository.save(FeatureState.builder()
                .feature(feature2)
                .environment(devEnv)
                .enabled(true)
                .build());

        // prod: dark-mode=true, fast-checkout=false
        featureStateRepository.save(FeatureState.builder()
                .feature(feature1)
                .environment(prodEnv)
                .enabled(true)
                .build());
        featureStateRepository.save(FeatureState.builder()
                .feature(feature2)
                .environment(prodEnv)
                .enabled(false)
                .build());
    }

    @Test
    @DisplayName("Should evaluate flags correctly for development environment")
    void testEvaluateDevelopmentEnvironment() {
        EvaluationResponse response = featureService.evaluateFlags(testApiKey, "development");

        assertThat(response).isNotNull();
        assertThat(response.getProjectId()).isEqualTo(testProject.getId());
        assertThat(response.getProjectName()).isEqualTo("Eval Test Store");
        assertThat(response.getEnvironment()).isEqualTo("development");
        assertThat(response.getFlags()).isNotNull();
        assertThat(response.getFlags().get("dark-mode")).isTrue();
        assertThat(response.getFlags().get("fast-checkout")).isTrue();
    }

    @Test
    @DisplayName("Should evaluate flags correctly for production environment with differing state")
    void testEvaluateProductionEnvironment() {
        EvaluationResponse response = featureService.evaluateFlags(testApiKey, "production");

        assertThat(response).isNotNull();
        assertThat(response.getEnvironment()).isEqualTo("production");
        assertThat(response.getFlags().get("dark-mode")).isTrue();
        assertThat(response.getFlags().get("fast-checkout")).isFalse();
    }

    @Test
    @DisplayName("Should throw unauthorized for invalid API key")
    void testInvalidApiKey() {
        assertThrows(RuntimeException.class, () ->
                featureService.evaluateFlags("invalid_key_12345", "development")
        );
    }
}
