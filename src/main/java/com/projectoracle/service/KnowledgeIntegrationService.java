package com.projectoracle.service;

import com.projectoracle.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for integrating external knowledge sources with test generation.
 * This includes integrating API documentation, code comments, external specifications,
 * project documentation, historical test data, and Atlassian (Jira/Confluence) content.
 */
@Service
public class KnowledgeIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeIntegrationService.class);
    
    @Autowired
    private TestGenerationService testGenerationService;
    
    @Autowired
    private CodeAnalysisService codeAnalysisService;
    
    @Autowired
    private JiraService jiraService;
    
    @Autowired
    private ConfluenceService confluenceService;
    
    /**
     * Integrates API documentation into test generation process
     * 
     * @param apiDocPath Path to API documentation (OpenAPI/Swagger)
     * @param methodSignature Method signature to generate tests for
     * @return Enhanced test case with API documentation knowledge
     */
    public TestCase integrateApiDocumentation(Path apiDocPath, String methodSignature) {
        logger.info("Integrating API documentation from {} for method {}", apiDocPath, methodSignature);
        
        // Extract method info from code
        MethodInfo methodInfo = codeAnalysisService.findMethodBySignature(methodSignature);
        
        // Parse API documentation and enrich method info
        // This is a placeholder for actual API doc parsing logic
        Map<String, Object> apiContextData = parseApiDocumentation(apiDocPath, methodSignature);
        methodInfo.setAdditionalContext(apiContextData);
        
        // Generate test with enriched context
        return testGenerationService.generateTestForMethod(methodInfo);
    }
    
    /**
     * Integrates project documentation into test generation
     * 
     * @param docPath Path to project documentation
     * @param methodSignature Method signature to generate tests for
     * @return Enhanced test case with project documentation knowledge
     */
    public TestCase integrateProjectDocumentation(Path docPath, String methodSignature) {
        logger.info("Integrating project documentation from {} for method {}", docPath, methodSignature);
        
        MethodInfo methodInfo = codeAnalysisService.findMethodBySignature(methodSignature);
        Map<String, Object> docContextData = parseProjectDocumentation(docPath, methodSignature);
        methodInfo.setAdditionalContext(docContextData);
        
        return testGenerationService.generateTestForMethod(methodInfo);
    }
    
    /**
     * Integrates historical test data to improve test generation
     * 
     * @param testHistoryPath Path to historical test data
     * @param methodSignature Method signature to generate tests for
     * @return Enhanced test case with historical testing knowledge
     */
    public TestCase integrateHistoricalTestData(Path testHistoryPath, String methodSignature) {
        logger.info("Integrating historical test data from {} for method {}", testHistoryPath, methodSignature);
        
        MethodInfo methodInfo = codeAnalysisService.findMethodBySignature(methodSignature);
        Map<String, Object> historyContextData = parseHistoricalTestData(testHistoryPath, methodSignature);
        methodInfo.setAdditionalContext(historyContextData);
        
        return testGenerationService.generateTestForMethod(methodInfo);
    }
    
    /**
     * Integrates Jira issue data into test generation process
     * 
     * @param methodSignature Method signature to generate tests for
     * @param projectKey Jira project key
     * @param credentials Atlassian credentials for authentication
     * @return Enhanced test case with Jira issue knowledge
     */
    public TestCase integrateJiraData(String methodSignature, String projectKey, AtlassianCredentials credentials) {
        logger.info("Integrating Jira issue data for method {}", methodSignature);
        
        MethodInfo methodInfo = codeAnalysisService.findMethodBySignature(methodSignature);
        if (methodInfo == null) {
            logger.error("Method info not found for {}", methodSignature);
            return null;
        }
        
        String className = methodInfo.getClassName();
        
        // Query Jira for issues related to this method
        List<JiraIssue> issues = jiraService.getIssuesForMethod(methodSignature, className, credentials);
        logger.info("Found {} Jira issues related to {}", issues.size(), methodSignature);
        
        // Extract context data from Jira issues
        Map<String, Object> jiraContext = extractJiraContext(issues, methodSignature);
        
        // Add to method info context
        methodInfo.setAdditionalContext(jiraContext);
        
        // Generate test
        TestCase testCase = testGenerationService.generateTestForMethod(methodInfo);
        
        // Add references to knowledge sources
        if (testCase.getKnowledgeSources() == null) {
            testCase.setKnowledgeSources(new ArrayList<>());
        }
        
        for (JiraIssue issue : issues) {
            testCase.getKnowledgeSources().add(
                new KnowledgeSource(
                    "jira", 
                    credentials.getBaseUrl() + "/browse/" + issue.getKey(),
                    "jira",
                    true
                )
            );
        }
        
        return testCase;
    }
    
    /**
     * Integrates Confluence page data into test generation process
     * 
     * @param methodSignature Method signature to generate tests for
     * @param spaceKey Confluence space key to search in
     * @param credentials Atlassian credentials for authentication
     * @return Enhanced test case with Confluence knowledge
     */
    public TestCase integrateConfluenceData(String methodSignature, String spaceKey, AtlassianCredentials credentials) {
        logger.info("Integrating Confluence data for method {}", methodSignature);
        
        MethodInfo methodInfo = codeAnalysisService.findMethodBySignature(methodSignature);
        if (methodInfo == null) {
            logger.error("Method info not found for {}", methodSignature);
            return null;
        }
        
        String className = methodInfo.getClassName();
        
        // Query Confluence for pages related to this method
        ConfluenceFilter filter = ConfluenceFilter.builder()
            .spaceKey(spaceKey)
            .includeBody(true)
            .build();
        
        List<ConfluencePage> pages = confluenceService.getPagesForMethod(methodSignature, className, credentials);
        logger.info("Found {} Confluence pages related to {}", pages.size(), methodSignature);
        
        // Extract context data from Confluence pages
        Map<String, Object> confluenceContext = extractConfluenceContext(pages, methodSignature);
        
        // Add to method info context
        methodInfo.setAdditionalContext(confluenceContext);
        
        // Generate test
        TestCase testCase = testGenerationService.generateTestForMethod(methodInfo);
        
        // Add references to knowledge sources
        if (testCase.getKnowledgeSources() == null) {
            testCase.setKnowledgeSources(new ArrayList<>());
        }
        
        for (ConfluencePage page : pages) {
            testCase.getKnowledgeSources().add(
                new KnowledgeSource(
                    "confluence", 
                    credentials.getBaseUrl() + page.getWebUrl(),
                    "confluence",
                    true
                )
            );
        }
        
        return testCase;
    }
    
    /**
     * Integrates all available knowledge sources for test generation
     * 
     * @param methodSignature Method signature to generate tests for
     * @param knowledgeSources Map of knowledge source paths by type
     * @return Enhanced test case with comprehensive knowledge integration
     */
    public TestCase integrateAllKnowledgeSources(String methodSignature, Map<String, Path> knowledgeSources) {
        logger.info("Integrating all knowledge sources for method {}", methodSignature);
        
        MethodInfo methodInfo = codeAnalysisService.findMethodBySignature(methodSignature);
        if (methodInfo == null) {
            logger.error("Method info not found for {}", methodSignature);
            return null;
        }
        
        Map<String, Object> combinedContext = new HashMap<>();
        
        // Process each knowledge source
        if (knowledgeSources.containsKey("api")) {
            combinedContext.putAll(parseApiDocumentation(knowledgeSources.get("api"), methodSignature));
        }
        
        if (knowledgeSources.containsKey("docs")) {
            combinedContext.putAll(parseProjectDocumentation(knowledgeSources.get("docs"), methodSignature));
        }
        
        if (knowledgeSources.containsKey("history")) {
            combinedContext.putAll(parseHistoricalTestData(knowledgeSources.get("history"), methodSignature));
        }
        
        // Add code comments analysis
        if (knowledgeSources.containsKey("source")) {
            combinedContext.putAll(extractCodeComments(knowledgeSources.get("source"), methodSignature));
        }
        
        methodInfo.setAdditionalContext(combinedContext);
        return testGenerationService.generateTestForMethod(methodInfo);
    }
    
    /**
     * Integrates all available knowledge sources including Atlassian data for test generation
     * 
     * @param methodSignature Method signature to generate tests for
     * @param knowledgeSources Map of knowledge source paths by type
     * @param credentials Atlassian credentials for authentication (optional)
     * @param jiraProjectKey Jira project key (optional)
     * @param confluenceSpaceKey Confluence space key (optional)
     * @return Enhanced test case with comprehensive knowledge integration
     */
    public TestCase integrateAllKnowledgeSources(
            String methodSignature, 
            Map<String, Path> knowledgeSources,
            AtlassianCredentials credentials,
            String jiraProjectKey,
            String confluenceSpaceKey) {
        
        logger.info("Integrating all knowledge sources including Atlassian data for method {}", methodSignature);
        
        MethodInfo methodInfo = codeAnalysisService.findMethodBySignature(methodSignature);
        if (methodInfo == null) {
            logger.error("Method info not found for {}", methodSignature);
            return null;
        }
        
        Map<String, Object> combinedContext = new HashMap<>();
        List<KnowledgeSource> usedSources = new ArrayList<>();
        
        // Process file-based knowledge sources
        if (knowledgeSources != null) {
            if (knowledgeSources.containsKey("api")) {
                Map<String, Object> apiContext = parseApiDocumentation(knowledgeSources.get("api"), methodSignature);
                combinedContext.putAll(apiContext);
                usedSources.add(new KnowledgeSource("api", knowledgeSources.get("api").toString(), "swagger", true));
            }
            
            if (knowledgeSources.containsKey("docs")) {
                Map<String, Object> docsContext = parseProjectDocumentation(knowledgeSources.get("docs"), methodSignature);
                combinedContext.putAll(docsContext);
                usedSources.add(new KnowledgeSource("docs", knowledgeSources.get("docs").toString(), "markdown", true));
            }
            
            if (knowledgeSources.containsKey("history")) {
                Map<String, Object> historyContext = parseHistoricalTestData(knowledgeSources.get("history"), methodSignature);
                combinedContext.putAll(historyContext);
                usedSources.add(new KnowledgeSource("history", knowledgeSources.get("history").toString(), "junit", true));
            }
            
            if (knowledgeSources.containsKey("source")) {
                Map<String, Object> codeContext = extractCodeComments(knowledgeSources.get("source"), methodSignature);
                combinedContext.putAll(codeContext);
                usedSources.add(new KnowledgeSource("source", knowledgeSources.get("source").toString(), "java", true));
            }
        }
        
        // Process Atlassian data sources if credentials are provided
        if (credentials != null) {
            String className = methodInfo.getClassName();
            
            // Integrate Jira data
            if (jiraProjectKey != null && !jiraProjectKey.isEmpty()) {
                JiraFilter filter = JiraFilter.builder()
                    .projectKey(jiraProjectKey)
                    .maxResults(5)
                    .includeComments(true)
                    .build();
                
                List<JiraIssue> issues = jiraService.getIssuesForMethod(methodSignature, className, credentials);
                if (!issues.isEmpty()) {
                    Map<String, Object> jiraContext = extractJiraContext(issues, methodSignature);
                    combinedContext.putAll(jiraContext);
                    
                    // Add Jira sources to knowledge sources
                    for (JiraIssue issue : issues) {
                        usedSources.add(new KnowledgeSource(
                            "jira", 
                            credentials.getBaseUrl() + "/browse/" + issue.getKey(),
                            "jira",
                            true
                        ));
                    }
                }
            }
            
            // Integrate Confluence data
            if (confluenceSpaceKey != null && !confluenceSpaceKey.isEmpty()) {
                List<ConfluencePage> pages = confluenceService.getPagesForMethod(methodSignature, className, credentials);
                if (!pages.isEmpty()) {
                    Map<String, Object> confluenceContext = extractConfluenceContext(pages, methodSignature);
                    combinedContext.putAll(confluenceContext);
                    
                    // Add Confluence sources to knowledge sources
                    for (ConfluencePage page : pages) {
                        usedSources.add(new KnowledgeSource(
                            "confluence", 
                            credentials.getBaseUrl() + page.getWebUrl(),
                            "confluence",
                            true
                        ));
                    }
                }
            }
        }
        
        // Set the combined context on the method info
        methodInfo.setAdditionalContext(combinedContext);
        
        // Generate test with enhanced context
        TestCase testCase = testGenerationService.generateTestForMethod(methodInfo);
        
        // Add knowledge sources to the test case
        testCase.setKnowledgeSources(usedSources);
        
        return testCase;
    }
    
    // Helper methods for parsing different knowledge sources
    
    private Map<String, Object> parseApiDocumentation(Path apiDocPath, String methodSignature) {
        // Implementation would parse Swagger/OpenAPI docs to extract:
        // - Expected request/response formats
        // - Validation rules
        // - Error codes
        // - Examples
        // This is a placeholder - actual implementation would depend on the format
        Map<String, Object> context = new HashMap<>();
        context.put("source", "apiDoc");
        context.put("path", apiDocPath.toString());
        return context;
    }
    
    private Map<String, Object> parseProjectDocumentation(Path docPath, String methodSignature) {
        // Implementation would extract relevant information from:
        // - README files
        // - Wiki pages
        // - Specification documents
        Map<String, Object> context = new HashMap<>();
        context.put("source", "projectDoc");
        context.put("path", docPath.toString());
        return context;
    }
    
    private Map<String, Object> parseHistoricalTestData(Path historyPath, String methodSignature) {
        // Implementation would analyze existing tests to identify:
        // - Common test patterns
        // - Edge cases already covered
        // - Test data formats
        Map<String, Object> context = new HashMap<>();
        context.put("source", "testHistory");
        context.put("path", historyPath.toString());
        return context;
    }
    
    private Map<String, Object> extractCodeComments(Path sourcePath, String methodSignature) {
        // Implementation would extract:
        // - Javadoc comments
        // - TODO comments
        // - In-line documentation
        Map<String, Object> context = new HashMap<>();
        context.put("source", "codeComments");
        context.put("path", sourcePath.toString());
        return context;
    }
    
    /**
     * Extract context data from Jira issues
     * 
     * @param issues List of Jira issues
     * @param methodSignature Method signature
     * @return Map of context data for test generation
     */
    private Map<String, Object> extractJiraContext(List<JiraIssue> issues, String methodSignature) {
        Map<String, Object> context = new HashMap<>();
        
        if (issues == null || issues.isEmpty()) {
            return context;
        }
        
        StringBuilder issuesSummary = new StringBuilder();
        List<String> requirements = new ArrayList<>();
        List<String> testSteps = new ArrayList<>();
        List<String> expectedResults = new ArrayList<>();
        List<String> bugDescriptions = new ArrayList<>();
        
        // Process each issue to extract relevant information
        for (JiraIssue issue : issues) {
            issuesSummary.append(issue.getTestGenerationSummary()).append("\n\n");
            
            // Categorize and extract specific information
            if ("Bug".equalsIgnoreCase(issue.getIssueType())) {
                bugDescriptions.add(issue.getKey() + ": " + issue.getSummary());
                if (issue.getDescription() != null && !issue.getDescription().isEmpty()) {
                    bugDescriptions.add(issue.getDescription());
                }
            }
            
            // Collect requirements
            if (!issue.getAcceptanceCriteria().isEmpty()) {
                requirements.addAll(issue.getAcceptanceCriteria());
            }
            
            // For requirements in description
            if (issue.getDescription() != null && !issue.getDescription().isEmpty()) {
                for (String line : issue.getDescription().split("\\n")) {
                    if (line.contains("should") || line.contains("must") || line.contains("shall")) {
                        requirements.add(line.trim());
                    }
                }
            }
            
            // Collect test steps and expected results
            testSteps.addAll(issue.getTestSteps());
            expectedResults.addAll(issue.getExpectedResults());
        }
        
        // Add to context
        context.put("jiraIssuesSummary", issuesSummary.toString());
        
        if (!requirements.isEmpty()) {
            context.put("jiraRequirements", requirements);
        }
        
        if (!testSteps.isEmpty()) {
            context.put("jiraTestSteps", testSteps);
        }
        
        if (!expectedResults.isEmpty()) {
            context.put("jiraExpectedResults", expectedResults);
        }
        
        if (!bugDescriptions.isEmpty()) {
            context.put("jiraBugDescriptions", bugDescriptions);
        }
        
        // Add source information
        context.put("source", "jira");
        return context;
    }
    
    /**
     * Extract context data from Confluence pages
     * 
     * @param pages List of Confluence pages
     * @param methodSignature Method signature
     * @return Map of context data for test generation
     */
    private Map<String, Object> extractConfluenceContext(List<ConfluencePage> pages, String methodSignature) {
        Map<String, Object> context = new HashMap<>();
        
        if (pages == null || pages.isEmpty()) {
            return context;
        }
        
        StringBuilder pagesSummary = new StringBuilder();
        List<String> apiDocs = new ArrayList<>();
        List<String> requirementDocs = new ArrayList<>();
        List<String> designDocs = new ArrayList<>();
        List<String> userGuideDocs = new ArrayList<>();
        
        // Process each page to extract relevant information
        for (ConfluencePage page : pages) {
            pagesSummary.append(page.getTestGenerationSummary()).append("\n\n");
            
            // Categorize page content
            if (page.isAPIDoc()) {
                apiDocs.add(page.getTitle() + ": " + 
                            (page.getContent() != null ? page.getContent().substring(0, Math.min(500, page.getContent().length())) : ""));
            }
            
            if (page.isRequirementDoc()) {
                requirementDocs.add(page.getTitle() + ": " + 
                                   (page.getContent() != null ? page.getContent().substring(0, Math.min(500, page.getContent().length())) : ""));
            }
            
            if (page.isArchitectureDoc()) {
                designDocs.add(page.getTitle() + ": " + 
                              (page.getContent() != null ? page.getContent().substring(0, Math.min(500, page.getContent().length())) : ""));
            }
            
            if (page.isUserGuide()) {
                userGuideDocs.add(page.getTitle() + ": " + 
                                 (page.getContent() != null ? page.getContent().substring(0, Math.min(500, page.getContent().length())) : ""));
            }
        }
        
        // Add to context
        context.put("confluencePagesSummary", pagesSummary.toString());
        
        if (!apiDocs.isEmpty()) {
            context.put("confluenceApiDocs", apiDocs);
        }
        
        if (!requirementDocs.isEmpty()) {
            context.put("confluenceRequirements", requirementDocs);
        }
        
        if (!designDocs.isEmpty()) {
            context.put("confluenceDesignDocs", designDocs);
        }
        
        if (!userGuideDocs.isEmpty()) {
            context.put("confluenceUserGuides", userGuideDocs);
        }
        
        // Add source information
        context.put("source", "confluence");
        return context;
    }
}