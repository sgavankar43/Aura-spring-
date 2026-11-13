package com.aura.controller;

import com.aura.dto.InviteMemberRequest;
import com.aura.dto.ProjectMemberDto;
import com.aura.dto.UpdateMemberRoleRequest;
import com.aura.security.UserPrincipal;
import com.aura.service.ProjectMemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    public ProjectMemberController(ProjectMemberService projectMemberService) {
        this.projectMemberService = projectMemberService;
    }

    @GetMapping
    public ResponseEntity<List<ProjectMemberDto>> getMembers(@PathVariable UUID projectId) {
        return ResponseEntity.ok(projectMemberService.getMembers(projectId));
    }

    @PostMapping
    public ResponseEntity<ProjectMemberDto> inviteMember(
            @PathVariable UUID projectId,
            @Valid @RequestBody InviteMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectMemberService.inviteMember(projectId, request, principal));
    }

    @PatchMapping("/{memberId}/role")
    public ResponseEntity<ProjectMemberDto> updateRole(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectMemberService.updateRole(projectId, memberId, request, principal));
    }

    @PostMapping("/{memberId}/activate")
    public ResponseEntity<ProjectMemberDto> activateMember(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectMemberService.activateMember(projectId, memberId, principal));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Map<String, String>> removeMember(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        projectMemberService.removeMember(projectId, memberId, principal);
        return ResponseEntity.ok(Map.of("message", "Member removed successfully"));
    }
}
