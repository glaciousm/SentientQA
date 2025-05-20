package com.projectoracle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a filter for querying Confluence pages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfluenceFilter {
    private String spaceKey;        // Space key (e.g., "DOCS", "DEV")
    private String title;           // Page title
    private String contentType;     // Content type (e.g., "page", "blogpost")
    private String searchQuery;     // CQL search query
    private List<String> labels;    // Labels to filter by
    private List<String> ancestorIds; // List of ancestor IDs to filter by
    private int maxResults;         // Maximum number of results to return
    private boolean includeComments; // Whether to include comments
    private boolean includeAttachments; // Whether to include attachments
    private boolean includeBody;     // Whether to include the body content
    private String contentFormat;    // Content format (e.g., "storage", "view", "export_view")
    
    /**
     * Convert this filter to a CQL query string
     * @return CQL (Confluence Query Language) query string
     */
    public String toCqlString() {
        // If custom CQL search query is provided, use it
        if (searchQuery != null && !searchQuery.isEmpty()) {
            return searchQuery;
        }
        
        StringBuilder cqlBuilder = new StringBuilder();
        
        // Add content type filter
        if (contentType != null && !contentType.isEmpty()) {
            cqlBuilder.append("type = \"").append(contentType).append("\"");
        } else {
            cqlBuilder.append("type = \"page\"");
        }
        
        // Add space filter
        if (spaceKey != null && !spaceKey.isEmpty()) {
            if (cqlBuilder.length() > 0) {
                cqlBuilder.append(" AND ");
            }
            cqlBuilder.append("space = \"").append(spaceKey).append("\"");
        }
        
        // Add title filter
        if (title != null && !title.isEmpty()) {
            if (cqlBuilder.length() > 0) {
                cqlBuilder.append(" AND ");
            }
            cqlBuilder.append("title ~ \"").append(title).append("\"");
        }
        
        // Add labels filter
        if (labels != null && !labels.isEmpty()) {
            if (cqlBuilder.length() > 0) {
                cqlBuilder.append(" AND ");
            }
            
            cqlBuilder.append("(");
            for (int i = 0; i < labels.size(); i++) {
                if (i > 0) {
                    cqlBuilder.append(" OR ");
                }
                cqlBuilder.append("label = \"").append(labels.get(i)).append("\"");
            }
            cqlBuilder.append(")");
        }
        
        // Add ancestors filter
        if (ancestorIds != null && !ancestorIds.isEmpty()) {
            if (cqlBuilder.length() > 0) {
                cqlBuilder.append(" AND ");
            }
            
            cqlBuilder.append("(");
            for (int i = 0; i < ancestorIds.size(); i++) {
                if (i > 0) {
                    cqlBuilder.append(" OR ");
                }
                cqlBuilder.append("ancestor = ").append(ancestorIds.get(i));
            }
            cqlBuilder.append(")");
        }
        
        // Default order by last updated date descending
        if (cqlBuilder.length() > 0) {
            cqlBuilder.append(" ORDER BY lastmodified DESC");
        }
        
        return cqlBuilder.toString();
    }
}