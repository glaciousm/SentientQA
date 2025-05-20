package com.projectoracle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Credentials for authenticating with Atlassian services (Jira, Confluence)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtlassianCredentials {
    private String baseUrl;      // Base URL of the Atlassian instance (e.g., "https://your-instance.atlassian.net")
    private String username;     // Username for basic auth or account ID for OAuth
    private String apiToken;     // API token for basic auth
    private String bearerToken;  // Bearer token (used with OAuth)
    private AuthType authType;   // Type of authentication to use
    
    /**
     * Authentication types for Atlassian APIs
     */
    public enum AuthType {
        BASIC_AUTH,       // Username + API token
        BEARER_TOKEN,     // OAuth bearer token
        PAT              // Personal Access Token
    }
}