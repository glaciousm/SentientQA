package com.projectoracle.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectoracle.model.AtlassianCredentials;
import com.projectoracle.model.ConfluenceFilter;
import com.projectoracle.model.ConfluencePage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Service for interacting with Confluence API to fetch page data
 */
@Service
public class ConfluenceService {

    private static final Logger logger = LoggerFactory.getLogger(ConfluenceService.class);
    private static final String API_PATH = "/rest/api/content";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public ConfluenceService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Get a list of Confluence pages based on provided filter and credentials
     * 
     * @param filter Filter criteria for pages
     * @param credentials Atlassian account credentials
     * @return List of ConfluencePage objects
     */
    public List<ConfluencePage> getPages(ConfluenceFilter filter, AtlassianCredentials credentials) {
        logger.info("Fetching Confluence pages with filter: {}", filter.toCqlString());
        
        try {
            HttpHeaders headers = createHeaders(credentials);
            
            String apiUrl = credentials.getBaseUrl() + API_PATH;
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl);
            
            // Apply filter parameters
            if (filter.getContentType() != null && !filter.getContentType().isEmpty()) {
                builder.queryParam("type", filter.getContentType());
            } else {
                builder.queryParam("type", "page");
            }
            
            if (filter.getSpaceKey() != null && !filter.getSpaceKey().isEmpty()) {
                builder.queryParam("spaceKey", filter.getSpaceKey());
            }
            
            if (filter.getTitle() != null && !filter.getTitle().isEmpty()) {
                builder.queryParam("title", filter.getTitle());
            }
            
            if (filter.getSearchQuery() != null && !filter.getSearchQuery().isEmpty()) {
                builder.queryParam("cql", filter.getSearchQuery());
            } else if (filter.toCqlString() != null && !filter.toCqlString().isEmpty()) {
                builder.queryParam("cql", filter.toCqlString());
            }
            
            builder.queryParam("limit", filter.getMaxResults() > 0 ? filter.getMaxResults() : 25);
            
            // Determine which fields to fetch
            List<String> expand = new ArrayList<>();
            expand.add("version");
            expand.add("space");
            
            if (filter.isIncludeBody()) {
                if (filter.getContentFormat() != null && !filter.getContentFormat().isEmpty()) {
                    expand.add("body." + filter.getContentFormat());
                } else {
                    expand.add("body.storage");
                }
            }
            
            if (filter.isIncludeAttachments()) {
                expand.add("children.attachment");
            }
            
            if (filter.isIncludeComments()) {
                expand.add("children.comment");
            }
            
            if (!expand.isEmpty()) {
                builder.queryParam("expand", String.join(",", expand));
            }
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                builder.build().encode().toUri(),
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return parsePagesResponse(response.getBody());
            
        } catch (Exception e) {
            logger.error("Error fetching Confluence pages: {}", e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * Get a single Confluence page by ID
     * 
     * @param pageId Confluence page ID
     * @param credentials Atlassian account credentials
     * @return ConfluencePage object, or null if not found
     */
    public ConfluencePage getPage(String pageId, AtlassianCredentials credentials) {
        logger.info("Fetching Confluence page: {}", pageId);
        
        try {
            HttpHeaders headers = createHeaders(credentials);
            
            String apiUrl = credentials.getBaseUrl() + API_PATH + "/" + pageId;
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("expand", "body.storage,version,space,children.attachment,children.comment,metadata.labels");
                
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                builder.build().encode().toUri(),
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return parsePageResponse(response.getBody());
            
        } catch (Exception e) {
            logger.error("Error fetching Confluence page {}: {}", pageId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Search for pages related to a method or class
     * 
     * @param methodSignature Method signature to find pages for
     * @param className Class name to find pages for
     * @param credentials Atlassian account credentials
     * @return List of ConfluencePage objects
     */
    public List<ConfluencePage> getPagesForMethod(String methodSignature, String className, AtlassianCredentials credentials) {
        logger.info("Fetching Confluence pages for method: {}", methodSignature);
        
        // Create a CQL query to search for pages mentioning the method
        String cql = "text ~ \"" + methodSignature + "\"";
        if (className != null && !className.isEmpty()) {
            cql += " OR text ~ \"" + className + "\"";
        }
        
        ConfluenceFilter filter = ConfluenceFilter.builder()
            .searchQuery(cql)
            .contentType("page")
            .includeBody(true)
            .maxResults(10)
            .build();
            
        List<ConfluencePage> pages = getPages(filter, credentials);
        
        // Add metadata for test generation
        for (ConfluencePage page : pages) {
            // Simple heuristics to classify pages - in a real implementation,
            // this would be based on more sophisticated content analysis or specific labels
            String title = page.getTitle().toLowerCase();
            String content = page.getContent() != null ? page.getContent().toLowerCase() : "";
            
            if (title.contains("requirement") || title.contains("spec") || content.contains("requirement") || content.contains("must")) {
                page.setRequirementDoc(true);
            }
            
            if (title.contains("api") || title.contains("endpoint") || content.contains("endpoint") || content.contains("rest api")) {
                page.setAPIDoc(true);
            }
            
            if (title.contains("architecture") || title.contains("design") || content.contains("component") || content.contains("module")) {
                page.setArchitectureDoc(true);
            }
            
            if (title.contains("user") || title.contains("guide") || content.contains("user guide") || content.contains("manual")) {
                page.setUserGuide(true);
            }
            
            // Add relevant class/method to related components
            if (className != null && !className.isEmpty()) {
                page.getRelatedCodeComponents().add(className);
            }
            
            if (methodSignature != null && !methodSignature.isEmpty()) {
                page.getRelatedCodeComponents().add(methodSignature);
            }
        }
        
        return pages;
    }
    
    /**
     * Create HTTP headers with authentication for Atlassian API
     * 
     * @param credentials Atlassian account credentials
     * @return HttpHeaders with authentication
     */
    private HttpHeaders createHeaders(AtlassianCredentials credentials) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Accept", "application/json");
        
        switch (credentials.getAuthType()) {
            case BASIC_AUTH:
                String auth = credentials.getUsername() + ":" + credentials.getApiToken();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                headers.add("Authorization", "Basic " + encodedAuth);
                break;
                
            case BEARER_TOKEN:
                headers.add("Authorization", "Bearer " + credentials.getBearerToken());
                break;
                
            case PAT:
                headers.add("Authorization", "Bearer " + credentials.getApiToken());
                break;
        }
        
        return headers;
    }
    
    /**
     * Parse the JSON response from the Confluence API into a list of ConfluencePage objects
     * 
     * @param jsonResponse JSON response from Confluence API
     * @return List of ConfluencePage objects
     */
    private List<ConfluencePage> parsePagesResponse(String jsonResponse) {
        List<ConfluencePage> pages = new ArrayList<>();
        
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode resultsNode = rootNode.get("results");
            
            if (resultsNode != null && resultsNode.isArray()) {
                for (JsonNode pageNode : resultsNode) {
                    ConfluencePage page = parsePageNode(pageNode);
                    if (page != null) {
                        pages.add(page);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error parsing Confluence pages response: {}", e.getMessage(), e);
        }
        
        return pages;
    }
    
    /**
     * Parse a single Confluence page from JSON response
     * 
     * @param jsonResponse JSON response for a single page
     * @return ConfluencePage object
     */
    private ConfluencePage parsePageResponse(String jsonResponse) {
        try {
            JsonNode pageNode = objectMapper.readTree(jsonResponse);
            return parsePageNode(pageNode);
        } catch (Exception e) {
            logger.error("Error parsing Confluence page response: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Parse a JSON node into a ConfluencePage object
     * 
     * @param pageNode JSON node for a Confluence page
     * @return ConfluencePage object
     */
    private ConfluencePage parsePageNode(JsonNode pageNode) {
        try {
            ConfluencePage page = new ConfluencePage();
            
            page.setId(pageNode.get("id").asText());
            page.setTitle(pageNode.get("title").asText());
            page.setContentType(pageNode.get("type").asText());
            
            // Get web URL - construct from baseUrl and ID if not provided
            if (pageNode.has("_links") && pageNode.get("_links").has("webui")) {
                page.setWebUrl(pageNode.get("_links").get("webui").asText());
            } else {
                // This is a fallback; typically the API includes the URL
                page.setWebUrl("/pages/viewpage.action?pageId=" + page.getId());
            }
            
            // Parse space info
            if (pageNode.has("space")) {
                JsonNode spaceNode = pageNode.get("space");
                page.setSpaceKey(spaceNode.get("key").asText());
                if (spaceNode.has("name")) {
                    page.setSpaceName(spaceNode.get("name").asText());
                }
            }
            
            // Parse body content
            if (pageNode.has("body")) {
                if (pageNode.get("body").has("storage")) {
                    page.setContent(pageNode.get("body").get("storage").get("value").asText());
                    page.setContentFormat("storage");
                } else if (pageNode.get("body").has("view")) {
                    page.setContent(pageNode.get("body").get("view").get("value").asText());
                    page.setContentFormat("view");
                }
            }
            
            // Parse version info
            if (pageNode.has("version")) {
                JsonNode versionNode = pageNode.get("version");
                page.setVersion(versionNode.get("number").asInt());
                
                if (versionNode.has("when")) {
                    try {
                        page.setUpdated(LocalDateTime.parse(versionNode.get("when").asText(), DATE_FORMATTER));
                    } catch (Exception e) {
                        logger.warn("Could not parse updated date for page {}", page.getId());
                    }
                }
                
                if (versionNode.has("by") && versionNode.get("by").has("displayName")) {
                    page.setLastUpdatedBy(versionNode.get("by").get("displayName").asText());
                }
            }
            
            // Parse created info
            if (pageNode.has("history") && pageNode.get("history").has("createdDate")) {
                try {
                    page.setCreated(LocalDateTime.parse(pageNode.get("history").get("createdDate").asText(), DATE_FORMATTER));
                } catch (Exception e) {
                    logger.warn("Could not parse created date for page {}", page.getId());
                }
                
                if (pageNode.get("history").has("createdBy") && pageNode.get("history").get("createdBy").has("displayName")) {
                    page.setCreatedBy(pageNode.get("history").get("createdBy").get("displayName").asText());
                }
            }
            
            // Parse labels
            if (pageNode.has("metadata") && pageNode.get("metadata").has("labels")) {
                JsonNode labelsNode = pageNode.get("metadata").get("labels");
                if (labelsNode.has("results") && labelsNode.get("results").isArray()) {
                    for (JsonNode labelNode : labelsNode.get("results")) {
                        if (labelNode.has("name")) {
                            String labelName = labelNode.get("name").asText();
                            page.getLabels().put(labelName, labelName);
                            
                            // Use labels to identify document types
                            switch (labelName.toLowerCase()) {
                                case "requirement":
                                case "requirements":
                                case "spec":
                                    page.setRequirementDoc(true);
                                    break;
                                case "api":
                                case "api-doc":
                                case "rest-api":
                                    page.setAPIDoc(true);
                                    break;
                                case "architecture":
                                case "design":
                                    page.setArchitectureDoc(true);
                                    break;
                                case "user-guide":
                                case "manual":
                                    page.setUserGuide(true);
                                    break;
                            }
                        }
                    }
                }
            }
            
            // Parse attachments
            if (pageNode.has("children") && pageNode.get("children").has("attachment")) {
                JsonNode attachmentsNode = pageNode.get("children").get("attachment");
                if (attachmentsNode.has("results") && attachmentsNode.get("results").isArray()) {
                    for (JsonNode attachmentNode : attachmentsNode.get("results")) {
                        ConfluencePage.Attachment attachment = new ConfluencePage.Attachment();
                        
                        attachment.setId(attachmentNode.get("id").asText());
                        attachment.setTitle(attachmentNode.get("title").asText());
                        
                        if (attachmentNode.has("extensions")) {
                            JsonNode extensionsNode = attachmentNode.get("extensions");
                            attachment.setFileName(attachmentNode.get("title").asText());
                            
                            if (extensionsNode.has("mediaType")) {
                                attachment.setMediaType(extensionsNode.get("mediaType").asText());
                            }
                            
                            if (extensionsNode.has("fileSize")) {
                                attachment.setFileSize(extensionsNode.get("fileSize").asLong());
                            }
                        }
                        
                        if (attachmentNode.has("_links") && attachmentNode.get("_links").has("download")) {
                            attachment.setDownloadUrl(attachmentNode.get("_links").get("download").asText());
                        }
                        
                        page.getAttachments().add(attachment);
                    }
                }
            }
            
            // Parse comments
            if (pageNode.has("children") && pageNode.get("children").has("comment")) {
                JsonNode commentsNode = pageNode.get("children").get("comment");
                if (commentsNode.has("results") && commentsNode.get("results").isArray()) {
                    for (JsonNode commentNode : commentsNode.get("results")) {
                        ConfluencePage.Comment comment = new ConfluencePage.Comment();
                        
                        comment.setId(commentNode.get("id").asText());
                        
                        if (commentNode.has("title")) {
                            comment.setTitle(commentNode.get("title").asText());
                        }
                        
                        if (commentNode.has("body") && commentNode.get("body").has("storage")) {
                            comment.setContent(commentNode.get("body").get("storage").get("value").asText());
                        }
                        
                        if (commentNode.has("version")) {
                            JsonNode versionNode = commentNode.get("version");
                            comment.setMinor(versionNode.has("minorEdit") && versionNode.get("minorEdit").asBoolean());
                            
                            if (versionNode.has("when")) {
                                try {
                                    comment.setUpdated(LocalDateTime.parse(versionNode.get("when").asText(), DATE_FORMATTER));
                                } catch (Exception e) {
                                    logger.warn("Could not parse updated date for comment");
                                }
                            }
                            
                            if (versionNode.has("by")) {
                                if (versionNode.get("by").has("displayName")) {
                                    comment.setAuthorName(versionNode.get("by").get("displayName").asText());
                                }
                                
                                if (versionNode.get("by").has("accountId")) {
                                    comment.setAuthorKey(versionNode.get("by").get("accountId").asText());
                                }
                            }
                        }
                        
                        page.getComments().add(comment);
                    }
                }
            }
            
            return page;
        } catch (Exception e) {
            logger.error("Error parsing Confluence page: {}", e.getMessage(), e);
            return null;
        }
    }
}