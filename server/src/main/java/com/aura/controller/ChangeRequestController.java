package com.aura.controller;

import com.aura.dto.ChangeRequestDto;
import com.aura.dto.CreateChangeRequestPayload;
import com.aura.dto.ReviewChangeRequestPayload;
import com.aura.security.UserPrincipal;
import com.aura.service.ChangeRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/change-requests")
public class ChangeRequestController {

    private final ChangeRequestService changeRequestService;

    public ChangeRequestController(ChangeRequestService changeRequestService) {
        this.changeRequestService = changeRequestService;
    }

    @GetMapping
    public ResponseEntity<List<ChangeRequestDto>> getChangeRequests(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(changeRequestService.getChangeRequests(projectId, status));
    }

    @PostMapping
    public ResponseEntity<ChangeRequestDto> createChangeRequest(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateChangeRequestPayload payload,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(changeRequestService.createChangeRequest(projectId, payload, principal));
    }

    @PostMapping("/{changeRequestId}/review")
    public ResponseEntity<ChangeRequestDto> reviewChangeRequest(
            @PathVariable UUID projectId,
            @PathVariable UUID changeRequestId,
            @Valid @RequestBody ReviewChangeRequestPayload payload,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(changeRequestService.reviewChangeRequest(projectId, changeRequestId, payload, principal));
    }
}
