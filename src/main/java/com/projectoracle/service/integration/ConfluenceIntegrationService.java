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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for integrating with Confluence to extract knowledge from pages,
 * technical specifications, and documentation.
 */
@Service
public class ConfluenceIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(ConfluenceIntegrationService.class);
    private static final DateTimeFormatter CONFLUENCE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static final Pattern JIRA_KEY_PATTERN = Pattern.compile("([A-Z][A-Z0-9]+-[0-9]+)");

    @Value("${integration.confluence.url:}")
    private String confluenceUrl;

    @Value("${integration.confluence.username:}")
    private String username;

    @Value("${integration.confluence.api-token:}")
    private String apiToken;

    @Value("${integration.confluence.spaces:}")
    private String spaces;
    
    @Value("${integration.confluence.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate;
    private final Map<String, KnowledgeItem> confluenceItems = new HashMap<>();
    private LocalDateTime lastSyncTime;

    @Autowired
    public ConfluenceIntegrationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Check if connection to Confluence can be established
     * 
     * @return True if connection is successful
     */
    public boolean checkConnection() {
        if (!enabled || confluenceUrl.isEmpty()) {
            logger.info("Confluence integration is disabled");
            return false;
        }

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    confluenceUrl + "/rest/api/user/current",
                    HttpMethod.GET,
                    request,
                    Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Successfully connected to Confluence as: {}", response.getBody().get("displayName"));
                return true;
            } else {
                logger.warn("Failed to connect to Confluence: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error connecting to Confluence", e);
            return false;
        }
    }

    /**
     * Synchronize with Confluence to get the latest information
     * 
     * @return The sync result
     */
    public SyncResult synchronize() {
        if (!enabled) {
            return new SyncResult(false, 0, "Confluence integration is disabled");
        }

        logger.info("Starting Confluence synchronization");
        long startTime = System.currentTimeMillis();
        
        try {
            // Parse space list
            List<String> spaceKeys = Arrays.asList(spaces.split(","));
            
            int totalPages = 0;
            int newItems = 0;
            int updatedItems = 0;
            
            // Synchronize each space
            for (String spaceKey : spaceKeys) {
                logger.info("Synchronizing Confluence space: {}", spaceKey);
                
                // Fetch content from the space
                Map<String, Object> content = getContentFromSpace(spaceKey.trim());
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) content.get("results");
                
                if (results != null) {
                    for (Map<String, Object> page : results) {
                        // Filter only pages (not blog posts or other content types)
                        String type = (String) page.get("type");
                        if ("page".equals(type)) {
                            // Get full page content
                            String pageId = (String) page.get("id");
                            Map<String, Object> fullPage = getPage(pageId);
                            
                            if (fullPage != null) {
                                KnowledgeItem item = convertToKnowledgeItem(fullPage);
                                
                                // Check if this is a new or updated item
                                if (confluenceItems.containsKey(item.getItemId())) {
                                    updatedItems++;
                                } else {
                                    newItems++;
                                }
                                
                                // Update the cache
                                confluenceItems.put(item.getItemId(), item);
                            }
                        }
                    }
                    
                    totalPages += results.size();
                }
            }
            
            // Update last sync time
            lastSyncTime = LocalDateTime.now();
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            logger.info("Confluence synchronization completed in {}ms: {} total pages, {} new, {} updated", 
                    elapsedTime, totalPages, newItems, updatedItems);
            
            return new SyncResult(true, totalPages, "Synchronization completed successfully", 
                    newItems, updatedItems, 0, elapsedTime);
            
        } catch (Exception e) {
            logger.error("Error during Confluence synchronization", e);
            return new SyncResult(false, 0, "Error: " + e.getMessage());
        }
    }

    /**
     * Query Confluence for content related to a specific context
     * 
     * @param context The context to search for
     * @return List of relevant knowledge items
     */
    public List<KnowledgeItem> queryByContext(String context) {
        if (!enabled) {
            return Collections.emptyList();
        }
        
        try {
            String cql = "type=page AND text ~ \"" + context.replace("\"", "\\\"") + "\"";
            
            // Execute the search
            Map<String, Object> searchResults = searchContent(cql);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) searchResults.get("results");
            
            if (results == null || results.isEmpty()) {
                return Collections.emptyList();
            }
            
            // Convert to knowledge items and assign relevance scores
            List<KnowledgeItem> items = new ArrayList<>();
            for (Map<String, Object> result : results) {
                // Get full page content
                String pageId = (String) result.get("id");
                Map<String, Object> page = getPage(pageId);
                
                if (page != null) {
                    KnowledgeItem item = convertToKnowledgeItem(page);
                    
                    // Calculate relevance score based on text match
                    double relevance = calculateRelevance(item, context);
                    item.setRelevanceScore(relevance);
                    
                    items.add(item);
                }
            }
            
            // Sort by relevance
            items.sort(Comparator.comparing(KnowledgeItem::getRelevanceScore).reversed());
            
            return items;
        } catch (Exception e) {
            logger.error("Error querying Confluence by context", e);
            return Collections.emptyList();
        }
    }

    /**
     * Find Confluence pages related to a specific Jira issue
     * 
     * @param jiraKey The Jira issue key
     * @return List of related Confluence pages
     */
    public List<KnowledgeItem> findRelated(String jiraKey) {
        if (!enabled) {
            return Collections.emptyList();
        }
        
        try {
            // Search for pages that mention this Jira key
            String cql = "type=page AND text ~ \"" + jiraKey + "\"";
            
            // Execute the search
            Map<String, Object> searchResults = searchContent(cql);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) searchResults.get("results");
            
            if (results == null || results.isEmpty()) {
                return Collections.emptyList();
            }
            
            // Convert to knowledge items
            List<KnowledgeItem> items = new ArrayList<>();
            for (Map<String, Object> result : results) {
                // Get full page content
                String pageId = (String) result.get("id");
                Map<String, Object> page = getPage(pageId);
                
                if (page != null) {
                    KnowledgeItem item = convertToKnowledgeItem(page);
                    items.add(item);
                }
            }
            
            return items;
        } catch (Exception e) {
            logger.error("Error finding related Confluence pages for " + jiraKey, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get content from a Confluence space
     * 
     * @param spaceKey The space key
     * @return The content results
     */
    private Map<String, Object> getContentFromSpace(String spaceKey) throws RestClientException {
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        // Build query parameters
        String url = confluenceUrl + "/rest/api/content?spaceKey=" + spaceKey + "&expand=version";
        
        // Add last updated filter if we've synced before
        if (lastSyncTime != null) {
            String lastUpdated = lastSyncTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            url += "&lastModified>=" + lastUpdated;
        }
        
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Map.class);
        
        if (response.getStatusCode().is2xxSuccessful()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = response.getBody();
            return result;
        } else {
            throw new RestClientException("Failed to get Confluence content: " + response.getStatusCode());
        }
    }

    /**
     * Get a specific Confluence page with its content
     * 
     * @param pageId The page ID
     * @return The page content
     */
    private Map<String, Object> getPage(String pageId) throws RestClientException {
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        String url = confluenceUrl + "/rest/api/content/" + pageId + "?expand=body.storage,version,metadata.labels";
        
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Map.class);
        
        if (response.getStatusCode().is2xxSuccessful()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = response.getBody();
            return result;
        } else {
            logger.warn("Failed to get Confluence page {}: {}", pageId, response.getStatusCode());
            return null;
        }
    }

    /**
     * Search Confluence content using CQL
     * 
     * @param cql The CQL query
     * @return The search results
     */
    private Map<String, Object> searchContent(String cql) throws RestClientException {
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        String url = confluenceUrl + "/rest/api/content/search?cql=" + cql + "&limit=20";
        
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Map.class);
        
        if (response.getStatusCode().is2xxSuccessful()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = response.getBody();
            return result;
        } else {
            throw new RestClientException("Failed to search Confluence: " + response.getStatusCode());
        }
    }

    /**
     * Convert a Confluence page to a KnowledgeItem
     * 
     * @param page The Confluence page
     * @return The converted knowledge item
     */
    private KnowledgeItem convertToKnowledgeItem(Map<String, Object> page) {
        String id = (String) page.get("id");
        String title = (String) page.get("title");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyObj = (Map<String, Object>) page.get("body");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> storageObj = bodyObj != null ? (Map<String, Object>) bodyObj.get("storage") : null;
        
        String content = storageObj != null ? (String) storageObj.get("value") : "";
        
        @SuppressWarnings("unchecked")
        Map<String, Object> versionObj = (Map<String, Object>) page.get("version");
        
        KnowledgeItem item = new KnowledgeItem();
        item.setSourceSystem("confluence");
        item.setItemId(id);
        item.setItemType("page");
        item.setTitle(title);
        item.setContent(content);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> links = (Map<String, Object>) page.get("_links");
        
        if (links != null && links.containsKey("webui")) {
            item.setUrl(confluenceUrl + links.get("webui"));
        } else {
            item.setUrl(confluenceUrl + "/pages/viewpage.action?pageId=" + id);
        }
        
        // Parse dates
        if (versionObj != null) {
            String whenStr = (String) versionObj.get("when");
            int versionNumber = ((Number) versionObj.get("number")).intValue();
            
            if (whenStr != null) {
                try {
                    LocalDateTime updated = LocalDateTime.parse(whenStr, CONFLUENCE_DATE_FORMAT);
                    item.setUpdatedDate(updated);
                    
                    // Estimate creation date (if version 1)
                    if (versionNumber == 1) {
                        item.setCreatedDate(updated);
                    }
                } catch (Exception e) {
                    logger.warn("Could not parse Confluence date: {}", whenStr);
                }
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> by = (Map<String, Object>) versionObj.get("by");
            
            if (by != null) {
                item.setAuthor((String) by.get("displayName"));
            }
        }
        
        // Extract labels
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) page.get("metadata");
        
        if (metadata != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> labels = (Map<String, Object>) metadata.get("labels");
            
            if (labels != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) labels.get("results");
                
                if (results != null) {
                    List<String> tagList = results.stream()
                            .map(l -> (String) l.get("name"))
                            .collect(Collectors.toList());
                    
                    item.setTags(tagList);
                }
            }
        }
        
        // Set attributes
        Map<String, String> attributes = new HashMap<>();
        
        // Extract any mentioned Jira keys
        if (content != null) {
            Matcher matcher = JIRA_KEY_PATTERN.matcher(content);
            Set<String> jiraKeys = new HashSet<>();
            
            while (matcher.find()) {
                jiraKeys.add(matcher.group(1));
            }
            
            if (!jiraKeys.isEmpty()) {
                attributes.put("relatedJiraKeys", String.join(",", jiraKeys));
            }
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
        
        // Lower weight for content matches
        if (item.getContent() != null) {
            // Count occurrences in content
            String lowerContent = item.getContent().toLowerCase();
            String lowerContext = context.toLowerCase();
            
            int count = 0;
            int index = lowerContent.indexOf(lowerContext);
            while (index != -1) {
                count++;
                index = lowerContent.indexOf(lowerContext, index + lowerContext.length());
            }
            
            // Normalize count (max out at 5 occurrences for a score of 0.3)
            score += Math.min(0.3, count * 0.06);
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
     * Create authorization headers for Confluence API calls
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