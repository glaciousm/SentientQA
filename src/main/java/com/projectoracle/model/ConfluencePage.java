package com.projectoracle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Represents a Confluence page with relevant test-related information.
 * Used for knowledge integration with the test generation process.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfluencePage {
    private String id;
    private String title;
    private String spaceKey;
    private String spaceName;
    private String webUrl;
    private String content;
    private String contentType; // "page", "blogpost", etc.
    private String contentFormat; // "storage", "view", "export_view", etc.
    private Map<String, String> labels = new HashMap<>();
    private LocalDateTime created;
    private LocalDateTime updated;
    private String createdBy;
    private String lastUpdatedBy;
    private int version;
    private List<Attachment> attachments = new ArrayList<>();
    private List<Comment> comments = new ArrayList<>();
    
    // Metadata specifically for test generation
    private boolean isRequirementDoc;
    private boolean isAPIDoc;
    private boolean isUserGuide;
    private boolean isArchitectureDoc;
    private List<String> relatedCodeComponents = new ArrayList<>();
    
    /**
     * Represents an attachment on a Confluence page
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        private String id;
        private String title;
        private String fileName;
        private String mediaType;
        private String downloadUrl;
        private long fileSize;
        private String comment;
        private LocalDateTime created;
        private String createdBy;
    }
    
    /**
     * Represents a comment on a Confluence page
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Comment {
        private String id;
        private String content;
        private LocalDateTime created;
        private LocalDateTime updated;
        private String authorName;
        private String authorKey;
        private String title;
        private boolean isMinor;
    }
    
    /**
     * Get a compact summary of the page for inclusion in test generation
     * @return String containing key information about this confluence page
     */
    public String getTestGenerationSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("CONFLUENCE PAGE - ").append(title).append("\n");
        summary.append("Space: ").append(spaceName).append(" (").append(spaceKey).append(")\n");
        
        if (isRequirementDoc) {
            summary.append("REQUIREMENT DOCUMENT\n");
        }
        
        if (isAPIDoc) {
            summary.append("API DOCUMENTATION\n");
        }
        
        if (isArchitectureDoc) {
            summary.append("ARCHITECTURE DOCUMENT\n");
        }
        
        // Add plain text version of content, truncated for reasonable size
        if (content != null && !content.isEmpty()) {
            String plainTextContent = stripHtml(content);
            summary.append("Content Summary: ").append(truncateText(plainTextContent, 500)).append("\n");
        }
        
        if (!relatedCodeComponents.isEmpty()) {
            summary.append("Related Components:\n");
            for (String component : relatedCodeComponents) {
                summary.append("- ").append(component).append("\n");
            }
        }
        
        summary.append("URL: ").append(webUrl).append("\n");
        
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
    
    /**
     * Very basic HTML stripping function - in a real implementation,
     * this would be replaced with a proper HTML to plain text converter
     * @param html HTML content
     * @return Plain text version of content
     */
    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        
        // This is a very simplistic approach - production code would use a proper HTML parser
        return html.replaceAll("\\<.*?\\>", "")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&quot;", "\"")
                   .replaceAll("&apos;", "'");
    }
}