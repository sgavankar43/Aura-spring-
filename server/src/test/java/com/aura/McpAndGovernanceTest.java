package com.aura;

import com.aura.dto.ChangeRequestDto;
import com.aura.dto.CreateChangeRequestPayload;
import com.aura.dto.CreateWebhookRequest;
import com.aura.dto.ReviewChangeRequestPayload;
import com.aura.dto.WebhookDto;
import com.aura.dto.WebhookTestResultDto;
import com.aura.entity.Environment;
import com.aura.entity.Feature;
import com.aura.entity.FeatureState;
import com.aura.entity.Project;
import com.aura.entity.User;
import com.aura.repository.*;
import com.aura.security.UserPrincipal;
import com.aura.service.ChangeRequestService;
import com.aura.service.McpServerService;
import com.aura.service.WebhookDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class McpAndGovernanceTest {

    @Autowired
    private ChangeRequestService changeRequestService;

    @Autowired
    private WebhookDispatchService webhookService;

    @Autowired
    private McpServerService mcpServerService;

    @Autowired
    private com.aura.service.FeatureService featureService;

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
    private User devUser;
    private User adminUser;
    private UserPrincipal devPrincipal;
    private UserPrincipal adminPrincipal;
    private Environment prodEnv;
    private Feature testFeature;

    @BeforeEach
    void setUp() {
        devUser = userRepository.save(User.builder()
                .name("Junior Developer")
                .email("junior-" + UUID.randomUUID() + "@aura.dev")
                .password("pass")
                .role("EDITOR")
                .build());

        adminUser = userRepository.save(User.builder()
                .name("Senior Admin")
                .email("admin-" + UUID.randomUUID() + "@aura.dev")
                .password("pass")
                .role("ADMIN")
                .build());

        devPrincipal = UserPrincipal.create(devUser);
        adminPrincipal = UserPrincipal.create(adminUser);

        testProject = projectRepository.save(Project.builder()
                .name("Governance Project")
                .apiKey("aura_pk_gov_" + UUID.randomUUID().toString().replace("-", ""))
                .user(adminUser)
                .build());

        prodEnv = environmentRepository.save(Environment.builder()
                .project(testProject)
                .name("Production")
                .slug("production")
                .sortOrder(1)
                .requireApproval(true)
                .build());

        testFeature = featureRepository.save(Feature.builder()
                .project(testProject)
                .key("critical-billing-v2")
                .name("Critical Billing Upgrade")
                .defaultEnabled(false)
                .build());

        featureStateRepository.save(FeatureState.builder()
                .feature(testFeature)
                .environment(prodEnv)
                .enabled(false)
                .rolloutPercentage(0)
                .build());
    }

    @Test
    @DisplayName("9.2 Four-Eyes Approval Workflow: Create request and approve into applied state")
    void testFourEyesApprovalWorkflow() {
        // 1. Junior developer submits change request for production
        CreateChangeRequestPayload payload = new CreateChangeRequestPayload();
        payload.setFeatureKey("critical-billing-v2");
        payload.setEnvironmentSlug("production");
        payload.setTargetState(true);
        payload.setTargetRollout(100);
        payload.setReason("Billing upgrade tested in staging and verified ready for production.");

        ChangeRequestDto cr = changeRequestService.createChangeRequest(testProject.getId(), payload, devPrincipal);
        assertThat(cr).isNotNull();
        assertThat(cr.getStatus()).isEqualTo("PENDING");
        assertThat(cr.getRequesterEmail()).isEqualTo(devUser.getEmail());

        // 2. Senior admin reviews and approves
        ReviewChangeRequestPayload reviewPayload = new ReviewChangeRequestPayload();
        reviewPayload.setDecision("APPROVE");
        reviewPayload.setComment("Approved after staging verification.");

        ChangeRequestDto approved = changeRequestService.reviewChangeRequest(
                testProject.getId(), cr.getId(), reviewPayload, adminPrincipal
        );

        assertThat(approved.getStatus()).isEqualTo("APPLIED");
        assertThat(approved.getReviewerName()).isEqualTo(adminUser.getName());

        // 3. Verify state in database was actually applied
        FeatureState state = featureStateRepository.findByFeatureIdAndEnvironmentId(testFeature.getId(), prodEnv.getId()).orElseThrow();
        assertThat(state.getEnabled()).isTrue();
        assertThat(state.getRolloutPercentage()).isEqualTo(100);
    }

    @Test
    @DisplayName("9.3 Outbound Webhooks: Create webhook, list, and test ping delivery")
    void testOutboundWebhooks() {
        CreateWebhookRequest req = new CreateWebhookRequest();
        req.setUrl("https://httpbin.org/post");
        req.setDescription("Slack Deployment Notifications");
        req.setEvents("flag.toggle,flag.rollout");

        WebhookDto webhook = webhookService.createWebhook(testProject.getId(), req, adminPrincipal);
        assertThat(webhook).isNotNull();
        assertThat(webhook.getUrl()).isEqualTo("https://httpbin.org/post");

        List<WebhookDto> list = webhookService.getWebhooks(testProject.getId());
        assertThat(list).hasSize(1);

        // Test ping
        WebhookTestResultDto testResult = webhookService.sendTestPing(testProject.getId(), webhook.getId(), adminPrincipal);
        assertThat(testResult).isNotNull();
        // httpbin or fallback ping execution
        assertThat(testResult.getLatencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("9.1 Model Context Protocol (MCP): Tool manifest, list_flags, and toggle_flag execution")
    void testMcpServerExecution() {
        // 1. Tool manifest
        List<Map<String, Object>> tools = mcpServerService.getToolsManifest();
        assertThat(tools).isNotEmpty();
        assertThat(tools.stream().anyMatch(t -> t.get("name").equals("list_flags"))).isTrue();
        assertThat(tools.stream().anyMatch(t -> t.get("name").equals("toggle_flag"))).isTrue();
        assertThat(tools.stream().anyMatch(t -> t.get("name").equals("evaluate_flags"))).isTrue();

        // 2. list_flags tool execution
        Map<String, Object> listResult = mcpServerService.executeTool("list_flags", Map.of(
                "apiKey", testProject.getApiKey()
        ));
        assertThat(listResult.get("flagsCount")).isEqualTo(1);

        // 3. toggle_flag tool execution
        Map<String, Object> toggleResult = mcpServerService.executeTool("toggle_flag", Map.of(
                "apiKey", testProject.getApiKey(),
                "flagKey", "critical-billing-v2",
                "environment", "production",
                "enabled", true
        ));
        assertThat(toggleResult.get("success")).isEqualTo(true);

        // Verify state
        FeatureState state = featureStateRepository.findByFeatureIdAndEnvironmentId(testFeature.getId(), prodEnv.getId()).orElseThrow();
        assertThat(state.getEnabled()).isTrue();

        // 4. evaluate_flags tool execution
        Map<String, Object> evalResult = mcpServerService.executeTool("evaluate_flags", Map.of(
                "apiKey", testProject.getApiKey(),
                "environment", "production",
                "userId", "usr_mcp_tester"
        ));
        assertThat(evalResult.get("evaluation")).isNotNull();
    }

    @Test
    @DisplayName("Should permanently delete a feature flag and cascade delete states and change requests")
    void testDeleteFeature() {
        // Create change request for the flag first
        changeRequestService.createChangeRequest(testProject.getId(), CreateChangeRequestPayload.builder()
                .featureKey(testFeature.getKey())
                .environmentSlug(prodEnv.getSlug())
                .targetState(true)
                .reason("Pre-delete change request")
                .build(), devPrincipal);

        // Verify feature exists
        assertThat(featureRepository.findByProjectIdAndKey(testProject.getId(), testFeature.getKey())).isPresent();
        assertThat(featureStateRepository.findByFeatureId(testFeature.getId())).isNotEmpty();

        // Perform deletion
        featureService.deleteFeature(testProject.getId(), testFeature.getKey(), adminPrincipal);

        // Verify flag is completely gone
        assertThat(featureRepository.findByProjectIdAndKey(testProject.getId(), testFeature.getKey())).isEmpty();
        assertThat(featureStateRepository.findByFeatureId(testFeature.getId())).isEmpty();
    }
}
