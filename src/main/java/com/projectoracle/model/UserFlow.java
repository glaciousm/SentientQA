package com.projectoracle.model;

import lombok.Data;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a discovered user flow between two application states.
 * Tracks the pages, interactions, and elements involved in the flow.
 */
@Data
public class UserFlow {
    
    // Unique identifier for this flow
    private UUID id;
    
    // Source and target page identifiers
    private UUID sourcePageId;
    private UUID targetPageId;
    
    // Reference to actual pages (may be null if not loaded)
    private Page sourcePage;
    private Page targetPage;
    
    // Timestamp when this flow was discovered
    private long discoveryTimestamp;
    
    // Type of flow (navigation, state_change, form_submission, etc.)
    private String flowType;
    
    // Type of interaction that triggered this flow (click, input, select, etc.)
    private String interactionType;
    
    // Human-readable description of the interaction
    private String interactionDescription;
    
    // XPath or CSS selector for the interacted element
    private String elementSelector;
    
    // Value used in the interaction (text input, selected option, etc.)
    private String interactionValue;
    
    // Whether this flow requires authentication to execute
    private boolean requiresAuthentication;
    
    // Whether this flow involves a form submission
    private boolean isFormSubmission;
    
    // List of prerequisite flows that must be executed before this one
    private List<UUID> prerequisiteFlows = new ArrayList<>();
    
    // Whether this flow has been verified by a human
    private boolean verified;
    
    // Additional metadata about this flow
    private String notes;
    
    // Priority score for this flow (higher values are more important)
    private int priorityScore;
    
    // Success rate of this flow during test executions (0-100%)
    private int successRate = 100;
    
    // How many steps involved in this flow
    private int stepCount = 1;
    
    // Importance category (critical_path, core_function, auxiliary, etc.)
    private String importanceCategory;
    
    /**
     * Creates a new user flow with a random ID
     */
    public UserFlow() {
        this.id = UUID.randomUUID();
        this.discoveryTimestamp = System.currentTimeMillis();
    }
    
    /**
     * Calculates flow importance for testing priority
     * 
     * @return A priority score from 0-100
     */
    public int calculateImportance() {
        int baseScore = 50;
        
        // Critical paths are more important
        if ("critical_path".equals(importanceCategory)) {
            baseScore += 30;
        } else if ("core_function".equals(importanceCategory)) {
            baseScore += 20;
        }
        
        // Forms are more important to test
        if (isFormSubmission) {
            baseScore += 15;
        }
        
        // Flows with authentication are more important
        if (requiresAuthentication) {
            baseScore += 10;
        }
        
        // More complex flows (more steps) are more important
        baseScore += Math.min(10, stepCount * 2);
        
        // Flows with lower success rates are more important to test
        if (successRate < 100) {
            baseScore += (100 - successRate) / 5;
        }
        
        // Cap at 100
        return Math.min(100, baseScore);
    }
    
    /**
     * Determines if this flow is part of a critical user journey
     */
    public boolean isCriticalJourney() {
        return "critical_path".equals(importanceCategory) ||
                priorityScore >= 80;
    }
}