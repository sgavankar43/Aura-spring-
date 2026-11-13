package com.aura;

import com.aura.dto.*;
import com.aura.entity.Project;
import com.aura.entity.User;
import com.aura.entity.Webhook;
import com.aura.exception.ApiException;
import com.aura.repository.ProjectMemberRepository;
import com.aura.repository.ProjectRepository;
import com.aura.repository.UserRepository;
import com.aura.repository.WebhookRepository;
import com.aura.security.UserPrincipal;
import com.aura.service.ProjectMemberService;
import com.aura.service.WebhookDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class TeamAndWebhookDeliveryTest {

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private WebhookDispatchService webhookService;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private WebhookRepository webhookRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private Project testProject;
    private User testUser;
    private UserPrincipal testPrincipal;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findAll().stream().findFirst().orElseGet(() -> {
            User u = new User();
            u.setEmail("test-lead@aura.dev");
            u.setPassword("hashedpassword123");
            u.setName("Test Team Lead");
            u.setRole("ADMIN");
            return userRepository.save(u);
        });

        testPrincipal = UserPrincipal.create(testUser);

        testProject = Project.builder()
                .name("Team Governance Project " + UUID.randomUUID().toString().substring(0, 6))
                .apiKey("aura_live_team_" + UUID.randomUUID().toString().replace("-", ""))
                .user(testUser)
                .build();
        testProject = projectRepository.save(testProject);
    }

    @Test
    @DisplayName("Should auto-seed initial project Admin if members list is empty")
    void testAutoSeedProjectAdmin() {
        List<ProjectMemberDto> members = projectMemberService.getMembers(testProject.getId());

        assertThat(members).isNotEmpty();
        assertThat(members.get(0).getEmail()).isEqualTo(testUser.getEmail().toLowerCase());
        assertThat(members.get(0).getRole()).isEqualTo("ADMIN");
        assertThat(members.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Should invite new team member and prevent duplicate emails in project")
    void testInviteAndDuplicatePrevention() {
        InviteMemberRequest req1 = InviteMemberRequest.builder()
                .name("Sarah Engineer")
                .email("sarah@company.com")
                .role("EDITOR")
                .build();

        ProjectMemberDto invited = projectMemberService.inviteMember(testProject.getId(), req1, testPrincipal);
        assertThat(invited.getEmail()).isEqualTo("sarah@company.com");
        assertThat(invited.getRole()).isEqualTo("EDITOR");
        assertThat(invited.getStatus()).isEqualTo("PENDING_INVITE");
        assertThat(invited.getInviteToken()).isNotNull();

        // Attempt duplicate invite
        assertThatThrownBy(() -> projectMemberService.inviteMember(testProject.getId(), req1, testPrincipal))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should update member role and protect against removing last Admin")
    void testUpdateRoleAndLastAdminProtection() {
        // Ensure initial admin exists
        List<ProjectMemberDto> initial = projectMemberService.getMembers(testProject.getId());
        ProjectMemberDto adminMember = initial.get(0);

        // Attempt to demote sole admin
        assertThatThrownBy(() -> projectMemberService.updateRole(
                testProject.getId(),
                adminMember.getId(),
                new UpdateMemberRoleRequest("VIEWER"),
                testPrincipal
        )).isInstanceOf(ApiException.class)
          .hasMessageContaining("Cannot demote the only remaining Admin");

        // Attempt to delete sole admin
        assertThatThrownBy(() -> projectMemberService.removeMember(
                testProject.getId(),
                adminMember.getId(),
                testPrincipal
        )).isInstanceOf(ApiException.class)
          .hasMessageContaining("Cannot remove the only remaining Admin");

        // Invite secondary admin
        ProjectMemberDto admin2 = projectMemberService.inviteMember(
                testProject.getId(),
                new InviteMemberRequest("Alex Admin", "alex@company.com", "ADMIN"),
                testPrincipal
        );

        // Now first admin can be updated to EDITOR
        ProjectMemberDto updated = projectMemberService.updateRole(
                testProject.getId(),
                adminMember.getId(),
                new UpdateMemberRoleRequest("EDITOR"),
                testPrincipal
        );
        assertThat(updated.getRole()).isEqualTo("EDITOR");

        // Remove first member
        projectMemberService.removeMember(testProject.getId(), adminMember.getId(), testPrincipal);
        List<ProjectMemberDto> afterRemove = projectMemberService.getMembers(testProject.getId());
        assertThat(afterRemove).noneMatch(m -> m.getId().equals(adminMember.getId()));
    }

    @Test
    @DisplayName("Should record webhook deliveries and allow payload retry")
    void testWebhookDeliveryLoggingAndRetry() {
        Webhook webhook = Webhook.builder()
                .project(testProject)
                .url("https://httpbin.org/post")
                .description("Test Slack Notification Webhook")
                .events("flag.toggle")
                .enabled(true)
                .build();
        webhook = webhookRepository.save(webhook);

        // Send test ping (generates delivery entry)
        webhookService.sendTestPing(testProject.getId(), webhook.getId(), testPrincipal);

        List<WebhookDeliveryDto> deliveries = webhookService.getDeliveries(testProject.getId(), webhook.getId());
        assertThat(deliveries).isNotEmpty();

        WebhookDeliveryDto firstDelivery = deliveries.get(0);
        assertThat(firstDelivery.getEventType()).isEqualTo("test.ping");
        assertThat(firstDelivery.getPayloadJson()).contains("ping");

        // Retry the delivery
        WebhookDeliveryDto retried = webhookService.retryDelivery(
                testProject.getId(),
                webhook.getId(),
                firstDelivery.getId(),
                testPrincipal
        );

        assertThat(retried).isNotNull();
        assertThat(retried.getAttemptNumber()).isEqualTo(firstDelivery.getAttemptNumber() + 1);

        List<WebhookDeliveryDto> updatedDeliveries = webhookService.getDeliveries(testProject.getId(), webhook.getId());
        assertThat(updatedDeliveries.size()).isGreaterThanOrEqualTo(2);
    }
}
