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
     * @return A token for API access
     */
    @GetMapping("/token")
    public Map<String, Object> getToken(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        
        String token = String.format("%s|%s|%s",
                authentication.getName(),
                authorities,
                Instant.now().plusSeconds(3600).getEpochSecond()); // 1 hour expiration
        
        response.put("token", token);
        response.put("expires_in", 3600);
        response.put("token_type", "Bearer");
        
        return response;
    }
}