package com.projectoracle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a filter for querying Jira issues
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JiraFilter {
    private String projectKey;        // Project key (e.g., "PROJ")
    private List<String> issueTypes;  // Issue types (e.g., "Bug", "Story", "Test")
    private List<String> statuses;    // Issue statuses (e.g., "Open", "In Progress", "Resolved")
    private String jql;               // Custom JQL query
    private int maxResults;           // Maximum number of results to return
    private boolean includeComments;  // Whether to include comments
    private boolean includeAttachments; // Whether to include attachments
    private boolean includeChangeHistory; // Whether to include change history
    private List<String> customFields = new ArrayList<>(); // Custom fields to include
    
    /**
     * Convert this filter to a JQL query string
     * @return JQL query string
     */
    public String toJqlString() {
        // If custom JQL is provided, use it
        if (jql != null && !jql.isEmpty()) {
            return jql;
        }
        
        StringBuilder jqlBuilder = new StringBuilder();
        
        // Add project filter
        if (projectKey != null && !projectKey.isEmpty()) {
            jqlBuilder.append("project = ").append(projectKey);
        }
        
        // Add issue types filter
        if (issueTypes != null && !issueTypes.isEmpty()) {
            if (jqlBuilder.length() > 0) {
                jqlBuilder.append(" AND ");
            }
            
            jqlBuilder.append("issuetype IN (");
            for (int i = 0; i < issueTypes.size(); i++) {
                if (i > 0) {
                    jqlBuilder.append(", ");
                }
                jqlBuilder.append("'").append(issueTypes.get(i)).append("'");
            }
            jqlBuilder.append(")");
        }
        
        // Add statuses filter
        if (statuses != null && !statuses.isEmpty()) {
            if (jqlBuilder.length() > 0) {
                jqlBuilder.append(" AND ");
            }
            
            jqlBuilder.append("status IN (");
            for (int i = 0; i < statuses.size(); i++) {
                if (i > 0) {
                    jqlBuilder.append(", ");
                }
                jqlBuilder.append("'").append(statuses.get(i)).append("'");
            }
            jqlBuilder.append(")");
        }
        
        // Default order by created date descending
        if (jqlBuilder.length() > 0) {
            jqlBuilder.append(" ORDER BY created DESC");
        }
        
        return jqlBuilder.toString();
    }
}