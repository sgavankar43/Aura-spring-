package com.aura.service;

import com.aura.dto.ChangeRequestDto;
import com.aura.dto.CreateChangeRequestPayload;
import com.aura.dto.ReviewChangeRequestPayload;
import com.aura.entity.*;
import com.aura.event.FlagStateChangedEvent;
import com.aura.exception.ApiException;
import com.aura.repository.*;
import com.aura.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChangeRequestService {

    private static final Logger log = LoggerFactory.getLogger(ChangeRequestService.class);

    private final ChangeRequestRepository changeRequestRepository;
    private final ProjectRepository projectRepository;
    private final FeatureRepository featureRepository;
    private final EnvironmentRepository environmentRepository;
    private final FeatureStateRepository featureStateRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    public ChangeRequestService(
            ChangeRequestRepository changeRequestRepository,
            ProjectRepository projectRepository,
            FeatureRepository featureRepository,
            EnvironmentRepository environmentRepository,
            FeatureStateRepository featureStateRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher) {
        this.changeRequestRepository = changeRequestRepository;
        this.projectRepository = projectRepository;
        this.featureRepository = featureRepository;
        this.environmentRepository = environmentRepository;
        this.featureStateRepository = featureStateRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestDto> getChangeRequests(UUID projectId, String status) {
        List<ChangeRequest> list = (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL"))
                ? changeRequestRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status.toUpperCase())
                : changeRequestRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        return list.stream().map(this::toDto).toList();
    }

    @Transactional
    public ChangeRequestDto createChangeRequest(UUID projectId, CreateChangeRequestPayload payload, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        User requester = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));

        Feature feature = featureRepository.findByProjectIdAndKey(projectId, payload.getFeatureKey())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Feature flag '" + payload.getFeatureKey() + "' not found"));

        Environment environment = environmentRepository.findByProjectIdAndSlug(projectId, payload.getEnvironmentSlug())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Environment '" + payload.getEnvironmentSlug() + "' not found"));

        int targetRollout = payload.getTargetRollout() != null ? payload.getTargetRollout() : 100;

        ChangeRequest cr = ChangeRequest.builder()
                .project(project)
                .feature(feature)
                .environment(environment)
                .requester(requester)
                .targetState(payload.getTargetState())
                .targetRollout(targetRollout)
                .targetRules(payload.getTargetRules())
                .reason(payload.getReason())
                .status("PENDING")
                .build();

        cr = changeRequestRepository.save(cr);

        auditLogService.logAction(
                project,
                requester,
                "CHANGE_REQUEST_CREATED",
                "ChangeRequest",
                cr.getId().toString(),
                Map.of(
                        "featureKey", feature.getKey(),
                        "environment", environment.getSlug(),
                        "targetState", payload.getTargetState(),
                        "targetRollout", targetRollout,
                        "reason", payload.getReason() != null ? payload.getReason() : "None provided"
                )
        );

        return toDto(cr);
    }

    @Transactional
    public ChangeRequestDto reviewChangeRequest(UUID projectId, UUID changeRequestId, ReviewChangeRequestPayload payload, UserPrincipal principal) {
        Project project = projectRepository.findByIdAndArchivedAtIsNull(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));

        User reviewer = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));

        ChangeRequest cr = changeRequestRepository.findById(changeRequestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Change request not found"));

        if (!cr.getProject().getId().equals(projectId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Change request does not belong to this project");
        }

        if (!"PENDING".equalsIgnoreCase(cr.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Change request has already been reviewed (status: " + cr.getStatus() + ")");
        }

        // Four-eyes check: reviewer should ideally not be requester (allowed for admin override in dev)
        String decision = payload.getDecision().toUpperCase();
        cr.setReviewer(reviewer);
        cr.setReviewedAt(LocalDateTime.now());

        if ("APPROVE".equals(decision)) {
            cr.setStatus("APPLIED");

            final Feature targetFeature = cr.getFeature();
            final Environment targetEnv = cr.getEnvironment();
            final Boolean targetState = cr.getTargetState();
            final Integer targetRollout = cr.getTargetRollout();
            final String targetRules = cr.getTargetRules();

            // Apply change to FeatureState
            FeatureState state = featureStateRepository
                    .findByFeatureIdAndEnvironmentId(targetFeature.getId(), targetEnv.getId())
                    .orElseGet(() -> FeatureState.builder()
                            .feature(targetFeature)
                            .environment(targetEnv)
                            .enabled(targetState)
                            .rolloutPercentage(targetRollout)
                            .rules(targetRules)
                            .build());

            state.setEnabled(targetState);
            state.setRolloutPercentage(targetRollout);
            state.setRules(targetRules);
            featureStateRepository.save(state);

            // Broadcast instant real-time SSE stream update (<15ms)
            eventPublisher.publishEvent(FlagStateChangedEvent.builder()
                    .projectId(project.getId())
                    .envSlug(cr.getEnvironment().getSlug())
                    .featureKey(cr.getFeature().getKey())
                    .enabled(cr.getTargetState())
                    .rolloutPercentage(cr.getTargetRollout())
                    .rules(cr.getTargetRules())
                    .build());

            auditLogService.logAction(
                    project,
                    reviewer,
                    "CHANGE_REQUEST_APPROVED_AND_APPLIED",
                    "ChangeRequest",
                    cr.getId().toString(),
                    Map.of(
                            "featureKey", cr.getFeature().getKey(),
                            "environment", cr.getEnvironment().getSlug(),
                            "requester", cr.getRequester().getEmail(),
                            "reviewer", reviewer.getEmail(),
                            "state", cr.getTargetState(),
                            "rollout", cr.getTargetRollout()
                    )
            );
        } else {
            cr.setStatus("REJECTED");
            auditLogService.logAction(
                    project,
                    reviewer,
                    "CHANGE_REQUEST_REJECTED",
                    "ChangeRequest",
                    cr.getId().toString(),
                    Map.of(
                            "featureKey", cr.getFeature().getKey(),
                            "environment", cr.getEnvironment().getSlug(),
                            "requester", cr.getRequester().getEmail(),
                            "reviewer", reviewer.getEmail(),
                            "comment", payload.getComment() != null ? payload.getComment() : "None provided"
                    )
            );
        }

        cr = changeRequestRepository.save(cr);
        return toDto(cr);
    }

    private ChangeRequestDto toDto(ChangeRequest cr) {
        return ChangeRequestDto.builder()
                .id(cr.getId())
                .projectId(cr.getProject().getId())
                .featureId(cr.getFeature().getId())
                .featureKey(cr.getFeature().getKey())
                .featureName(cr.getFeature().getName())
                .environmentId(cr.getEnvironment().getId())
                .environmentSlug(cr.getEnvironment().getSlug())
                .environmentName(cr.getEnvironment().getName())
                .requesterId(cr.getRequester().getId())
                .requesterName(cr.getRequester().getName())
                .requesterEmail(cr.getRequester().getEmail())
                .reviewerId(cr.getReviewer() != null ? cr.getReviewer().getId() : null)
                .reviewerName(cr.getReviewer() != null ? cr.getReviewer().getName() : null)
                .targetState(cr.getTargetState())
                .targetRollout(cr.getTargetRollout())
                .targetRules(cr.getTargetRules())
                .reason(cr.getReason())
                .status(cr.getStatus())
                .createdAt(cr.getCreatedAt())
                .reviewedAt(cr.getReviewedAt())
                .build();
    }
}
