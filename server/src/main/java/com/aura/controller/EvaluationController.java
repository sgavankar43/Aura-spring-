package com.aura.controller;

import com.aura.dto.EvaluationContext;
import com.aura.dto.EvaluationResponse;
import com.aura.service.FeatureService;
import com.aura.service.SseStreamService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class EvaluationController {

    private final FeatureService featureService;
    private final SseStreamService sseStreamService;

    public EvaluationController(FeatureService featureService, SseStreamService sseStreamService) {
        this.featureService = featureService;
        this.sseStreamService = sseStreamService;
    }

    /**
     * Standard GET evaluation with optional userId and email query parameters
     */
    @GetMapping("/evaluate")
    public ResponseEntity<EvaluationResponse> evaluate(
            @RequestParam String apiKey,
            @RequestParam(defaultValue = "development") String env,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String email
    ) {
        EvaluationContext context = null;
        if (userId != null || email != null) {
            context = EvaluationContext.builder()
                    .userId(userId)
                    .email(email)
                    .build();
        }
        return ResponseEntity.ok(featureService.evaluateFlags(apiKey, env, context));
    }

    /**
     * Advanced POST evaluation with full EvaluationContext payload (attributes, custom targeting)
     */
    @PostMapping("/evaluate")
    public ResponseEntity<EvaluationResponse> evaluateWithContext(
            @RequestParam String apiKey,
            @RequestParam(defaultValue = "development") String env,
            @RequestBody(required = false) EvaluationContext context
    ) {
        return ResponseEntity.ok(featureService.evaluateFlags(apiKey, env, context));
    }

    /**
     * Real-time Server-Sent Events (SSE) stream for instant (<15ms) push of flag changes
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam String apiKey,
            @RequestParam(defaultValue = "development") String env
    ) {
        return sseStreamService.subscribe(apiKey, env);
    }
}
