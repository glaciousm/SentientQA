package com.projectoracle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents aggregated knowledge about a specific feature,
 * combining information from multiple sources like Jira and Confluence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureKnowledge {
    
    /**
     * Feature identifier (usually the Jira issue key)
     */
    private String featureId;
    
    /**
     * Feature title
     */
    private String title;
    
    /**
     * Feature description
     */
    private String description;
    
    /**
     * Current status of the feature
     */
    private String status;
    
    /**
     * Acceptance criteria for the feature
     */
    private String acceptanceCriteria;
    
    /**
     * Priority of this feature
     */
    private String priority;
    
    /**
     * Components associated with this feature
     */
    private List<String> components = new ArrayList<>();
    
    /**
     * Estimated complexity (e.g., story points)
     */
    private String complexity;
    
    /**
     * Known limitations or constraints
     */
    private String limitations;
    
    /**
     * Related knowledge items (e.g., Confluence pages, design docs)
     */
    private List<KnowledgeItem> relatedKnowledge = new ArrayList<>();
    
    /**
     * Associated test cases (if any)
     */
    private List<String> testCaseIds = new ArrayList<>();
    
    /**
     * Additional metadata
     */
    private Map<String, String> metadata = new HashMap<>();
    
    /**
     * Add a component to this feature
     * 
     * @param component Component name
     */
    public void addComponent(String component) {
        if (!components.contains(component)) {
            components.add(component);
        }
    }
    
    /**
     * Add a related knowledge item
     * 
     * @param item The knowledge item to add
     */
    public void addRelatedKnowledge(KnowledgeItem item) {
        relatedKnowledge.add(item);
    }
    
    /**
     * Add a test case ID
     * 
     * @param testCaseId The test case ID to add
     */
    public void addTestCase(String testCaseId) {
        if (!testCaseIds.contains(testCaseId)) {
            testCaseIds.add(testCaseId);
        }
    }
    
    /**
     * Add metadata
     * 
     * @param key Metadata key
     * @param value Metadata value
     */
    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }
}