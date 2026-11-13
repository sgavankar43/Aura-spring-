package com.aura.service;

import com.aura.dto.InviteMemberRequest;
import com.aura.dto.ProjectMemberDto;
import com.aura.dto.UpdateMemberRoleRequest;
import com.aura.entity.Project;
import com.aura.entity.ProjectMember;
import com.aura.entity.User;
import com.aura.exception.ApiException;
import com.aura.repository.ProjectMemberRepository;
import com.aura.repository.ProjectRepository;
import com.aura.repository.UserRepository;
import com.aura.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    public ProjectMemberService(
            ProjectMemberRepository projectMemberRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            PasswordEncoder passwordEncoder) {
        this.projectMemberRepository = projectMemberRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public List<ProjectMemberDto> getMembers(UUID projectId) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        List<ProjectMember> members = projectMemberRepository.findByProjectIdOrderByCreatedAtAsc(projectId);

        // Auto-seed project creator / owner if empty
        if (members.isEmpty()) {
            User owner = project.getUser();
            if (owner == null) {
                owner = userRepository.findAll().stream().findFirst().orElse(null);
            }

            if (owner != null) {
                ProjectMember initialAdmin = ProjectMember.builder()
                        .project(project)
                        .user(owner)
                        .name(owner.getName())
                        .email(owner.getEmail().toLowerCase())
                        .role("ADMIN")
                        .status("ACTIVE")
                        .build();

                initialAdmin = projectMemberRepository.save(initialAdmin);
                members = List.of(initialAdmin);
            }
        }

        return members.stream().map(this::toDto).toList();
    }

    @Transactional
    public ProjectMemberDto inviteMember(UUID projectId, InviteMemberRequest request, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        String email = request.getEmail().trim().toLowerCase();
        if (projectMemberRepository.existsByProjectIdAndEmail(projectId, email)) {
            throw new ApiException(HttpStatus.CONFLICT, "A member or invite with email '" + email + "' already exists in this project");
        }

        String role = request.getRole().trim().toUpperCase();
        if (!Set.of("ADMIN", "EDITOR", "VIEWER").contains(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid role. Must be ADMIN, EDITOR, or VIEWER");
        }

        Optional<User> existingUser = userRepository.findByEmail(email);
        String token = UUID.randomUUID().toString();
        String generatedPassword = null;
        User memberUser = existingUser.orElse(null);
        String status = existingUser.isPresent() ? "ACTIVE" : "PENDING_INVITE";

        if (Boolean.TRUE.equals(request.getActivateImmediately())) {
            if (memberUser == null) {
                generatedPassword = request.getTempPassword() != null && !request.getTempPassword().isBlank()
                        ? request.getTempPassword().trim()
                        : "AuraWelcome123!";
                User newUser = User.builder()
                        .email(email)
                        .name(request.getName().trim())
                        .password(passwordEncoder.encode(generatedPassword))
                        .role("ADMIN")
                        .build();
                memberUser = userRepository.save(newUser);
            }
            status = "ACTIVE";
            token = null;
        }

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .user(memberUser)
                .name(request.getName().trim())
                .email(email)
                .role(role)
                .status(status)
                .inviteToken(token)
                .build();

        member = projectMemberRepository.save(member);

        User actor = userRepository.findById(principal.getId()).orElse(null);
        auditLogService.logAction(
                project,
                actor,
                "PROJECT_MEMBER_INVITED",
                "ProjectMember",
                member.getId().toString(),
                Map.of("email", email, "role", role, "status", member.getStatus())
        );

        return toDto(member, generatedPassword);
    }

    @Transactional
    public ProjectMemberDto activateMember(UUID projectId, UUID memberId, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        ProjectMember member = projectMemberRepository.findByIdAndProjectId(memberId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Member not found in this project"));

        String generatedPassword = null;
        if (member.getUser() == null) {
            String email = member.getEmail().trim().toLowerCase();
            Optional<User> existingUser = userRepository.findByEmail(email);
            if (existingUser.isPresent()) {
                member.setUser(existingUser.get());
            } else {
                generatedPassword = "AuraWelcome123!";
                User newUser = User.builder()
                        .email(email)
                        .name(member.getName() != null && !member.getName().isBlank() ? member.getName() : email.split("@")[0])
                        .password(passwordEncoder.encode(generatedPassword))
                        .role("ADMIN")
                        .build();
                newUser = userRepository.save(newUser);
                member.setUser(newUser);
            }
        }

        member.setStatus("ACTIVE");
        member.setInviteToken(null);
        member = projectMemberRepository.save(member);

        User actor = userRepository.findById(principal.getId()).orElse(null);
        auditLogService.logAction(
                project,
                actor,
                "PROJECT_MEMBER_ACTIVATED",
                "ProjectMember",
                member.getId().toString(),
                Map.of("email", member.getEmail(), "role", member.getRole(), "status", "ACTIVE")
        );

        return toDto(member, generatedPassword);
    }

    @Transactional
    public ProjectMemberDto updateRole(UUID projectId, UUID memberId, UpdateMemberRoleRequest request, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        ProjectMember member = projectMemberRepository.findByIdAndProjectId(memberId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Member not found in this project"));

        String newRole = request.getRole().trim().toUpperCase();
        if (!Set.of("ADMIN", "EDITOR", "VIEWER").contains(newRole)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid role. Must be ADMIN, EDITOR, or VIEWER");
        }

        if ("ADMIN".equals(member.getRole()) && !"ADMIN".equals(newRole)) {
            long adminCount = projectMemberRepository.countByProjectIdAndRole(projectId, "ADMIN");
            if (adminCount <= 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot demote the only remaining Admin for this project");
            }
        }

        String oldRole = member.getRole();
        member.setRole(newRole);
        member = projectMemberRepository.save(member);

        User actor = userRepository.findById(principal.getId()).orElse(null);
        auditLogService.logAction(
                project,
                actor,
                "PROJECT_MEMBER_ROLE_UPDATED",
                "ProjectMember",
                member.getId().toString(),
                Map.of("email", member.getEmail(), "oldRole", oldRole, "newRole", newRole)
        );

        return toDto(member);
    }

    @Transactional
    public void removeMember(UUID projectId, UUID memberId, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        ProjectMember member = projectMemberRepository.findByIdAndProjectId(memberId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Member not found in this project"));

        if ("ADMIN".equals(member.getRole())) {
            long adminCount = projectMemberRepository.countByProjectIdAndRole(projectId, "ADMIN");
            if (adminCount <= 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot remove the only remaining Admin for this project");
            }
        }

        String email = member.getEmail();
        String role = member.getRole();
        projectMemberRepository.delete(member);

        User actor = userRepository.findById(principal.getId()).orElse(null);
        auditLogService.logAction(
                project,
                actor,
                "PROJECT_MEMBER_REMOVED",
                "ProjectMember",
                memberId.toString(),
                Map.of("email", email, "role", role)
        );
    }

    private ProjectMemberDto toDto(ProjectMember member) {
        return toDto(member, null);
    }

    private ProjectMemberDto toDto(ProjectMember member, String tempPassword) {
        return ProjectMemberDto.builder()
                .id(member.getId())
                .projectId(member.getProject().getId())
                .userId(member.getUser() != null ? member.getUser().getId() : null)
                .name(member.getName())
                .email(member.getEmail())
                .role(member.getRole())
                .status(member.getStatus())
                .inviteToken(member.getInviteToken())
                .tempPassword(tempPassword)
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }
}
