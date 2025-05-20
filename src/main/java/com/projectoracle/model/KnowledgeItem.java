package com.projectoracle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single item of knowledge from an external system like Jira or Confluence.
 * This is a unified model that can represent different types of knowledge items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeItem {
    
    /**
     * The source system this item came from
     */
    private String sourceSystem;
    
    /**
     * Unique identifier in the source system
     */
    private String itemId;
    
    /**
     * Type of this knowledge item (e.g., "story", "bug", "page", "requirement")
     */
    private String itemType;
    
    /**
     * Title or summary of this knowledge item
     */
    private String title;
    
    /**
     * Main content of this knowledge item
     */
    private String content;
    
    /**
     * URL to view this item in the source system
     */
    private String url;
    
    /**
     * When this item was created
     */
    private LocalDateTime createdDate;
    
    /**
     * When this item was last updated
     */
    private LocalDateTime updatedDate;
    
    /**
     * Author of this item
     */
    private String author;
    
    /**
     * Tags or labels associated with this item
     */
    private List<String> tags;
    
    /**
     * Additional attributes specific to the item type
     */
    private Map<String, String> attributes = new HashMap<>();
    
    /**
     * Relevance score when returned as part of a search
     */
    private double relevanceScore;
    
    /**
     * Add an attribute to this knowledge item
     * 
     * @param key The attribute key
     * @param value The attribute value
     */
    public void addAttribute(String key, String value) {
        attributes.put(key, value);
    }
    
    /**
     * Get a specific attribute value
     * 
     * @param key The attribute key
     * @return The attribute value, or null if not present
     */
    public String getAttribute(String key) {
        return attributes.get(key);
    }
}