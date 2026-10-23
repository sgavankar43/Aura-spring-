package com.aura;

import com.aura.dto.AuditLogEntryDto;
import com.aura.entity.AuditLog;
import com.aura.entity.Project;
import com.aura.entity.User;
import com.aura.repository.AuditLogRepository;
import com.aura.repository.ProjectRepository;
import com.aura.repository.UserRepository;
import com.aura.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class AuditLogExportTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Project testProject;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .name("Audit Tester")
                .email("audittester-" + UUID.randomUUID() + "@aura.dev")
                .password("hashed_pwd")
                .role("ADMIN")
                .build());

        testProject = projectRepository.save(Project.builder()
                .name("Export Test Project")
                .description("Project for testing audit exports")
                .apiKey("aura_pk_test_" + UUID.randomUUID())
                .user(testUser)
                .build());
    }

    @Test
    @DisplayName("Verify CSV export contains header, log entries, and human-readable details")
    void testAuditLogCsvExport() {
        // Log sample actions
        auditLogService.logAction(
                testProject,
                testUser,
                "FLAG_TOGGLED",
                "FeatureState",
                "state-1",
                Map.of("featureKey", "dark-mode", "envSlug", "production", "enabled", true)
        );

        auditLogService.logAction(
                testProject,
                testUser,
                "FEATURE_CREATED",
                "Feature",
                "feat-1",
                Map.of("key", "new-checkout", "name", "New Checkout")
        );

        String csvOutput = auditLogService.exportAuditLogsToCsv(testProject.getId());

        assertThat(csvOutput).isNotNull();
        assertThat(csvOutput).contains("ID,Timestamp,Action,Entity,Entity ID,User ID,User Name,User Email,Details,Metadata");
        assertThat(csvOutput).contains("FLAG_TOGGLED");
        assertThat(csvOutput).contains("FEATURE_CREATED");
        assertThat(csvOutput).contains("Toggled dark-mode to ON in production");
        assertThat(csvOutput).contains("Audit Tester");
        assertThat(csvOutput).contains(testUser.getEmail());
    }

    @Test
    @DisplayName("Verify CSV injection neutralization")
    void testCsvInjectionNeutralization() {
        // Potential formula injection: =cmd|' /C calc'!A0
        auditLogService.logAction(
                testProject,
                testUser,
                "PROJECT_UPDATED",
                "Project",
                testProject.getId().toString(),
                Map.of("name", "=calc|' /C calc'!A0")
        );

        String csvOutput = auditLogService.exportAuditLogsToCsv(testProject.getId());
        assertThat(csvOutput).isNotNull();
        // Leading '=' must be prepended with a quote so spreadsheet applications do not execute it
        assertThat(csvOutput).doesNotContain(",=calc");
    }

    @Test
    @DisplayName("Verify unpaginated export retrieval returns all logs for project")
    void testGetAllAuditLogsForExport() {
        for (int i = 1; i <= 5; i++) {
            auditLogService.logAction(
                    testProject,
                    testUser,
                    "FLAG_TOGGLED",
                    "FeatureState",
                    "state-" + i,
                    Map.of("featureKey", "flag-" + i, "enabled", i % 2 == 0)
            );
        }

        List<AuditLogEntryDto> entries = auditLogService.getAllAuditLogsForExport(testProject.getId());
        assertThat(entries).hasSize(5);
        assertThat(entries.get(0).getUserName()).isEqualTo("Audit Tester");
        assertThat(entries.get(0).getUserEmail()).isEqualTo(testUser.getEmail());
    }
}
