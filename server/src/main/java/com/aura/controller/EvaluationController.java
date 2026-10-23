package com.aura.controller;

import com.aura.dto.EvaluationResponse;
import com.aura.service.FeatureService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class EvaluationController {

    private final FeatureService featureService;

    public EvaluationController(FeatureService featureService) {
        this.featureService = featureService;
    }

    @GetMapping("/evaluate")
    public ResponseEntity<EvaluationResponse> evaluate(
            @RequestParam String apiKey,
            @RequestParam(defaultValue = "development") String env
    ) {
        return ResponseEntity.ok(featureService.evaluateFlags(apiKey, env));
    }
}
