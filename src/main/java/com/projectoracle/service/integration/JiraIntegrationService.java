package com.projectoracle.service.integration;

import com.projectoracle.model.KnowledgeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for integrating with Jira to extract knowledge about features,
 * bugs, requirements, and other relevant information for test generation.
 */
@Service
public class JiraIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(JiraIntegrationService.class);
    private static final DateTimeFormatter JIRA_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    @Value("${integration.jira.url:}")
    private String jiraUrl;

    @Value("${integration.jira.username:}")
    private String username;

    @Value("${integration.jira.api-token:}")
    private String apiToken;

    @Value("${integration.jira.projects:}")
    private String projects;
    
    @Value("${integration.jira.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate;
    private final Map<String, KnowledgeItem> jiraItems = new HashMap<>();
    private LocalDateTime lastSyncTime;

    @Autowired
    public JiraIntegrationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Check if connection to Jira can be established
     * 
     * @return True if connection is successful
     */
    public boolean checkConnection() {
        if (!enabled || jiraUrl.isEmpty()) {
            logger.info("Jira integration is disabled");
            return false;
        }

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    jiraUrl + "/rest/api/2/myself",
                    HttpMethod.GET,
                    request,
                    Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Successfully connected to Jira as: {}", response.getBody().get("displayName"));
                return true;
            } else {
                logger.warn("Failed to connect to Jira: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error connecting to Jira", e);
            return false;
        }
    }

    /**
     * Synchronize with Jira to get the latest information
     * 
     * @return The sync result
     */
    public SyncResult synchronize() {
        if (!enabled) {
            return new SyncResult(false, 0, "Jira integration is disabled");
        }

        logger.info("Starting Jira synchronization");
        long startTime = System.currentTimeMillis();
        
        try {
            // Parse project list
            List<String> projectKeys = Arrays.asList(projects.split(","));
            
            int totalIssues = 0;
            int newItems = 0;
            int updatedItems = 0;
            
            // Synchronize each project
            for (String projectKey : projectKeys) {
                logger.info("Synchronizing Jira project: {}", projectKey);
                
                // Build JQL query
                String jql = "project = " + projectKey.trim();
                if (lastSyncTime != null) {
                    jql += " AND updated >= '" + lastSyncTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "'";
                }
                jql += " ORDER BY updated DESC";
                
                // Fetch issues matching the query
                Map<String, Object> issues = searchIssues(jql);
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> issueList = (List<Map<String, Object>>) issues.get("issues");
                
                if (issueList != null) {
                    for (Map<String, Object> issue : issueList) {
                        KnowledgeItem item = convertToKnowledgeItem(issue);
                        
                        // Check if this is a new or updated item
                        if (jiraItems.containsKey(item.getItemId())) {
                            updatedItems++;
                        } else {
                            newItems++;
                        }
                        
                        // Update the cache
                        jiraItems.put(item.getItemId(), item);
                    }
                    
                    totalIssues += issueList.size();
                }
            }
            
            // Update last sync time
            lastSyncTime = LocalDateTime.now();
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            logger.info("Jira synchronization completed in {}ms: {} total issues, {} new, {} updated", 
                    elapsedTime, totalIssues, newItems, updatedItems);
            
            return new SyncResult(true, totalIssues, "Synchronization completed successfully", 
                    newItems, updatedItems, 0, elapsedTime);
            
        } catch (Exception e) {
            logger.error("Error during Jira synchronization", e);
            return new SyncResult(false, 0, "Error: " + e.getMessage());
        }
    }

    /**
     * Query Jira for items related to a specific context
     * 
     * @param context The context to search for
     * @return List of relevant knowledge items
     */
    public List<KnowledgeItem> queryByContext(String context) {
        if (!enabled) {
            return Collections.emptyList();
        }
        
        try {
            // Build JQL query based on context
            String jql = "text ~ \"" + context.replace("\"", "\\\"") + "\" ORDER BY created DESC";
            
            // Fetch issues matching the query
            Map<String, Object> issues = searchIssues(jql);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> issueList = (List<Map<String, Object>>) issues.get("issues");
            
            if (issueList == null || issueList.isEmpty()) {
                return Collections.emptyList();
            }
            
            // Convert to knowledge items and assign relevance scores
            List<KnowledgeItem> items = new ArrayList<>();
            for (Map<String, Object> issue : issueList) {
                KnowledgeItem item = convertToKnowledgeItem(issue);
                
                // Calculate relevance score based on text match
                double relevance = calculateRelevance(item, context);
                item.setRelevanceScore(relevance);
                
                items.add(item);
            }
            
            // Sort by relevance
            items.sort(Comparator.comparing(KnowledgeItem::getRelevanceScore).reversed());
            
            return items;
        } catch (Exception e) {
            logger.error("Error querying Jira by context", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get a specific Jira item by ID
     * 
     * @param itemId The item ID to get
     * @return The knowledge item, or null if not found
     */
    public KnowledgeItem getItemById(String itemId) {
        if (!enabled) {
            return null;
        }
        
        // Check cache first
        if (jiraItems.containsKey(itemId)) {
            return jiraItems.get(itemId);
        }
        
        try {
            // Fetch from Jira
            String url = jiraUrl + "/rest/api/2/issue/" + itemId;
            
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                KnowledgeItem item = convertToKnowledgeItem(response.getBody());
                
                // Update cache
                jiraItems.put(itemId, item);
                
                return item;
            } else {
                logger.warn("Failed to get Jira item {}: {}", itemId, response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            logger.error("Error getting Jira item " + itemId, e);
            return null;
        }
    }

    /**
     * Search for issues in Jira using JQL
     * 
     * @param jql The JQL query
     * @return The search results
     */
    private Map<String, Object> searchIssues(String jql) throws RestClientException {
        HttpHeaders headers = createAuthHeaders();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("jql", jql);
        requestBody.put("maxResults", 100);
        requestBody.put("fields", Arrays.asList(
                "summary", "description", "issuetype", "created", "updated", 
                "status", "priority", "labels", "components", "reporter",
                "customfield_10001" // Assuming this is for story points
        ));
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
                jiraUrl + "/rest/api/2/search",
                HttpMethod.POST,
                request,
                Map.class);
        
        if (response.getStatusCode().is2xxSuccessful()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = response.getBody();
            return result;
        } else {
            throw new RestClientException("Failed to search Jira: " + response.getStatusCode());
        }
    }

    /**
     * Convert a Jira issue to a KnowledgeItem
     * 
     * @param issue The Jira issue
     * @return The converted knowledge item
     */
    private KnowledgeItem convertToKnowledgeItem(Map<String, Object> issue) {
        String id = (String) issue.get("key");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) issue.get("fields");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> issueType = (Map<String, Object>) fields.get("issuetype");
        
        String summary = (String) fields.get("summary");
        String description = (String) fields.get("description");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> status = (Map<String, Object>) fields.get("status");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> priority = (Map<String, Object>) fields.get("priority");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) fields.get("components");
        
        KnowledgeItem item = new KnowledgeItem();
        item.setSourceSystem("jira");
        item.setItemId(id);
        item.setItemType(issueType != null ? (String) issueType.get("name") : "unknown");
        item.setTitle(summary);
        item.setContent(description);
        item.setUrl(jiraUrl + "/browse/" + id);
        
        // Parse dates
        String createdStr = (String) fields.get("created");
        String updatedStr = (String) fields.get("updated");
        
        if (createdStr != null) {
            try {
                item.setCreatedDate(LocalDateTime.parse(createdStr, JIRA_DATE_FORMAT));
            } catch (Exception e) {
                logger.warn("Could not parse Jira created date: {}", createdStr);
            }
        }
        
        if (updatedStr != null) {
            try {
                item.setUpdatedDate(LocalDateTime.parse(updatedStr, JIRA_DATE_FORMAT));
            } catch (Exception e) {
                logger.warn("Could not parse Jira updated date: {}", updatedStr);
            }
        }
        
        // Set author (reporter)
        @SuppressWarnings("unchecked")
        Map<String, Object> reporter = (Map<String, Object>) fields.get("reporter");
        if (reporter != null) {
            item.setAuthor((String) reporter.get("displayName"));
        }
        
        // Set tags (labels)
        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) fields.get("labels");
        if (labels != null) {
            item.setTags(new ArrayList<>(labels));
        } else {
            item.setTags(new ArrayList<>());
        }
        
        // Set attributes
        Map<String, String> attributes = new HashMap<>();
        
        // Status
        if (status != null) {
            attributes.put("status", (String) status.get("name"));
        }
        
        // Priority
        if (priority != null) {
            attributes.put("priority", (String) priority.get("name"));
        }
        
        // Components
        if (components != null && !components.isEmpty()) {
            String componentList = components.stream()
                    .map(c -> (String) c.get("name"))
                    .collect(Collectors.joining(", "));
            attributes.put("components", componentList);
        }
        
        // Story points (if available)
        Object storyPoints = fields.get("customfield_10001");
        if (storyPoints != null) {
            attributes.put("storyPoints", storyPoints.toString());
        }
        
        item.setAttributes(attributes);
        
        return item;
    }

    /**
     * Calculate relevance score for a knowledge item based on context
     * 
     * @param item The knowledge item
     * @param context The context to match against
     * @return Relevance score (0.0 to 1.0)
     */
    private double calculateRelevance(KnowledgeItem item, String context) {
        // Simple relevance scoring based on text matching
        double score = 0.0;
        
        // Higher weight for title matches
        if (item.getTitle() != null && item.getTitle().toLowerCase().contains(context.toLowerCase())) {
            score += 0.5;
        }
        
        // Lower weight for description matches
        if (item.getContent() != null && item.getContent().toLowerCase().contains(context.toLowerCase())) {
            score += 0.3;
        }
        
        // Bonus for labels/tags matches
        if (item.getTags() != null) {
            for (String tag : item.getTags()) {
                if (tag.toLowerCase().contains(context.toLowerCase()) ||
                        context.toLowerCase().contains(tag.toLowerCase())) {
                    score += 0.2;
                    break;
                }
            }
        }
        
        return Math.min(1.0, score);
    }

    /**
     * Create authorization headers for Jira API calls
     * 
     * @return The headers with authentication
     */
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        
        // Use basic auth with API token
        String auth = username + ":" + apiToken;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes());
        headers.add("Authorization", "Basic " + new String(encodedAuth));
        
        headers.add("Content-Type", "application/json");
        headers.add("Accept", "application/json");
        
        return headers;
    }
}