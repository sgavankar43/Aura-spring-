package com.aura;

import com.aura.dto.EvaluationContext;
import com.aura.dto.EvaluationResponse;
import com.aura.dto.FlagTargetingRequest;
import com.aura.entity.*;
import com.aura.repository.*;
import com.aura.security.UserPrincipal;
import com.aura.service.FeatureService;
import com.aura.util.MurmurHash3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class TargetingAndRolloutTest {

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
    private Environment prodEnv;
    private Feature canaryFeature;
    private Feature ruleFeature;
    private UserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .name("Rollout Admin")
                .email("admin-" + UUID.randomUUID() + "@aura.dev")
                .password("secret123")
                .role("ADMIN")
                .build());

        adminPrincipal = UserPrincipal.create(user);
        testApiKey = "aura_pk_canary_" + UUID.randomUUID().toString().replace("-", "");

        testProject = projectRepository.save(Project.builder()
                .name("Canary Project")
                .apiKey(testApiKey)
                .user(user)
                .build());

        prodEnv = environmentRepository.save(Environment.builder()
                .project(testProject)
                .name("Production")
                .slug("production")
                .color("#10B981")
                .sortOrder(1)
                .build());

        canaryFeature = featureRepository.save(Feature.builder()
                .project(testProject)
                .key("canary-exp")
                .name("Canary Experiment")
                .defaultEnabled(false)
                .build());

        ruleFeature = featureRepository.save(Feature.builder()
                .project(testProject)
                .key("beta-portal")
                .name("Beta Portal")
                .defaultEnabled(false)
                .build());
    }

    @Test
    @DisplayName("MurmurHash3 bucket must be deterministic and within [0..99]")
    void testMurmurHash3Bucketing() {
        int bucket1 = MurmurHash3.getRolloutBucket("usr_alpha_123", "canary-exp");
        int bucket2 = MurmurHash3.getRolloutBucket("usr_alpha_123", "canary-exp");

        assertThat(bucket1).isEqualTo(bucket2);
        assertThat(bucket1).isBetween(0, 99);
    }

    @Test
    @DisplayName("Should evaluate percentage rollout deterministically across different users")
    void testPercentageRolloutEvaluation() {
        // Configure 50% rollout
        featureService.updateFlagTargeting(
                testProject.getId(),
                "production",
                "canary-exp",
                FlagTargetingRequest.builder()
                        .enabled(true)
                        .rolloutPercentage(50)
                        .build(),
                adminPrincipal
        );

        // Find a user who hashes < 50 and one who hashes >= 50
        String userUnder50 = null;
        String userOver50 = null;

        for (int i = 0; i < 200; i++) {
            String uid = "user_" + i;
            int bucket = MurmurHash3.getRolloutBucket(uid, "canary-exp");
            if (bucket < 50 && userUnder50 == null) {
                userUnder50 = uid;
            }
            if (bucket >= 50 && userOver50 == null) {
                userOver50 = uid;
            }
            if (userUnder50 != null && userOver50 != null) break;
        }

        assertThat(userUnder50).isNotNull();
        assertThat(userOver50).isNotNull();

        // Evaluate userUnder50 -> should be true
        EvaluationResponse respUnder = featureService.evaluateFlags(
                testApiKey,
                "production",
                EvaluationContext.builder().userId(userUnder50).build()
        );
        assertThat(respUnder.getFlags().get("canary-exp")).isTrue();

        // Evaluate userOver50 -> should be false
        EvaluationResponse respOver = featureService.evaluateFlags(
                testApiKey,
                "production",
                EvaluationContext.builder().userId(userOver50).build()
        );
        assertThat(respOver.getFlags().get("canary-exp")).isFalse();
    }

    @Test
    @DisplayName("Targeting rules should allow segment matches even when percentage rollout is 0%")
    void testTargetingRulesSegmentation() {
        // Set flag to 0% rollout, but allow anyone with email ending in @company.com
        String rulesJson = "[{\"attribute\":\"email\",\"operator\":\"endsWith\",\"value\":\"@company.com\"}]";

        featureService.updateFlagTargeting(
                testProject.getId(),
                "production",
                "beta-portal",
                FlagTargetingRequest.builder()
                        .enabled(true)
                        .rolloutPercentage(0)
                        .rules(rulesJson)
                        .build(),
                adminPrincipal
        );

        // Employee matching rule
        EvaluationResponse respInternal = featureService.evaluateFlags(
                testApiKey,
                "production",
                EvaluationContext.builder()
                        .userId("emp_42")
                        .email("alice@company.com")
                        .build()
        );
        assertThat(respInternal.getFlags().get("beta-portal")).isTrue();

        // External user not matching rule
        EvaluationResponse respExternal = featureService.evaluateFlags(
                testApiKey,
                "production",
                EvaluationContext.builder()
                        .userId("ext_99")
                        .email("bob@gmail.com")
                        .build()
        );
        assertThat(respExternal.getFlags().get("beta-portal")).isFalse();
    }
}
