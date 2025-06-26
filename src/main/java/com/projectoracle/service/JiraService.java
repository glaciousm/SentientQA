package com.projectoracle.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectoracle.model.AtlassianCredentials;
import com.projectoracle.model.JiraFilter;
import com.projectoracle.model.JiraIssue;

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
 * Service for interacting with Jira API to fetch issue data
 */
@Service
public class JiraService {

    private static final Logger logger = LoggerFactory.getLogger(JiraService.class);
    private static final String API_PATH = "/rest/api/3";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public JiraService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Get a list of Jira issues based on provided filter and credentials
     * 
     * @param filter Filter criteria for issues
     * @param credentials Atlassian account credentials
     * @return List of JiraIssue objects
     */
    public List<JiraIssue> getIssues(JiraFilter filter, AtlassianCredentials credentials) {
        logger.info("Fetching Jira issues with filter: {}", filter.toJqlString());
        
        try {
            HttpHeaders headers = createHeaders(credentials);
            
            String apiUrl = credentials.getBaseUrl() + API_PATH + "/search";
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("jql", filter.toJqlString())
                .queryParam("maxResults", filter.getMaxResults() > 0 ? filter.getMaxResults() : 50);
                
            // Determine which fields to fetch
            List<String> fields = new ArrayList<>();
            fields.add("summary");
            fields.add("description");
            fields.add("status");
            fields.add("issuetype");
            fields.add("priority");
            fields.add("labels");
            fields.add("components");
            fields.add("versions");
            fields.add("fixVersions");
            fields.add("created");
            fields.add("updated");
            fields.add("reporter");
            fields.add("assignee");
            fields.add("resolution");
            fields.add("resolutiondate");
            
            // Add any custom fields
            if (filter.getCustomFields() != null) {
                fields.addAll(filter.getCustomFields());
            }
            
            if (filter.isIncludeComments()) {
                fields.add("comment");
            }
            
            if (filter.isIncludeAttachments()) {
                fields.add("attachment");
            }
            
            builder.queryParam("fields", String.join(",", fields));
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                builder.build().encode().toUri(),
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return parseIssuesResponse(response.getBody());
            
        } catch (Exception e) {
            logger.error("Error fetching Jira issues: {}", e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * Get a single Jira issue by key
     * 
     * @param issueKey Jira issue key (e.g., "PROJECT-123")
     * @param credentials Atlassian account credentials
     * @return JiraIssue object, or null if not found
     */
    public JiraIssue getIssue(String issueKey, AtlassianCredentials credentials) {
        logger.info("Fetching Jira issue: {}", issueKey);
        
        try {
            HttpHeaders headers = createHeaders(credentials);
            
            String apiUrl = credentials.getBaseUrl() + API_PATH + "/issue/" + issueKey;
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("fields", "summary,description,status,issuetype,priority,labels,components,versions,fixVersions,created,updated,reporter,assignee,resolution,resolutiondate,comment,attachment");
                
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                builder.build().encode().toUri(),
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return parseIssueResponse(response.getBody());
            
        } catch (Exception e) {
            logger.error("Error fetching Jira issue {}: {}", issueKey, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get all issues linked to a method or class
     * 
     * @param methodSignature Method signature to find issues for
     * @param className Class name to find issues for
     * @param credentials Atlassian account credentials
     * @return List of JiraIssue objects
     */
    public List<JiraIssue> getIssuesForMethod(String methodSignature, String className, AtlassianCredentials credentials) {
        logger.info("Fetching Jira issues for method: {}", methodSignature);
        
        // Build a JQL query to find issues related to this method
        // This is a simple approach - in a real implementation,
        // you'd want to use custom fields or a more sophisticated matching strategy
        String methodJql = "text ~ \"" + methodSignature + "\"";
        if (className != null && !className.isEmpty()) {
            methodJql += " OR text ~ \"" + className + "\"";
        }
        
        JiraFilter filter = JiraFilter.builder()
            .jql(methodJql)
            .maxResults(20)
            .includeComments(true)
            .build();
            
        return getIssues(filter, credentials);
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
     * Parse the JSON response from the Jira API into a list of JiraIssue objects
     * 
     * @param jsonResponse JSON response from Jira API
     * @return List of JiraIssue objects
     */
    private List<JiraIssue> parseIssuesResponse(String jsonResponse) {
        List<JiraIssue> issues = new ArrayList<>();
        
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode issuesNode = rootNode.get("issues");
            
            if (issuesNode != null && issuesNode.isArray()) {
                for (JsonNode issueNode : issuesNode) {
                    JiraIssue issue = parseIssueNode(issueNode);
                    if (issue != null) {
                        issues.add(issue);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error parsing Jira issues response: {}", e.getMessage(), e);
        }
        
        return issues;
    }
    
    /**
     * Parse a single Jira issue from JSON response
     * 
     * @param jsonResponse JSON response for a single issue
     * @return JiraIssue object
     */
    private JiraIssue parseIssueResponse(String jsonResponse) {
        try {
            JsonNode issueNode = objectMapper.readTree(jsonResponse);
            return parseIssueNode(issueNode);
        } catch (Exception e) {
            logger.error("Error parsing Jira issue response: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Parse a JSON node into a JiraIssue object
     * 
     * @param issueNode JSON node for a Jira issue
     * @return JiraIssue object
     */
    private JiraIssue parseIssueNode(JsonNode issueNode) {
        try {
            String key = issueNode.get("key").asText();
            JsonNode fields = issueNode.get("fields");
            
            JiraIssue issue = new JiraIssue();
            issue.setKey(key);
            
            // Parse basic fields
            if (fields.has("summary")) {
                issue.setSummary(fields.get("summary").asText(""));
            }
            
            if (fields.has("description")) {
                issue.setDescription(
                    fields.get("description") != null && !fields.get("description").isNull() ? 
                    fields.get("description").asText("") : ""
                );
            }
            
            if (fields.has("status") && fields.get("status").has("name")) {
                issue.setStatus(fields.get("status").get("name").asText());
            }
            
            if (fields.has("issuetype") && fields.get("issuetype").has("name")) {
                issue.setIssueType(fields.get("issuetype").get("name").asText());
            }
            
            if (fields.has("priority") && fields.get("priority").has("name")) {
                issue.setPriority(fields.get("priority").get("name").asText());
            }
            
            // Parse dates
            if (fields.has("created")) {
                try {
                    issue.setCreated(LocalDateTime.parse(fields.get("created").asText(), DATE_FORMATTER));
                } catch (Exception e) {
                    logger.warn("Could not parse created date for issue {}", key);
                }
            }
            
            if (fields.has("updated")) {
                try {
                    issue.setUpdated(LocalDateTime.parse(fields.get("updated").asText(), DATE_FORMATTER));
                } catch (Exception e) {
                    logger.warn("Could not parse updated date for issue {}", key);
                }
            }
            
            // Parse arrays
            if (fields.has("labels") && fields.get("labels").isArray()) {
                for (JsonNode label : fields.get("labels")) {
                    issue.getLabels().add(label.asText());
                }
            }
            
            if (fields.has("components") && fields.get("components").isArray()) {
                for (JsonNode component : fields.get("components")) {
                    if (component.has("name")) {
                        issue.getComponents().add(component.get("name").asText());
                    }
                }
            }
            
            // Parse user fields
            if (fields.has("reporter") && fields.get("reporter").has("displayName")) {
                issue.setReporter(fields.get("reporter").get("displayName").asText());
            }
            
            if (fields.has("assignee") && fields.get("assignee") != null && !fields.get("assignee").isNull() && fields.get("assignee").has("displayName")) {
                issue.setAssignee(fields.get("assignee").get("displayName").asText());
            }
            
            // Parse comments
            if (fields.has("comment") && fields.get("comment").has("comments") && fields.get("comment").get("comments").isArray()) {
                for (JsonNode commentNode : fields.get("comment").get("comments")) {
                    JiraIssue.JiraComment comment = new JiraIssue.JiraComment();
                    comment.setId(commentNode.get("id").asText());
                    
                    if (commentNode.has("body")) {
                        comment.setBody(commentNode.get("body").asText());
                    }
                    
                    if (commentNode.has("created")) {
                        try {
                            comment.setCreated(LocalDateTime.parse(commentNode.get("created").asText(), DATE_FORMATTER));
                        } catch (Exception e) {
                            logger.warn("Could not parse comment created date");
                        }
                    }
                    
                    if (commentNode.has("updated")) {
                        try {
                            comment.setUpdated(LocalDateTime.parse(commentNode.get("updated").asText(), DATE_FORMATTER));
                        } catch (Exception e) {
                            logger.warn("Could not parse comment updated date");
                        }
                    }
                    
                    if (commentNode.has("author") && commentNode.get("author").has("displayName")) {
                        comment.setAuthorName(commentNode.get("author").get("displayName").asText());
                    }
                    
                    issue.getComments().add(comment);
                }
            }
            
            // Parse attachments
            if (fields.has("attachment") && fields.get("attachment").isArray()) {
                for (JsonNode attachmentNode : fields.get("attachment")) {
                    JiraIssue.JiraAttachment attachment = new JiraIssue.JiraAttachment();
                    attachment.setId(attachmentNode.get("id").asText());
                    
                    if (attachmentNode.has("filename")) {
                        attachment.setFilename(attachmentNode.get("filename").asText());
                    }
                    
                    if (attachmentNode.has("mimeType")) {
                        attachment.setContentType(attachmentNode.get("mimeType").asText());
                    }
                    
                    if (attachmentNode.has("content")) {
                        attachment.setUrl(attachmentNode.get("content").asText());
                    }
                    
                    if (attachmentNode.has("created")) {
                        try {
                            attachment.setCreated(LocalDateTime.parse(attachmentNode.get("created").asText(), DATE_FORMATTER));
                        } catch (Exception e) {
                            logger.warn("Could not parse attachment created date");
                        }
                    }
                    
                    if (attachmentNode.has("author") && attachmentNode.get("author").has("displayName")) {
                        attachment.setAuthorName(attachmentNode.get("author").get("displayName").asText());
                    }
                    
                    if (attachmentNode.has("size")) {
                        attachment.setSize(attachmentNode.get("size").asLong());
                    }
                    
                    issue.getAttachments().add(attachment);
                }
            }
            
            // Look for custom fields that might contain test cases or requirements
            for (String fieldName : List.of("customfield_10001", "customfield_10002", "customfield_10003")) {
                if (fields.has(fieldName) && fields.get(fieldName) != null && !fields.get(fieldName).isNull()) {
                    // Try to extract test steps and requirements from custom fields
                    // This is a simplistic approach - real implementation would depend on your Jira configuration
                    String fieldValue = fields.get(fieldName).asText("");
                    if (fieldValue.contains("Step:") || fieldValue.contains("Test Step:")) {
                        String[] lines = fieldValue.split("\\n");
                        for (String line : lines) {
                            line = line.trim();
                            if (line.startsWith("Step:") || line.startsWith("Test Step:")) {
                                issue.getTestSteps().add(line);
                            } else if (line.startsWith("Expected:") || line.startsWith("Expected Result:")) {
                                issue.getExpectedResults().add(line);
                            } else if (line.startsWith("Acceptance Criteria:") || line.contains("must") || line.contains("should")) {
                                issue.getAcceptanceCriteria().add(line);
                            }
                        }
                    }
                }
            }
            
            return issue;
        } catch (Exception e) {
            logger.error("Error parsing Jira issue: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Create a Jira issue for a failing test case.
     *
     * @param credentials Atlassian credentials
     * @param projectKey  Jira project key
     * @param summary     Issue summary
     * @param description Issue description
     * @return created issue key or null
     */
    public String createIssue(AtlassianCredentials credentials, String projectKey,
                              String summary, String description) {
        logger.info("Creating Jira issue in project {}", projectKey);

        try {
            HttpHeaders headers = createHeaders(credentials);
            headers.add("Content-Type", "application/json");

            String apiUrl = credentials.getBaseUrl() + API_PATH + "/issue";

            String body = "{" +
                    "\"fields\": {" +
                    "\"project\": {\"key\": \"" + projectKey + "\"}," +
                    "\"summary\": \"" + summary.replace("\"", "\\\"") + "\"," +
                    "\"description\": \"" + description.replace("\"", "\\\"") + "\"," +
                    "\"issuetype\": {\"name\": \"Bug\"}" +
                    "}}";

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            JsonNode node = objectMapper.readTree(response.getBody());
            if (node.has("key")) {
                return node.get("key").asText();
            }
        } catch (Exception e) {
            logger.error("Failed to create Jira issue: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * Transition an issue to a different status by name.
     *
     * @param credentials Atlassian credentials
     * @param issueKey    issue key
     * @param statusName  target status name (e.g., "Done")
     * @return true if transition succeeded
     */
    public boolean transitionIssue(AtlassianCredentials credentials, String issueKey, String statusName) {
        try {
            HttpHeaders headers = createHeaders(credentials);
            headers.add("Content-Type", "application/json");

            String transitionsUrl = credentials.getBaseUrl() + API_PATH + "/issue/" + issueKey + "/transitions";

            ResponseEntity<String> resp = restTemplate.exchange(
                    transitionsUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            JsonNode node = objectMapper.readTree(resp.getBody());
            if (node.has("transitions")) {
                for (JsonNode t : node.get("transitions")) {
                    if (statusName.equalsIgnoreCase(t.get("name").asText())) {
                        String id = t.get("id").asText();
                        String body = "{\"transition\":{\"id\":\"" + id + "\"}}";
                        restTemplate.exchange(transitionsUrl, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to transition issue {}: {}", issueKey, e.getMessage(), e);
        }
        return false;
    }
}