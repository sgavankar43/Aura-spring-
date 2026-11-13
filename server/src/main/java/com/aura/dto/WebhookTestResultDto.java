package com.aura.dto;

import lombok.Builder;
import lombok.Data;

@Data
public class WebhookTestResultDto {
    private boolean success;
    private int statusCode;
    private String responseBody;
    private long latencyMs;
    private String message;

    public WebhookTestResultDto() {
    }

    @Builder
    public WebhookTestResultDto(boolean success, int statusCode, String responseBody, long latencyMs, String message) {
        this.success = success;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.latencyMs = latencyMs;
        this.message = message;
    }
}
