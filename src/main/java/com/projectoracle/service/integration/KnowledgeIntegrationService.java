package com.projectoracle.service.integration;

import com.projectoracle.model.FeatureKnowledge;
import com.projectoracle.model.KnowledgeItem;
import java.util.Comparator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for integrating knowledge from different sources into the testing framework.
 * Handles the orchestration of data ingestion from various systems like Jira and Confluence.
 */
@Service("integrationKnowledgeService")
public class KnowledgeIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeIntegrationService.class);

    @Autowired
    private JiraIntegrationService jiraService;

    @Autowired
    private ConfluenceIntegrationService confluenceService;

    /**
     * Initialize the knowledge integration service and verify connectivity to external systems
     */
    public void initialize() {
        logger.info("Initializing Knowledge Integration Service");
        
        // Check connections to external systems
        boolean jiraConnected = jiraService.checkConnection();
        boolean confluenceConnected = confluenceService.checkConnection();
        
        if (jiraConnected) {
            logger.info("Successfully connected to Jira");
        } else {
            logger.warn("Could not connect to Jira, integration will be disabled");
        }
        
        if (confluenceConnected) {
            logger.info("Successfully connected to Confluence");
        } else {
            logger.warn("Could not connect to Confluence, integration will be disabled");
        }
    }

    /**
     * Synchronize all knowledge sources and integrate the data
     * 
     * @return Map of source names to sync results
     */
    public Map<String, SyncResult> synchronizeAllSources() {
        logger.info("Starting synchronization of all knowledge sources");
        
        // Create futures for parallel execution
        CompletableFuture<SyncResult> jiraFuture = CompletableFuture.supplyAsync(() -> jiraService.synchronize());
        CompletableFuture<SyncResult> confluenceFuture = CompletableFuture.supplyAsync(() -> confluenceService.synchronize());
        
        // Wait for all futures to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(jiraFuture, confluenceFuture);
        
        try {
            // Block until all futures are done
            allOf.join();
            
            // Collect results
            Map<String, SyncResult> results = Map.of(
                "jira", jiraFuture.get(),
                "confluence", confluenceFuture.get()
            );
            
            // Log summary
            logger.info("Synchronization complete. Jira: {} items, Confluence: {} items", 
                    results.get("jira").getTotalItems(),
                    results.get("confluence").getTotalItems());
            
            return results;
        } catch (Exception e) {
            logger.error("Error during knowledge synchronization", e);
            return Map.of(
                "jira", new SyncResult(false, 0, "Error: " + e.getMessage()),
                "confluence", new SyncResult(false, 0, "Error: " + e.getMessage())
            );
        }
    }
    
    /**
     * Query for relevant information based on a test context
     * 
     * @param context The context to search with (e.g., feature name, component, etc.)
     * @return List of relevant knowledge items
     */
    public List<KnowledgeItem> findRelevantKnowledge(String context) {
        logger.info("Searching for knowledge relevant to: {}", context);
        
        // Query both systems in parallel
        CompletableFuture<List<KnowledgeItem>> jiraFuture = 
                CompletableFuture.supplyAsync(() -> jiraService.queryByContext(context));
        
        CompletableFuture<List<KnowledgeItem>> confluenceFuture = 
                CompletableFuture.supplyAsync(() -> confluenceService.queryByContext(context));
        
        // Wait for both to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(jiraFuture, confluenceFuture);
        
        try {
            allOf.join();
            
            // Combine results
            List<KnowledgeItem> jiraItems = jiraFuture.get();
            List<KnowledgeItem> confluenceItems = confluenceFuture.get();
            
            // Merge and sort by relevance
            List<KnowledgeItem> combined = Stream.concat(jiraItems.stream(), confluenceItems.stream())
                                                 .sorted(Comparator.comparing(KnowledgeItem::getRelevanceScore).reversed())
                                                 .collect(Collectors.toList());
            
            logger.info("Found {} relevant knowledge items", combined.size());
            return combined;
        } catch (Exception e) {
            logger.error("Error searching for knowledge", e);
            return List.of();
        }
    }
    
    /**
     * Get detailed knowledge about a specific feature
     * 
     * @param featureId The feature ID to search for
     * @return The feature knowledge
     */
    public FeatureKnowledge getFeatureKnowledge(String featureId) {
        logger.info("Retrieving knowledge for feature: {}", featureId);
        
        // First try to get from Jira
        KnowledgeItem jiraItem = jiraService.getItemById(featureId);
        
        // If found in Jira, enhance with Confluence data
        if (jiraItem != null) {
            // Find related Confluence pages
            List<KnowledgeItem> relatedPages = confluenceService.findRelated(featureId);
            
            // Create enhanced feature knowledge
            FeatureKnowledge featureKnowledge = new FeatureKnowledge();
            featureKnowledge.setFeatureId(featureId);
            featureKnowledge.setTitle(jiraItem.getTitle());
            featureKnowledge.setDescription(jiraItem.getContent());
            featureKnowledge.setStatus(jiraItem.getAttributes().getOrDefault("status", "Unknown"));
            featureKnowledge.setRelatedKnowledge(relatedPages);
            
            // Extract acceptance criteria if available
            if (jiraItem.getAttributes().containsKey("acceptanceCriteria")) {
                featureKnowledge.setAcceptanceCriteria(jiraItem.getAttributes().get("acceptanceCriteria"));
            }
            
            return featureKnowledge;
        }
        
        // Not found
        logger.warn("Feature not found: {}", featureId);
        return null;
    }
}