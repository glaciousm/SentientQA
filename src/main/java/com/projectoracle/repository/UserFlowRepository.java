package com.projectoracle.repository;

import com.projectoracle.model.UserFlow;
import com.projectoracle.model.Page;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.annotation.PostConstruct;

/**
 * Repository for storing and retrieving UserFlow objects.
 * Provides methods for flow analysis and persistence.
 */
@Repository
public class UserFlowRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(UserFlowRepository.class);
    
    // In-memory store of flows
    private final Map<UUID, UserFlow> flows = new ConcurrentHashMap<>();
    
    // Store flows by source page
    private final Map<UUID, List<UserFlow>> flowsBySourcePage = new ConcurrentHashMap<>();
    
    // Store flows by target page
    private final Map<UUID, List<UserFlow>> flowsByTargetPage = new ConcurrentHashMap<>();
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // Output directory for persisting flows
    private final String outputDir = "output/user-flows";
    
    @PostConstruct
    public void init() {
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        // Create output directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(outputDir));
        } catch (IOException e) {
            logger.error("Failed to create output directory for user flows", e);
        }
        
        // Load any existing flows
        loadFlows();
    }
    
    /**
     * Save a new user flow
     * 
     * @param flow The flow to save
     * @return The saved flow
     */
    public UserFlow saveFlow(UserFlow flow) {
        // Generate ID if not set
        if (flow.getId() == null) {
            flow.setId(UUID.randomUUID());
        }
        
        // Set discovery timestamp if not set
        if (flow.getDiscoveryTimestamp() == 0) {
            flow.setDiscoveryTimestamp(System.currentTimeMillis());
        }
        
        // Calculate priority score if not set
        if (flow.getPriorityScore() == 0) {
            flow.setPriorityScore(flow.calculateImportance());
        }
        
        // Store in main map
        flows.put(flow.getId(), flow);
        
        // Store in source page map
        flowsBySourcePage.computeIfAbsent(flow.getSourcePageId(), k -> new ArrayList<>()).add(flow);
        
        // Store in target page map
        flowsByTargetPage.computeIfAbsent(flow.getTargetPageId(), k -> new ArrayList<>()).add(flow);
        
        // Log creation
        logger.info("Saved user flow: {} -> {}", flow.getSourcePageId(), flow.getTargetPageId());
        
        return flow;
    }
    
    /**
     * Get a user flow by ID
     * 
     * @param id The flow ID
     * @return The user flow or null if not found
     */
    public UserFlow getFlow(UUID id) {
        return flows.get(id);
    }
    
    /**
     * Get all user flows
     * 
     * @return List of all user flows
     */
    public List<UserFlow> getAllFlows() {
        return new ArrayList<>(flows.values());
    }
    
    /**
     * Get flows originating from a specific page
     * 
     * @param pageId The source page ID
     * @return List of flows from the page
     */
    public List<UserFlow> getFlowsFromPage(UUID pageId) {
        return flowsBySourcePage.getOrDefault(pageId, new ArrayList<>());
    }
    
    /**
     * Get flows that lead to a specific page
     * 
     * @param pageId The target page ID
     * @return List of flows to the page
     */
    public List<UserFlow> getFlowsToPage(UUID pageId) {
        return flowsByTargetPage.getOrDefault(pageId, new ArrayList<>());
    }
    
    /**
     * Delete a flow by ID
     * 
     * @param id The flow ID
     * @return true if deleted, false if not found
     */
    public boolean deleteFlow(UUID id) {
        UserFlow flow = flows.remove(id);
        
        if (flow != null) {
            // Remove from source and target maps
            List<UserFlow> sourceFlows = flowsBySourcePage.get(flow.getSourcePageId());
            if (sourceFlows != null) {
                sourceFlows.removeIf(f -> f.getId().equals(id));
            }
            
            List<UserFlow> targetFlows = flowsByTargetPage.get(flow.getTargetPageId());
            if (targetFlows != null) {
                targetFlows.removeIf(f -> f.getId().equals(id));
            }
            
            // Delete persisted file
            try {
                Files.deleteIfExists(Paths.get(outputDir, id.toString() + ".json"));
            } catch (IOException e) {
                logger.error("Failed to delete flow file", e);
            }
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Find all flow paths between two pages
     * 
     * @param sourcePageId The starting page ID
     * @param targetPageId The destination page ID
     * @param maxDepth Maximum path length
     * @return List of flow paths (each path is a list of flows)
     */
    public List<List<UserFlow>> findFlowPaths(UUID sourcePageId, UUID targetPageId, int maxDepth) {
        List<List<UserFlow>> result = new ArrayList<>();
        Map<UUID, Boolean> visited = new ConcurrentHashMap<>();
        
        findPathsDFS(sourcePageId, targetPageId, new ArrayList<>(), visited, result, maxDepth);
        
        return result;
    }
    
    /**
     * Recursive DFS to find all paths
     */
    private void findPathsDFS(UUID currentPageId, UUID targetPageId, List<UserFlow> currentPath,
                             Map<UUID, Boolean> visited, List<List<UserFlow>> result, int maxDepth) {
        // Check if we've reached max depth
        if (currentPath.size() >= maxDepth) {
            return;
        }
        
        // Mark current page as visited
        visited.put(currentPageId, true);
        
        // Check if we've reached the target
        if (currentPageId.equals(targetPageId)) {
            // Add current path to result
            result.add(new ArrayList<>(currentPath));
            visited.put(currentPageId, false);
            return;
        }
        
        // Get all flows from current page
        List<UserFlow> outgoingFlows = getFlowsFromPage(currentPageId);
        
        // Explore each outgoing flow
        for (UserFlow flow : outgoingFlows) {
            UUID nextPageId = flow.getTargetPageId();
            
            // Skip if already visited in current path
            if (visited.getOrDefault(nextPageId, false)) {
                continue;
            }
            
            // Add flow to current path
            currentPath.add(flow);
            
            // Recursively explore from next page
            findPathsDFS(nextPageId, targetPageId, currentPath, visited, result, maxDepth);
            
            // Backtrack
            currentPath.remove(currentPath.size() - 1);
        }
        
        // Mark current page as unvisited for other paths
        visited.put(currentPageId, false);
    }
    
    /**
     * Find most important flows based on priority score
     * 
     * @param limit Maximum number of flows to return
     * @return List of high-priority flows
     */
    public List<UserFlow> findImportantFlows(int limit) {
        return flows.values().stream()
                .sorted((f1, f2) -> Integer.compare(f2.getPriorityScore(), f1.getPriorityScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Find all critical journeys (sequences of flows that form important user paths)
     * 
     * @return List of flow sequences
     */
    public List<List<UserFlow>> findCriticalJourneys() {
        List<List<UserFlow>> journeys = new ArrayList<>();
        
        // Start with entry points (login pages, home pages, etc.)
        List<UserFlow> entryFlows = flows.values().stream()
                .filter(flow -> flow.isFormSubmission() && flow.getInteractionType().contains("login"))
                .collect(Collectors.toList());
        
        // For each entry flow, find all paths to important destinations
        for (UserFlow entryFlow : entryFlows) {
            Map<UUID, Boolean> visited = new ConcurrentHashMap<>();
            findCriticalJourneyDFS(entryFlow.getTargetPageId(), new ArrayList<>(), visited, journeys, 10);
        }
        
        // If no login flows, try starting from other entry points
        if (journeys.isEmpty()) {
            // Find pages with no incoming flows (likely entry points)
            for (UUID pageId : flowsBySourcePage.keySet()) {
                if (!flowsByTargetPage.containsKey(pageId) || flowsByTargetPage.get(pageId).isEmpty()) {
                    Map<UUID, Boolean> visited = new ConcurrentHashMap<>();
                    findCriticalJourneyDFS(pageId, new ArrayList<>(), visited, journeys, 10);
                }
            }
        }
        
        return journeys;
    }
    
    /**
     * Recursive DFS to find critical journeys
     */
    private void findCriticalJourneyDFS(UUID currentPageId, List<UserFlow> currentPath,
                                       Map<UUID, Boolean> visited, List<List<UserFlow>> result, int maxDepth) {
        // Check if we've reached max depth
        if (currentPath.size() >= maxDepth) {
            // If path is long enough, consider it a journey
            if (currentPath.size() >= 3) {
                result.add(new ArrayList<>(currentPath));
            }
            return;
        }
        
        // Mark current page as visited
        visited.put(currentPageId, true);
        
        // Get all flows from current page
        List<UserFlow> outgoingFlows = getFlowsFromPage(currentPageId);
        
        // Sort by priority
        outgoingFlows.sort((f1, f2) -> Integer.compare(f2.getPriorityScore(), f1.getPriorityScore()));
        
        // Track if we found any important continuation
        boolean foundImportantContinuation = false;
        
        // Explore each outgoing flow
        for (UserFlow flow : outgoingFlows) {
            UUID nextPageId = flow.getTargetPageId();
            
            // Skip if already visited in current path
            if (visited.getOrDefault(nextPageId, false)) {
                continue;
            }
            
            // Prefer critical or important flows
            if (flow.isCriticalJourney() || flow.getPriorityScore() >= 70) {
                // Add flow to current path
                currentPath.add(flow);
                foundImportantContinuation = true;
                
                // Recursively explore from next page
                findCriticalJourneyDFS(nextPageId, currentPath, visited, result, maxDepth);
                
                // Backtrack
                currentPath.remove(currentPath.size() - 1);
            }
        }
        
        // If no important continuation and path is significant, save it
        if (!foundImportantContinuation && currentPath.size() >= 2) {
            result.add(new ArrayList<>(currentPath));
        }
        
        // Mark current page as unvisited for other paths
        visited.put(currentPageId, false);
    }
    
    /**
     * Persist all flows to disk
     * Scheduled to run every 5 minutes
     */
    @Scheduled(fixedRate = 300000)
    public void persistFlows() {
        logger.info("Persisting {} user flows to disk", flows.size());
        
        for (UserFlow flow : flows.values()) {
            try {
                // Write flow to JSON file
                Path filePath = Paths.get(outputDir, flow.getId().toString() + ".json");
                objectMapper.writeValue(filePath.toFile(), flow);
            } catch (IOException e) {
                logger.error("Failed to persist flow: {}", flow.getId(), e);
            }
        }
    }
    
    /**
     * Load flows from disk
     */
    private void loadFlows() {
        try {
            Path dir = Paths.get(outputDir);
            
            // Skip if directory doesn't exist
            if (!Files.exists(dir)) {
                return;
            }
            
            // Find all JSON files
            try (Stream<Path> paths = Files.list(dir)) {
                paths.filter(path -> path.toString().endsWith(".json"))
                     .forEach(path -> {
                         try {
                             // Read flow from file
                             UserFlow flow = objectMapper.readValue(path.toFile(), UserFlow.class);
                             
                             // Add to repository (but don't overwrite existing)
                             if (!flows.containsKey(flow.getId())) {
                                 saveFlow(flow);
                             }
                         } catch (IOException e) {
                             logger.error("Failed to load flow from file: {}", path, e);
                         }
                     });
            }
            
            logger.info("Loaded {} user flows from disk", flows.size());
        } catch (IOException e) {
            logger.error("Failed to load flows from disk", e);
        }
    }
    
    /**
     * Connect flows to actual Page objects
     * 
     * @param pages Map of page IDs to Page objects
     */
    public void connectFlowsToPages(Map<UUID, Page> pages) {
        for (UserFlow flow : flows.values()) {
            flow.setSourcePage(pages.get(flow.getSourcePageId()));
            flow.setTargetPage(pages.get(flow.getTargetPageId()));
        }
    }
}