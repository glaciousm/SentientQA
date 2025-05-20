package com.projectoracle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Jira issue with relevant test-related information.
 * Used for knowledge integration with the test generation process.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JiraIssue {
    private String key;
    private String summary;
    private String description;
    private String status;
    private String issueType;
    private String priority;
    private List<String> labels = new ArrayList<>();
    private List<String> components = new ArrayList<>();
    private List<String> affectedVersions = new ArrayList<>();
    private List<String> fixVersions = new ArrayList<>();
    private LocalDateTime created;
    private LocalDateTime updated;
    private String reporter;
    private String assignee;
    private String resolution;
    private LocalDateTime resolutionDate;
    
    // Custom fields
    private List<String> testCases = new ArrayList<>();
    private List<String> requirements = new ArrayList<>();
    private List<String> acceptanceCriteria = new ArrayList<>();
    private List<String> testSteps = new ArrayList<>();
    private List<String> expectedResults = new ArrayList<>();
    
    // Linked issues
    private List<String> linkedIssues = new ArrayList<>();
    
    // Attachments
    private List<JiraAttachment> attachments = new ArrayList<>();
    
    // Comments
    private List<JiraComment> comments = new ArrayList<>();
    
    /**
     * Represents an attachment in a Jira issue
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JiraAttachment {
        private String id;
        private String filename;
        private String contentType;
        private LocalDateTime created;
        private String authorName;
        private String url;
        private long size;
    }
    
    /**
     * Represents a comment in a Jira issue
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JiraComment {
        private String id;
        private String body;
        private LocalDateTime created;
        private LocalDateTime updated;
        private String authorName;
    }
    
    /**
     * Get a compact summary of the issue for inclusion in test generation
     * @return String containing key information about this issue
     */
    public String getTestGenerationSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("JIRA ISSUE ").append(key).append(": ").append(this.summary).append("\n");
        summary.append("Type: ").append(issueType).append(", Status: ").append(status)
               .append(", Priority: ").append(priority).append("\n");
        
        if (!description.isEmpty()) {
            summary.append("Description: ").append(truncateText(description, 200)).append("\n");
        }
        
        if (!acceptanceCriteria.isEmpty()) {
            summary.append("Acceptance Criteria:\n");
            for (String criterion : acceptanceCriteria) {
                summary.append("- ").append(criterion).append("\n");
            }
        }
        
        if (!testSteps.isEmpty()) {
            summary.append("Test Steps:\n");
            for (String step : testSteps) {
                summary.append("- ").append(step).append("\n");
            }
        }
        
        if (!expectedResults.isEmpty()) {
            summary.append("Expected Results:\n");
            for (String result : expectedResults) {
                summary.append("- ").append(result).append("\n");
            }
        }
        
        return summary.toString();
    }
    
    /**
     * Truncate text to a specified length
     * @param text Text to truncate
     * @param maxLength Maximum length
     * @return Truncated text with ellipsis if needed
     */
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}