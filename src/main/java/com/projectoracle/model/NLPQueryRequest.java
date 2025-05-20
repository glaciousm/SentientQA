package com.projectoracle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request for generating tests using natural language queries.
 * This allows users to describe test scenarios in plain English,
 * and have them converted to actual test cases.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NLPQueryRequest {
    private String query;                 // The natural language query (e.g., "Test the login method with invalid credentials")
    private String projectContext;        // General context about the project
    private String codeScope;             // What part of the code to test (method, class, package)
    private String targetClass;           // Target class name (optional)
    private String targetMethod;          // Target method name (optional)
    private String testType;              // Type of test to generate (unit, integration, api, ui)
    private List<String> includeCases;    // Specific cases to include in the tests
    private List<String> excludeCases;    // Cases to exclude from testing
    private List<KnowledgeSource> knowledgeSources = new ArrayList<>(); // External knowledge sources to consider
    private AtlassianCredentials atlassianCredentials; // Credentials for Jira/Confluence access
    private boolean useAllAvailableKnowledge; // Whether to use all available knowledge sources
}