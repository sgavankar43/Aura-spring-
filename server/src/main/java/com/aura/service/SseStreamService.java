package com.aura.service;

import com.aura.dto.EvaluationResponse;
import com.aura.entity.Environment;
import com.aura.entity.Feature;
import com.aura.entity.FeatureState;
import com.aura.entity.Project;
import com.aura.event.AuditLogCreatedEvent;
import com.aura.event.FlagStateChangedEvent;
import com.aura.exception.ApiException;
import com.aura.repository.EnvironmentRepository;
import com.aura.repository.FeatureRepository;
import com.aura.repository.FeatureStateRepository;
import com.aura.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseStreamService {

    private static final Logger log = LoggerFactory.getLogger(SseStreamService.class);
    private static final Long SSE_TIMEOUT = 1_800_000L; // 30 minutes

    // Channel key: "projectId:envSlug" -> list of active emitters
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> channels = new ConcurrentHashMap<>();

    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final FeatureRepository featureRepository;
    private final FeatureStateRepository featureStateRepository;
    private final TargetingEngine targetingEngine;

    public SseStreamService(
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            FeatureRepository featureRepository,
            FeatureStateRepository featureStateRepository,
            TargetingEngine targetingEngine
    ) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.featureRepository = featureRepository;
        this.featureStateRepository = featureStateRepository;
        this.targetingEngine = targetingEngine;
    }

    /**
     * Subscribe a client to real-time flag push for a specific project & environment
     */
    public SseEmitter subscribe(String apiKey, String envSlug) {
        Project project = projectRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or unrecognized Project API Key"));

        if (project.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Project has been archived");
        }

        Environment environment = environmentRepository.findByProjectIdAndSlug(project.getId(), envSlug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Environment '" + envSlug + "' not found"));

        String channelKey = project.getId().toString() + ":" + environment.getSlug();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        channels.computeIfAbsent(channelKey, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(channelKey, emitter));
        emitter.onTimeout(() -> removeEmitter(channelKey, emitter));
        emitter.onError(e -> removeEmitter(channelKey, emitter));

        // Immediately push initial flags snapshot so client has current state upon connection
        try {
            EvaluationResponse initialSnapshot = buildSnapshot(project, environment);
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(initialSnapshot));
            log.info("SSE client connected to channel {}. Active listeners: {}",
                    channelKey, channels.get(channelKey).size());
        } catch (IOException e) {
            log.warn("Failed to send initial SSE payload: {}", e.getMessage());
            removeEmitter(channelKey, emitter);
        }

        return emitter;
    }

    /**
     * Handle internal flag change events and broadcast instantly to all subscribed clients
     */
    @EventListener
    @Async
    public void onFlagStateChanged(FlagStateChangedEvent event) {
        String channelKey = event.getProjectId().toString() + ":" + event.getEnvSlug();
        List<SseEmitter> emitters = channels.get(channelKey);

        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        Project project = projectRepository.findById(event.getProjectId()).orElse(null);
        if (project == null) return;

        Environment environment = environmentRepository.findByProjectIdAndSlug(project.getId(), event.getEnvSlug()).orElse(null);
        if (environment == null) return;

        EvaluationResponse payload = buildSnapshot(project, environment);
        log.info("Broadcasting flag update for feature '{}' to {} active SSE clients on channel {}",
                event.getFeatureKey(), emitters.size(), channelKey);

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("flags_updated")
                        .data(payload));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
        }
    }

    /**
     * Broadcast live audit log creation to all connected project SSE channels
     */
    @Async
    @EventListener
    public void onAuditLogCreated(AuditLogCreatedEvent event) {
        String projectPrefix = event.getProjectId().toString() + ":";
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : channels.entrySet()) {
            if (entry.getKey().startsWith(projectPrefix)) {
                List<SseEmitter> deadEmitters = new ArrayList<>();
                for (SseEmitter emitter : entry.getValue()) {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("audit_log_created")
                                .data(event.getEntry()));
                    } catch (Exception e) {
                        deadEmitters.add(emitter);
                    }
                }
                if (!deadEmitters.isEmpty()) {
                    entry.getValue().removeAll(deadEmitters);
                }
            }
        }
    }

    /**
     * Send periodic heartbeat ping every 25 seconds to keep proxies (Render, AWS ALB, Cloudflare) from closing idle connections
     */
    @Scheduled(fixedRate = 25000)
    public void sendHeartbeat() {
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : channels.entrySet()) {
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    dead.add(emitter);
                }
            }
            if (!dead.isEmpty()) {
                entry.getValue().removeAll(dead);
            }
            if (entry.getValue().isEmpty()) {
                channels.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private void removeEmitter(String channelKey, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = channels.get(channelKey);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                channels.remove(channelKey);
            }
        }
    }

    private EvaluationResponse buildSnapshot(Project project, Environment environment) {
        List<Feature> features = featureRepository.findByProjectIdAndArchivedAtIsNull(project.getId());
        Map<String, Boolean> flags = new HashMap<>();

        for (Feature feature : features) {
            FeatureState state = featureStateRepository.findByFeatureIdAndEnvironmentId(feature.getId(), environment.getId())
                    .orElse(null);

            // Default snapshot uses baseline evaluation (100% rollout check)
            boolean enabled = targetingEngine.evaluate(feature, state, null);
            flags.put(feature.getKey(), enabled);
        }

        return EvaluationResponse.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .environment(environment.getSlug())
                .flags(flags)
                .evaluatedAt(LocalDateTime.now())
                .build();
    }
}
