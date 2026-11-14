package com.aura.service;

/**
 * Resolves effective GitHub authorization token with a priority chain:
 * 1. Project-level Integration Token (User OAuth or PAT)
 * 2. System-level Environment/YAML Token (GITHUB_TOKEN / GITHUB_PAT)
 * 3. System Properties (github.token)
 * 4. Graceful unauthenticated fallback with synthetic failover
 */
public final class GithubTokenResolver {

    private GithubTokenResolver() {}

    public static String resolveToken(String explicitToken, String systemConfiguredToken) {
        if (explicitToken != null && !explicitToken.isBlank()) {
            return explicitToken.trim();
        }
        if (systemConfiguredToken != null && !systemConfiguredToken.isBlank()) {
            return systemConfiguredToken.trim();
        }
        String envToken = System.getenv("GITHUB_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            return envToken.trim();
        }
        String envPat = System.getenv("GITHUB_PAT");
        if (envPat != null && !envPat.isBlank()) {
            return envPat.trim();
        }
        String sysProp = System.getProperty("github.token");
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }
        return null;
    }
}
