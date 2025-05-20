package com.projectoracle.rest;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for authentication-related endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /**
     * Generates a simple token for API access. In a production environment,
     * this would be replaced with a proper JWT implementation.
     * 
     * @param authentication The authenticated user
     * @return A token response with limited information
     */
    @GetMapping("/token")
    public Map<String, Object> getToken(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        // Copy only the necessary information from the authentication object
        String username = authentication.getName();
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        
        // Generate a secure expiration time
        long expirationTime = Instant.now().plusSeconds(3600).getEpochSecond(); // 1 hour expiration
        
        // Create token without exposing the entire Authentication object
        String token = generateToken(username, authorities, expirationTime);
        
        // Build the response with only necessary information
        response.put("token", token);
        response.put("expires_in", 3600);
        response.put("token_type", "Bearer");
        
        return response;
    }
    
    /**
     * Helper method to generate a token with user details.
     * This separates the token generation logic from the controller method.
     * 
     * @param username The username
     * @param authorities The user's authorities
     * @param expirationTime The token expiration timestamp
     * @return The generated token string
     */
    private String generateToken(String username, String authorities, long expirationTime) {
        return String.format("%s|%s|%d", username, authorities, expirationTime);
    }
}