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
@Service("mainKnowledgeIntegrationService")
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
        logger.info("Parsing API documentation from {} for method {}", apiDocPath, methodSignature);
        Map<String, Object> context = new HashMap<>();
        
        try {
            // Parse the file content based on its format
            String content = new String(java.nio.file.Files.readAllBytes(apiDocPath));
            String fileExt = apiDocPath.toString().toLowerCase();
            
            // Extract method name from signature for lookup
            String methodName = methodSignature;
            if (methodSignature.contains("(")) {
                methodName = methodSignature.substring(0, methodSignature.indexOf("("));
            }
            
            if (fileExt.endsWith(".json") || fileExt.endsWith(".yaml") || fileExt.endsWith(".yml")) {
                // Parse OpenAPI/Swagger format
                context.putAll(parseOpenApiFormat(content, methodName));
            } else if (fileExt.endsWith(".xml")) {
                // Parse XML API doc format
                context.putAll(parseXmlApiFormat(content, methodName));
            } else if (fileExt.endsWith(".md") || fileExt.endsWith(".txt")) {
                // Parse markdown/text documentation
                context.putAll(parseTextApiFormat(content, methodName));
            }
            
            // Add source metadata
            context.put("source", "apiDoc");
            context.put("path", apiDocPath.toString());
            context.put("parsed", true);
            
            logger.info("Successfully parsed API documentation with {} extracted elements", context.size() - 3);
        } catch (Exception e) {
            logger.error("Error parsing API documentation: {}", e.getMessage(), e);
            context.put("source", "apiDoc");
            context.put("path", apiDocPath.toString());
            context.put("parsed", false);
            context.put("error", e.getMessage());
        }
        
        return context;
    }
    
    /**
     * Parse OpenAPI/Swagger documentation (JSON/YAML format)
     * 
     * @param apiContent The API document content
     * @param methodName The method name to look for
     * @return Map of extracted API metadata
     */
    private Map<String, Object> parseOpenApiFormat(String apiContent, String methodName) {
        Map<String, Object> apiMetadata = new HashMap<>();
        
        try {
            // Look for endpoints relating to the method name
            // This is a simplified approach - in a real implementation you would use a proper
            // OpenAPI parser library like Swagger Parser or OpenAPI4J
            
            // First, check if we have JSON or YAML
            boolean isJson = apiContent.trim().startsWith("{");
            
            // Extract paths/endpoints
            List<String> endpoints = new ArrayList<>();
            List<String> requestFormats = new ArrayList<>();
            List<String> responseFormats = new ArrayList<>();
            List<String> errorCodes = new ArrayList<>();
            
            // Simplistic pattern matching for the method name in the API doc
            String[] lines = apiContent.split("\n");
            boolean inRelevantPath = false;
            StringBuilder currentBlock = new StringBuilder();
            
            for (String line : lines) {
                // Look for method name indicators in the API doc
                if (line.toLowerCase().contains(methodName.toLowerCase())) {
                    inRelevantPath = true;
                    currentBlock.setLength(0); // Reset block
                    currentBlock.append(line).append("\n");
                    
                    // Extract path if in a path definition
                    if (line.contains("/") && (line.contains("path") || line.contains("url"))) {
                        String path = extractPath(line);
                        if (path != null) {
                            endpoints.add(path);
                        }
                    }
                } else if (inRelevantPath) {
                    currentBlock.append(line).append("\n");
                    
                    // Check if we've reached the end of this block
                    if ((isJson && line.trim().equals("}")) || 
                        (!isJson && (line.trim().isEmpty() || line.trim().startsWith("---")))) {
                        inRelevantPath = false;
                        
                        // Process collected block
                        String block = currentBlock.toString();
                        
                        // Extract request details
                        if (block.contains("requestBody") || block.contains("parameters")) {
                            requestFormats.add(extractRequestFormat(block));  
                        }
                        
                        // Extract response details
                        if (block.contains("responses")) {
                            responseFormats.add(extractResponseFormat(block));
                            
                            // Extract error codes
                            errorCodes.addAll(extractErrorCodes(block));
                        }
                    }
                }
            }
            
            // Add findings to the metadata
            if (!endpoints.isEmpty()) {
                apiMetadata.put("apiEndpoints", endpoints);
            }
            
            if (!requestFormats.isEmpty()) {
                apiMetadata.put("apiRequestFormats", requestFormats);
            }
            
            if (!responseFormats.isEmpty()) {
                apiMetadata.put("apiResponseFormats", responseFormats);
            }
            
            if (!errorCodes.isEmpty()) {
                apiMetadata.put("apiErrorCodes", errorCodes);
            }
            
            apiMetadata.put("apiFormat", isJson ? "json" : "yaml");
            
        } catch (Exception e) {
            logger.error("Error parsing OpenAPI format: {}", e.getMessage(), e);
            apiMetadata.put("parseError", e.getMessage());
        }
        
        return apiMetadata;
    }
    
    /**
     * Parse XML API documentation
     * 
     * @param xmlContent The API document content
     * @param methodName The method name to look for
     * @return Map of extracted API metadata
     */
    private Map<String, Object> parseXmlApiFormat(String xmlContent, String methodName) {
        Map<String, Object> apiMetadata = new HashMap<>();
        
        try {
            // Look for elements relating to the method name
            // This is a simplified approach - in a real implementation you would use a proper XML parser
            
            // Extract API details using pattern matching
            List<String> endpoints = new ArrayList<>();
            List<String> requestFormats = new ArrayList<>();
            List<String> responseFormats = new ArrayList<>();
            
            // Simplistic pattern matching for the method name in the XML
            String[] lines = xmlContent.split("\n");
            boolean inRelevantMethod = false;
            StringBuilder currentBlock = new StringBuilder();
            
            for (String line : lines) {
                // Look for method name indicators in the XML
                if (line.toLowerCase().contains(methodName.toLowerCase())) {
                    inRelevantMethod = true;
                    currentBlock.setLength(0); // Reset block
                    currentBlock.append(line).append("\n");
                    
                    // Extract path from method definition
                    if (line.contains("url") || line.contains("path") || line.contains("endpoint")) {
                        String path = extractXmlValue(line, "url");
                        if (path == null) {
                            path = extractXmlValue(line, "path");
                        }
                        if (path == null) {
                            path = extractXmlValue(line, "endpoint");
                        }
                        
                        if (path != null) {
                            endpoints.add(path);
                        }
                    }
                } else if (inRelevantMethod) {
                    currentBlock.append(line).append("\n");
                    
                    // Check if we've reached the end of this method block
                    if (line.contains("</method>") || line.contains("</endpoint>") || line.contains("</api>")) {
                        inRelevantMethod = false;
                        
                        // Process collected block
                        String block = currentBlock.toString();
                        
                        // Extract request details
                        if (block.contains("<request>") || block.contains("<parameter>")) {
                            requestFormats.add(extractXmlRequestFormat(block));
                        }
                        
                        // Extract response details
                        if (block.contains("<response>")) {
                            responseFormats.add(extractXmlResponseFormat(block));
                        }
                    }
                }
            }
            
            // Add findings to the metadata
            if (!endpoints.isEmpty()) {
                apiMetadata.put("apiEndpoints", endpoints);
            }
            
            if (!requestFormats.isEmpty()) {
                apiMetadata.put("apiRequestFormats", requestFormats);
            }
            
            if (!responseFormats.isEmpty()) {
                apiMetadata.put("apiResponseFormats", responseFormats);
            }
            
            apiMetadata.put("apiFormat", "xml");
            
        } catch (Exception e) {
            logger.error("Error parsing XML API format: {}", e.getMessage(), e);
            apiMetadata.put("parseError", e.getMessage());
        }
        
        return apiMetadata;
    }
    
    /**
     * Parse text-based API documentation (Markdown, plain text)
     * 
     * @param textApiContent The API document content
     * @param methodName The method name to look for
     * @return Map of extracted API metadata
     */
    private Map<String, Object> parseTextApiFormat(String textApiContent, String methodName) {
        Map<String, Object> apiMetadata = new HashMap<>();
        
        try {
            // Look for sections relating to the method name
            // This uses heuristic-based parsing for plain text and markdown
            
            // Extract content using pattern matching
            List<String> endpoints = new ArrayList<>();
            List<String> requirements = new ArrayList<>();
            List<String> validationRules = new ArrayList<>();
            List<String> examples = new ArrayList<>();
            
            String[] lines = textApiContent.split("\n");
            boolean inRelevantSection = false;
            boolean inExampleSection = false;
            StringBuilder currentSection = new StringBuilder();
            StringBuilder currentExample = new StringBuilder();
            
            for (String line : lines) {
                // Look for method name or headers indicating relevant sections
                if (line.toLowerCase().contains(methodName.toLowerCase()) || 
                    (line.startsWith("#") && line.toLowerCase().contains(methodName.toLowerCase()))) {
                    
                    inRelevantSection = true;
                    inExampleSection = false;
                    currentSection.setLength(0); // Reset section
                    currentSection.append(line).append("\n");
                    
                    // Check for endpoint definition
                    if (line.contains("/") && 
                        (line.toLowerCase().contains("endpoint") || 
                         line.toLowerCase().contains("url") || 
                         line.toLowerCase().contains("path"))) {
                        String path = extractEndpointFromText(line);
                        if (path != null) {
                            endpoints.add(path);
                        }
                    }
                } else if (inRelevantSection) {
                    // Check if we've moved to a new section header
                    if (line.startsWith("#")) {
                        inRelevantSection = false;
                        
                        // Process collected section
                        String section = currentSection.toString().toLowerCase();
                        
                        // Analyze the section content for specific information
                        if (section.contains("requirement") || 
                            section.contains("must") || 
                            section.contains("should")) {
                            extractRequirements(currentSection.toString(), requirements);
                        }
                        
                        if (section.contains("validation") || 
                            section.contains("valid") || 
                            section.contains("rule")) {
                            extractValidationRules(currentSection.toString(), validationRules);
                        }
                    } else {
                        currentSection.append(line).append("\n");
                        
                        // Check for example markers
                        if (line.toLowerCase().contains("example") || 
                            line.toLowerCase().contains("sample") || 
                            line.startsWith("```")) {
                            inExampleSection = true;
                            currentExample.setLength(0); // Reset example
                        } else if (inExampleSection) {
                            currentExample.append(line).append("\n");
                            
                            // Check for end of code example
                            if (line.startsWith("```")) {
                                inExampleSection = false;
                                String example = currentExample.toString().trim();
                                if (!example.isEmpty()) {
                                    examples.add(example);
                                }
                            }
                        }
                    }
                }
            }
            
            // Process any remaining section
            if (inRelevantSection) {
                String section = currentSection.toString().toLowerCase();
                if (section.contains("requirement") || 
                    section.contains("must") || 
                    section.contains("should")) {
                    extractRequirements(currentSection.toString(), requirements);
                }
                
                if (section.contains("validation") || 
                    section.contains("valid") || 
                    section.contains("rule")) {
                    extractValidationRules(currentSection.toString(), validationRules);
                }
            }
            
            // Add findings to the metadata
            if (!endpoints.isEmpty()) {
                apiMetadata.put("apiEndpoints", endpoints);
            }
            
            if (!requirements.isEmpty()) {
                apiMetadata.put("apiRequirements", requirements);
            }
            
            if (!validationRules.isEmpty()) {
                apiMetadata.put("apiValidationRules", validationRules);
            }
            
            if (!examples.isEmpty()) {
                apiMetadata.put("apiExamples", examples);
            }
            
            apiMetadata.put("apiFormat", "text");
            
        } catch (Exception e) {
            logger.error("Error parsing text API format: {}", e.getMessage(), e);
            apiMetadata.put("parseError", e.getMessage());
        }
        
        return apiMetadata;
    }
    
    // Helper methods for API documentation parsing
    
    private String extractPath(String line) {
        // Extract path from OpenAPI path definition
        if (line.contains("\"") && line.contains("/")) {
            int startIndex = line.indexOf("/");
            int endIndex = line.indexOf("\"", startIndex);
            if (endIndex == -1) {
                endIndex = line.length();
            }
            return line.substring(startIndex, endIndex).trim();
        }
        return null;
    }
    
    private String extractRequestFormat(String block) {
        // Extract request format details from an OpenAPI block
        StringBuilder format = new StringBuilder();
        
        if (block.contains("requestBody")) {
            format.append("Request body: ");
            if (block.contains("application/json")) {
                format.append("JSON");
            } else if (block.contains("application/xml")) {
                format.append("XML");
            } else if (block.contains("multipart/form-data")) {
                format.append("Form Data");
            } else if (block.contains("application/x-www-form-urlencoded")) {
                format.append("URL Encoded Form");
            } else {
                format.append("Unknown format");
            }
        }
        
        if (block.contains("parameters")) {
            if (!format.isEmpty()) {
                format.append(", ");
            }
            format.append("Parameters: ");
            
            // Count parameters by type
            int pathParams = countOccurrences(block, "path");
            int queryParams = countOccurrences(block, "query");
            int headerParams = countOccurrences(block, "header");
            
            if (pathParams > 0) {
                format.append(pathParams).append(" path");
            }
            if (queryParams > 0) {
                if (pathParams > 0) format.append(", ");
                format.append(queryParams).append(" query");
            }
            if (headerParams > 0) {
                if (pathParams > 0 || queryParams > 0) format.append(", ");
                format.append(headerParams).append(" header");
            }
        }
        
        return format.toString();
    }
    
    private String extractResponseFormat(String block) {
        // Extract response format details from an OpenAPI block
        StringBuilder format = new StringBuilder("Response: ");
        
        // Check for success codes
        if (block.contains("200") || block.contains("201") || block.contains("204")) {
            if (block.contains("200")) format.append("200 OK");
            if (block.contains("201")) format.append("201 Created");
            if (block.contains("204")) format.append("204 No Content");
            
            // Check content type
            if (block.contains("application/json")) {
                format.append(" (JSON)");
            } else if (block.contains("application/xml")) {
                format.append(" (XML)");
            } else if (block.contains("text/plain")) {
                format.append(" (Plain Text)");
            }
        } else {
            format.append("Unknown");
        }
        
        return format.toString();
    }
    
    private List<String> extractErrorCodes(String block) {
        // Extract error codes from an OpenAPI responses block
        List<String> errorCodes = new ArrayList<>();
        
        // Common HTTP error codes to look for
        String[] codes = {"400", "401", "403", "404", "409", "500", "503"};
        for (String code : codes) {
            if (block.contains(code)) {
                String description = "";
                
                // Try to extract the description for this error code
                int codeIndex = block.indexOf(code);
                int descriptionIndex = block.indexOf("description", codeIndex);
                if (descriptionIndex > 0) {
                    int startQuote = block.indexOf("\"", descriptionIndex);
                    int endQuote = block.indexOf("\"", startQuote + 1);
                    if (startQuote > 0 && endQuote > 0) {
                        description = block.substring(startQuote + 1, endQuote);
                    }
                }
                
                if (!description.isEmpty()) {
                    errorCodes.add(code + ": " + description);
                } else {
                    errorCodes.add(code);
                }
            }
        }
        
        return errorCodes;
    }
    
    private String extractXmlValue(String line, String tag) {
        // Extract value from XML tag
        String openTag = "<" + tag;
        String closeTag = "</" + tag + ">";
        int openIndex = line.indexOf(openTag);
        int closeIndex = line.indexOf(closeTag);
        
        if (openIndex >= 0 && closeIndex > 0) {
            int valueStart = line.indexOf(">", openIndex) + 1;
            return line.substring(valueStart, closeIndex).trim();
        }
        
        // Try to extract from attribute
        openIndex = line.indexOf(tag + "=\"");
        if (openIndex >= 0) {
            int valueStart = openIndex + tag.length() + 2;
            int valueEnd = line.indexOf("\"", valueStart);
            if (valueEnd > 0) {
                return line.substring(valueStart, valueEnd).trim();
            }
        }
        
        return null;
    }
    
    private String extractXmlRequestFormat(String block) {
        // Extract request format details from an XML API doc block
        StringBuilder format = new StringBuilder();
        
        if (block.contains("<request>")) {
            format.append("Request: ");
            
            // Check for content types
            if (block.contains("content-type") || block.contains("Content-Type")) {
                if (block.contains("application/json")) {
                    format.append("JSON");
                } else if (block.contains("application/xml")) {
                    format.append("XML");
                } else if (block.contains("multipart/form-data")) {
                    format.append("Form Data");
                } else {
                    format.append("Unknown Format");
                }
            } else {
                format.append("Default Format");
            }
        }
        
        // Count parameters
        int paramCount = countOccurrences(block, "<parameter>");
        if (paramCount > 0) {
            if (!format.isEmpty()) {
                format.append(", ");
            }
            format.append(paramCount).append(" parameters");
        }
        
        return format.toString();
    }
    
    private String extractXmlResponseFormat(String block) {
        // Extract response format details from an XML API doc block
        StringBuilder format = new StringBuilder("Response: ");
        
        // Check for status codes
        String status = null;
        if (block.contains("status=")) {
            int statusIndex = block.indexOf("status=\"");
            if (statusIndex > 0) {
                int valueStart = statusIndex + 8;
                int valueEnd = block.indexOf("\"", valueStart);
                if (valueEnd > 0) {
                    status = block.substring(valueStart, valueEnd).trim();
                }
            }
        }
        
        if (status != null) {
            format.append(status);
        } else if (block.contains("success")) {
            format.append("Success");
        } else {
            format.append("Unknown status");
        }
        
        // Check content type
        if (block.contains("content-type") || block.contains("Content-Type")) {
            if (block.contains("application/json")) {
                format.append(" (JSON)");
            } else if (block.contains("application/xml")) {
                format.append(" (XML)");
            } else if (block.contains("text/plain")) {
                format.append(" (Plain Text)");
            }
        }
        
        return format.toString();
    }
    
    private String extractEndpointFromText(String line) {
        // Extract endpoint URL from text content
        int startIndex = line.indexOf("/");
        if (startIndex >= 0) {
            // Find the end of the URL (whitespace, end of line, or punctuation)
            int endIndex = line.length();
            for (int i = startIndex + 1; i < line.length(); i++) {
                char c = line.charAt(i);
                if (Character.isWhitespace(c) || c == ',' || c == ';' || c == '.' || c == ')') {
                    endIndex = i;
                    break;
                }
            }
            
            return line.substring(startIndex, endIndex).trim();
        }
        return null;
    }
    
    private void extractRequirements(String section, List<String> requirements) {
        // Extract requirement statements from text
        String[] lines = section.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            
            // Look for requirement indicators
            if (line.toLowerCase().contains("must") || 
                line.toLowerCase().contains("should") || 
                line.toLowerCase().contains("required") || 
                line.toLowerCase().contains("shall")) {
                
                requirements.add(line);
            } else if (line.startsWith("-") || line.startsWith("*") || 
                       (line.length() > 2 && Character.isDigit(line.charAt(0)) && line.charAt(1) == '.')) {
                // This is a bullet point, might be a requirement
                if (line.toLowerCase().contains("must") || 
                    line.toLowerCase().contains("should") || 
                    line.toLowerCase().contains("required")) {
                    
                    requirements.add(line.substring(2).trim());
                }
            }
        }
    }
    
    private void extractValidationRules(String section, List<String> validationRules) {
        // Extract validation rules from text
        String[] lines = section.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            
            // Look for validation rule indicators
            if (line.toLowerCase().contains("valid") || 
                line.toLowerCase().contains("rule") || 
                line.toLowerCase().contains("check") || 
                line.toLowerCase().contains("ensure")) {
                
                validationRules.add(line);
            } else if (line.startsWith("-") || line.startsWith("*") || 
                       (line.length() > 2 && Character.isDigit(line.charAt(0)) && line.charAt(1) == '.')) {
                // This is a bullet point, might be a validation rule
                if (line.toLowerCase().contains("valid") || 
                    line.toLowerCase().contains("check") || 
                    line.toLowerCase().contains("ensure")) {
                    
                    validationRules.add(line.substring(2).trim());
                }
            }
        }
    }
    
    private int countOccurrences(String text, String substring) {
        // Count occurrences of a substring in text
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
    
    private Map<String, Object> parseProjectDocumentation(Path docPath, String methodSignature) {
        logger.info("Parsing project documentation from {} for method {}", docPath, methodSignature);
        Map<String, Object> context = new HashMap<>();
        
        try {
            // Read file content
            String content = new String(java.nio.file.Files.readAllBytes(docPath));
            String fileName = docPath.getFileName().toString().toLowerCase();
            
            // Extract method name from signature for lookup
            String methodName = methodSignature;
            if (methodSignature.contains("(")) {
                methodName = methodSignature.substring(0, methodSignature.indexOf("("));
            }
            String className = "";
            MethodInfo mInfo = codeAnalysisService.findMethodBySignature(methodSignature);
            if (mInfo != null && mInfo.getClassName() != null) {
                className = mInfo.getClassName();
            }
            
            // Process different documentation types based on file name or format
            if (fileName.contains("readme") || fileName.endsWith(".md")) {
                context.putAll(parseMarkdownDocumentation(content, methodName, className));
            } else if (fileName.endsWith(".txt") || fileName.endsWith(".doc") || fileName.endsWith(".rtf")) {
                context.putAll(parseTextDocumentation(content, methodName, className));
            } else if (fileName.endsWith(".pdf")) {
                // Note: PDF parsing would require libraries like Apache PDFBox
                // This is a placeholder for that functionality
                context.put("pdfDocumentation", "PDF parsing would require additional libraries");
            } else if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
                context.putAll(parseHtmlDocumentation(content, methodName, className));
            }
            
            // Add source information
            context.put("source", "projectDoc");
            context.put("path", docPath.toString());
            context.put("docType", getDocumentationType(fileName));
            context.put("parsed", true);
            
            logger.info("Successfully parsed project documentation with {} extracted elements", context.size() - 4);
        } catch (Exception e) {
            logger.error("Error parsing project documentation: {}", e.getMessage(), e);
            context.put("source", "projectDoc");
            context.put("path", docPath.toString());
            context.put("parsed", false);
            context.put("error", e.getMessage());
        }
        
        return context;
    }
    
    /**
     * Parse Markdown documentation
     * 
     * @param mdContent The document content
     * @param methodName The method name to look for
     * @param className Optional class name for context
     * @return Map of extracted documentation metadata
     */
    private Map<String, Object> parseMarkdownDocumentation(String mdContent, String methodName, String className) {
        Map<String, Object> docMetadata = new HashMap<>();
        
        try {
            // Analyze markdown structure and extract relevant sections
            List<String> functionDescriptions = new ArrayList<>();
            List<String> usageExamples = new ArrayList<>();
            List<String> requirements = new ArrayList<>();
            List<String> testCases = new ArrayList<>();
            
            // Process document by sections with markdown headers
            String[] lines = mdContent.split("\n");
            boolean inRelevantSection = false;
            boolean inExampleBlock = false;
            boolean inTableBlock = false;
            String currentSectionTitle = "";
            StringBuilder currentSection = new StringBuilder();
            StringBuilder currentExample = new StringBuilder();
            
            for (String line : lines) {
                String lineNormalized = line.toLowerCase().trim();
                
                // Check for section headers
                if (line.startsWith("#")) {
                    // Process the previous section before moving to a new one
                    if (inRelevantSection) {
                        processDocumentationSection(currentSectionTitle, currentSection.toString(), 
                                                methodName, className, functionDescriptions, 
                                                usageExamples, requirements, testCases);
                    }
                    
                    // Start a new section
                    currentSectionTitle = line.replaceAll("^#+\\s*", "").trim();
                    currentSection.setLength(0);
                    
                    // Check if this section is relevant to our method or class
                    inRelevantSection = isRelevantSection(currentSectionTitle, methodName, className);
                } else if (inRelevantSection) {
                    // Add this line to the current section
                    currentSection.append(line).append("\n");
                    
                    // Check for code blocks (usually examples)
                    if (line.startsWith("```") || line.startsWith("~~~")) {
                        if (!inExampleBlock) {
                            inExampleBlock = true;
                            currentExample.setLength(0);
                        } else {
                            inExampleBlock = false;
                            String example = currentExample.toString().trim();
                            if (!example.isEmpty()) {
                                usageExamples.add(example);
                            }
                        }
                    } else if (inExampleBlock) {
                        currentExample.append(line).append("\n");
                    }
                    
                    // Check for table blocks (usually test cases or requirements)
                    if (lineNormalized.contains("|--")) {
                        inTableBlock = true;
                    } else if (inTableBlock && line.contains("|")) {
                        // This is a table row
                        String tableRow = line.trim();
                        if (tableRow.toLowerCase().contains("test") || 
                            tableRow.toLowerCase().contains("case")) {
                            testCases.add(tableRow);
                        } else if (tableRow.toLowerCase().contains("require") || 
                                 tableRow.toLowerCase().contains("must") || 
                                 tableRow.toLowerCase().contains("should")) {
                            requirements.add(tableRow);
                        }
                    } else if (inTableBlock && !line.contains("|")) {
                        inTableBlock = false;
                    }
                    
                    // Check for direct method or function descriptions
                    if (lineNormalized.contains(methodName.toLowerCase()) && 
                        (lineNormalized.contains("function") || 
                         lineNormalized.contains("method") || 
                         lineNormalized.contains("api") || 
                         lineNormalized.contains("description"))) {
                        functionDescriptions.add(line.trim());
                    }
                }
            }
            
            // Process the last section if it was relevant
            if (inRelevantSection) {
                processDocumentationSection(currentSectionTitle, currentSection.toString(), 
                                        methodName, className, functionDescriptions, 
                                        usageExamples, requirements, testCases);
            }
            
            // Add findings to metadata
            if (!functionDescriptions.isEmpty()) {
                docMetadata.put("functionDescriptions", functionDescriptions);
            }
            
            if (!usageExamples.isEmpty()) {
                docMetadata.put("usageExamples", usageExamples);
            }
            
            if (!requirements.isEmpty()) {
                docMetadata.put("requirements", requirements);
            }
            
            if (!testCases.isEmpty()) {
                docMetadata.put("testCases", testCases);
            }
        } catch (Exception e) {
            logger.error("Error parsing markdown documentation: {}", e.getMessage(), e);
            docMetadata.put("parseError", e.getMessage());
        }
        
        return docMetadata;
    }
    
    /**
     * Process a markdown documentation section and extract relevant information
     */
    private void processDocumentationSection(String sectionTitle, String sectionContent, 
                                            String methodName, String className,
                                            List<String> functionDescriptions, 
                                            List<String> usageExamples, 
                                            List<String> requirements, 
                                            List<String> testCases) {
        
        // Check section title for relevance
        String titleLower = sectionTitle.toLowerCase();
        
        if (titleLower.contains("usage") || titleLower.contains("example")) {
            // Extract usage examples from this section
            extractCodeBlocks(sectionContent, usageExamples);
        } else if (titleLower.contains("requirement") || titleLower.contains("specification")) {
            // Extract requirements from this section
            extractRequirementsFromDoc(sectionContent, requirements);
        } else if (titleLower.contains("test") || titleLower.contains("case")) {
            // Extract test cases from this section
            extractTestCases(sectionContent, testCases);
        } else if (titleLower.contains("api") || 
                 titleLower.contains("function") || 
                 titleLower.contains("method") || 
                 titleLower.contains("description")) {
            // This might be a function/method description section
            String[] lines = sectionContent.split("\n");
            for (String line : lines) {
                if (line.toLowerCase().contains(methodName.toLowerCase()) ||
                    (className != null && !className.isEmpty() && line.toLowerCase().contains(className.toLowerCase()))) {
                    functionDescriptions.add(line.trim());
                }
            }
        }
    }
    
    /**
     * Extract code blocks from markdown content
     */
    private void extractCodeBlocks(String content, List<String> codeBlocks) {
        // Look for code blocks delimited by ``` or ~~~
        String[] lines = content.split("\n");
        boolean inCodeBlock = false;
        StringBuilder currentBlock = new StringBuilder();
        
        for (String line : lines) {
            if (line.startsWith("```") || line.startsWith("~~~")) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    currentBlock.setLength(0);
                } else {
                    inCodeBlock = false;
                    String block = currentBlock.toString().trim();
                    if (!block.isEmpty()) {
                        codeBlocks.add(block);
                    }
                }
            } else if (inCodeBlock) {
                currentBlock.append(line).append("\n");
            }
        }
    }
    
    /**
     * Extract requirements from documentation content
     */
    private void extractRequirementsFromDoc(String documentationContent, List<String> requirements) {
        // Look for requirement statements in content
        String[] lines = documentationContent.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // Check for requirement indicators
            if (line.toLowerCase().contains("must") || 
                line.toLowerCase().contains("should") || 
                line.toLowerCase().contains("shall") || 
                line.toLowerCase().contains("require") || 
                line.toLowerCase().contains("need")) {
                
                requirements.add(line);
            } else if (line.startsWith("-") || line.startsWith("*") || 
                     line.matches("^\\d+\\..*")) {  // Numbered list
                // This is a list item, check if it's a requirement
                String content = line.replaceFirst("^[-*]\\s*|^\\d+\\.\\s*", "").trim();
                if (content.toLowerCase().contains("must") || 
                    content.toLowerCase().contains("should") || 
                    content.toLowerCase().contains("shall") || 
                    content.toLowerCase().contains("require") || 
                    content.toLowerCase().contains("need")) {
                    
                    requirements.add(content);
                }
            }
        }
    }
    
    /**
     * Extract test cases from documentation content
     */
    private void extractTestCases(String content, List<String> testCases) {
        // Look for test case descriptions in content
        String[] lines = content.split("\n");
        boolean inTestCaseBlock = false;
        StringBuilder currentTestCase = new StringBuilder();
        
        for (String line : lines) {
            line = line.trim();
            
            // Check for test case indicators
            if (line.toLowerCase().contains("test case") || 
                line.toLowerCase().contains("test scenario")) {
                
                // Start of a new test case
                if (inTestCaseBlock && !currentTestCase.isEmpty()) {
                    testCases.add(currentTestCase.toString().trim());
                }
                
                inTestCaseBlock = true;
                currentTestCase.setLength(0);
                currentTestCase.append(line).append("\n");
            } else if (inTestCaseBlock) {
                // Add to current test case
                if (line.isEmpty()) {
                    // Empty line might indicate end of test case description
                    inTestCaseBlock = false;
                    if (!currentTestCase.isEmpty()) {
                        testCases.add(currentTestCase.toString().trim());
                    }
                } else {
                    currentTestCase.append(line).append("\n");
                }
            } else if (line.toLowerCase().contains("test") && 
                     (line.startsWith("-") || line.startsWith("*") || 
                      line.matches("^\\d+\\..*"))) {  // List item with "test"
                      
                testCases.add(line.replaceFirst("^[-*]\\s*|^\\d+\\.\\s*", "").trim());
            }
        }
        
        // Add the last test case if we were in a block
        if (inTestCaseBlock && !currentTestCase.isEmpty()) {
            testCases.add(currentTestCase.toString().trim());
        }
    }
    
    /**
     * Check if a section is relevant to the method or class being analyzed
     */
    private boolean isRelevantSection(String sectionTitle, String methodName, String className) {
        String titleLower = sectionTitle.toLowerCase();
        
        // Check for direct method/class name mentions
        if (titleLower.contains(methodName.toLowerCase())) {
            return true;
        }
        
        if (className != null && !className.isEmpty() && titleLower.contains(className.toLowerCase())) {
            return true;
        }
        
        // Check for general relevant sections
        return titleLower.contains("api") ||
                titleLower.contains("usage") ||
                titleLower.contains("example") ||
                titleLower.contains("test") ||
                titleLower.contains("method") ||
                titleLower.contains("function") ||
                titleLower.contains("requirement") ||
                titleLower.contains("specification");
    }
    
    /**
     * Parse plain text documentation
     * 
     * @param textContent The document content
     * @param methodName The method name to look for
     * @param className Optional class name for context
     * @return Map of extracted documentation metadata
     */
    private Map<String, Object> parseTextDocumentation(String textContent, String methodName, String className) {
        Map<String, Object> docMetadata = new HashMap<>();
        
        try {
            // For text documents, we'll use a simpler approach based on keyword matching
            List<String> relevantSections = new ArrayList<>();
            List<String> examples = new ArrayList<>();
            List<String> requirements = new ArrayList<>();
            
            // First split the document into sections/paragraphs
            String[] paragraphs = textContent.split("\n\s*\n");
            
            for (String paragraph : paragraphs) {
                paragraph = paragraph.trim();
                if (paragraph.isEmpty()) continue;
                
                // Check if this paragraph is relevant to our method or class
                if (paragraph.toLowerCase().contains(methodName.toLowerCase()) || 
                    (className != null && !className.isEmpty() && 
                     paragraph.toLowerCase().contains(className.toLowerCase()))) {
                    
                    relevantSections.add(paragraph);
                    
                    // Check if it contains requirements
                    if (paragraph.toLowerCase().contains("must") || 
                        paragraph.toLowerCase().contains("should") || 
                        paragraph.toLowerCase().contains("shall") || 
                        paragraph.toLowerCase().contains("require")) {
                        
                        requirements.add(paragraph);
                    }
                    
                    // Check if it contains examples
                    if (paragraph.toLowerCase().contains("example") || 
                        paragraph.toLowerCase().contains("usage") || 
                        paragraph.toLowerCase().contains("sample")) {
                        
                        examples.add(paragraph);
                    }
                }
            }
            
            // Add findings to metadata
            if (!relevantSections.isEmpty()) {
                docMetadata.put("relevantSections", relevantSections);
            }
            
            if (!examples.isEmpty()) {
                docMetadata.put("examples", examples);
            }
            
            if (!requirements.isEmpty()) {
                docMetadata.put("requirements", requirements);
            }
        } catch (Exception e) {
            logger.error("Error parsing text documentation: {}", e.getMessage(), e);
            docMetadata.put("parseError", e.getMessage());
        }
        
        return docMetadata;
    }
    
    /**
     * Parse HTML documentation
     * 
     * @param htmlContent The document content
     * @param methodName The method name to look for
     * @param className Optional class name for context
     * @return Map of extracted documentation metadata
     */
    private Map<String, Object> parseHtmlDocumentation(String htmlContent, String methodName, String className) {
        Map<String, Object> docMetadata = new HashMap<>();
        
        try {
            // For HTML, we'll do a simplified parsing without a full DOM parser
            // This extracts text from HTML and then processes it like plain text
            
            // First remove HTML tags but keep their contents
            String textContent = htmlContent.replaceAll("<script.*?</script>", "")  // Remove scripts
                                      .replaceAll("<style.*?</style>", "")      // Remove styles
                                      .replaceAll("<!--.*?-->", "")            // Remove comments
                                      .replaceAll("<[^>]*>", " ")              // Remove tags
                                      .replaceAll("&nbsp;", " ")               // Replace non-breaking spaces
                                      .replaceAll("\\s+", " ")                 // Normalize whitespace
                                      .trim();
            
            // Now process the extracted text like a text document
            Map<String, Object> textResults = parseTextDocumentation(textContent, methodName, className);
            docMetadata.putAll(textResults);
            
            // Also look for specific HTML elements of interest
            // Like code examples in <pre> or <code> blocks
            List<String> codeExamples = extractHtmlCodeBlocks(htmlContent);
            if (!codeExamples.isEmpty()) {
                docMetadata.put("codeExamples", codeExamples);
            }
            
            // Look for tables which might contain test cases or requirements
            List<String> tableContents = extractHtmlTables(htmlContent);
            if (!tableContents.isEmpty()) {
                docMetadata.put("tableContents", tableContents);
            }
        } catch (Exception e) {
            logger.error("Error parsing HTML documentation: {}", e.getMessage(), e);
            docMetadata.put("parseError", e.getMessage());
        }
        
        return docMetadata;
    }
    
    /**
     * Extract code blocks from HTML content
     */
    private List<String> extractHtmlCodeBlocks(String htmlContent) {
        List<String> codeBlocks = new ArrayList<>();
        
        // Extract content from <pre> and <code> tags
        extractTagContent(htmlContent, "pre", codeBlocks);
        extractTagContent(htmlContent, "code", codeBlocks);
        
        return codeBlocks;
    }
    
    /**
     * Extract tables from HTML content
     */
    private List<String> extractHtmlTables(String htmlContent) {
        List<String> tableContents = new ArrayList<>();
        
        // Extract content from <table> tags and convert to text representation
        List<String> rawTables = new ArrayList<>();
        extractTagContent(htmlContent, "table", rawTables);
        
        for (String table : rawTables) {
            // Convert HTML table to text representation
            String textTable = table.replaceAll("<tr>", "\n")
                                .replaceAll("</tr>", "")
                                .replaceAll("<t[hd]>", "|")
                                .replaceAll("</t[hd]>", "|")
                                .replaceAll("<[^>]*>", "")
                                .trim();
            tableContents.add(textTable);
        }
        
        return tableContents;
    }
    
    /**
     * Extract content of specific HTML tags
     */
    private void extractTagContent(String htmlContent, String tagName, List<String> results) {
        int startIndex = 0;
        String startTag = "<" + tagName;
        String endTag = "</" + tagName + ">";
        
        while ((startIndex = htmlContent.indexOf(startTag, startIndex)) != -1) {
            int contentStart = htmlContent.indexOf(">", startIndex) + 1;
            int contentEnd = htmlContent.indexOf(endTag, contentStart);
            
            if (contentEnd > contentStart) {
                String content = htmlContent.substring(contentStart, contentEnd).trim();
                results.add(content);
                startIndex = contentEnd + endTag.length();
            } else {
                startIndex += startTag.length();
            }
        }
    }
    
    /**
     * Determine the type of document based on its filename
     */
    private String getDocumentationType(String fileName) {
        fileName = fileName.toLowerCase();
        
        if (fileName.contains("readme")) {
            return "readme";
        } else if (fileName.contains("spec") || fileName.contains("specification")) {
            return "specification";
        } else if (fileName.contains("design")) {
            return "design";
        } else if (fileName.contains("requirement")) {
            return "requirements";
        } else if (fileName.contains("api") || fileName.contains("interface")) {
            return "api";
        } else if (fileName.contains("test") || fileName.contains("case")) {
            return "test";
        } else if (fileName.contains("doc") || fileName.contains("documentation")) {
            return "documentation";
        } else {
            return "other";
        }
    }
    
    private Map<String, Object> parseHistoricalTestData(Path historyPath, String methodSignature) {
        logger.info("Parsing historical test data from {} for method {}", historyPath, methodSignature);
        Map<String, Object> context = new HashMap<>();
        
        try {
            // Read file content
            String content = new String(java.nio.file.Files.readAllBytes(historyPath));
            String fileName = historyPath.getFileName().toString().toLowerCase();
            
            // Extract method name from signature for lookup
            String methodName = methodSignature;
            if (methodSignature.contains("(")) {
                methodName = methodSignature.substring(0, methodSignature.indexOf("("));
            }
            
            // Initialize collections for extracted data
            List<String> testPatterns = new ArrayList<>();
            List<String> edgeCases = new ArrayList<>();
            List<String> testDataFormats = new ArrayList<>();
            Map<String, List<String>> paramValues = new HashMap<>();
            Map<String, Integer> assertionTypes = new HashMap<>();
            List<String> complexExamples = new ArrayList<>();
            
            // Process based on file type
            if (fileName.endsWith(".java") || fileName.endsWith(".groovy")) {
                analyzeJavaTestFile(content, methodName, testPatterns, edgeCases, 
                                    testDataFormats, paramValues, assertionTypes, complexExamples);
            } else if (fileName.endsWith(".xml")) {
                // JUnit XML report format
                analyzeJUnitXmlReport(content, methodName, testPatterns, edgeCases);
            } else if (fileName.endsWith(".json")) {
                // Test history in JSON format
                analyzeJsonTestHistory(content, methodName, testPatterns, edgeCases, testDataFormats);
            } else if (fileName.endsWith(".csv")) {
                // Test data in CSV format
                analyzeCsvTestData(content, methodName, testDataFormats, paramValues);
            }
            
            // Add extracted data to the context
            if (!testPatterns.isEmpty()) {
                context.put("testPatterns", testPatterns);
            }
            
            if (!edgeCases.isEmpty()) {
                context.put("edgeCases", edgeCases);
            }
            
            if (!testDataFormats.isEmpty()) {
                context.put("testDataFormats", testDataFormats);
            }
            
            if (!paramValues.isEmpty()) {
                context.put("parameterValues", paramValues);
            }
            
            if (!assertionTypes.isEmpty()) {
                context.put("assertionTypes", assertionTypes);
            }
            
            if (!complexExamples.isEmpty()) {
                context.put("complexExamples", complexExamples);
            }
            
            // Add metadata
            context.put("source", "testHistory");
            context.put("path", historyPath.toString());
            context.put("fileType", getFileType(fileName));
            context.put("parsed", true);
            
            logger.info("Successfully parsed historical test data with {} extracted elements", 
                     testPatterns.size() + edgeCases.size() + testDataFormats.size() + 
                     paramValues.size() + assertionTypes.size() + complexExamples.size());
        } catch (Exception e) {
            logger.error("Error parsing historical test data: {}", e.getMessage(), e);
            context.put("source", "testHistory");
            context.put("path", historyPath.toString());
            context.put("parsed", false);
            context.put("error", e.getMessage());
        }
        
        return context;
    }
    
    /**
     * Analyze a Java/Groovy test file to extract historical test patterns
     * 
     * @param content File content
     * @param methodName The method name to look for
     * @param testPatterns Collection to add identified test patterns to
     * @param edgeCases Collection to add identified edge cases to
     * @param testDataFormats Collection to add identified test data formats to
     * @param paramValues Map to add parameter values to
     * @param assertionTypes Map to add assertion types and counts to
     * @param complexExamples Collection to add complex test examples to
     */
    private void analyzeJavaTestFile(String content, String methodName, 
                                   List<String> testPatterns, List<String> edgeCases,
                                   List<String> testDataFormats, Map<String, List<String>> paramValues,
                                   Map<String, Integer> assertionTypes, List<String> complexExamples) {
        
        // Look for test methods
        String[] lines = content.split("\n");
        boolean inTestMethod = false;
        boolean methodCallFound = false;
        StringBuilder currentTest = new StringBuilder();
        String currentTestName = "";
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            // Check for test method declaration
            if (trimmedLine.contains("@Test") || 
                (trimmedLine.startsWith("public void test") && trimmedLine.contains("(")) || 
                (trimmedLine.startsWith("def test") && trimmedLine.contains("("))) {
                
                // Start collecting a new test method
                if (inTestMethod && methodCallFound) {
                    // Process the previous test method first
                    analyzeTestMethod(currentTestName, currentTest.toString(), methodName, 
                                     testPatterns, edgeCases, testDataFormats, 
                                     paramValues, assertionTypes, complexExamples);
                }
                
                inTestMethod = true;
                methodCallFound = false;
                currentTest.setLength(0);
                
                // Extract the test name if possible
                if (trimmedLine.contains("void ")) {
                    int start = trimmedLine.indexOf("void ") + 5;
                    int end = trimmedLine.indexOf("(", start);
                    if (end > start) {
                        currentTestName = trimmedLine.substring(start, end).trim();
                    }
                } else if (trimmedLine.contains("def ")) {
                    int start = trimmedLine.indexOf("def ") + 4;
                    int end = trimmedLine.indexOf("(", start);
                    if (end > start) {
                        currentTestName = trimmedLine.substring(start, end).trim();
                    }
                } else {
                    currentTestName = "unknown";
                }
            } else if (inTestMethod) {
                // Add this line to the current test method
                currentTest.append(line).append("\n");
                
                // Check for method call
                if (trimmedLine.contains(methodName + "(")) {
                    methodCallFound = true;
                }
                
                // Check for end of test method
                if (trimmedLine.equals("}")) {
                    if (countOccurrences(currentTest.toString(), "{") <= countOccurrences(currentTest.toString(), "}")) {
                        // This likely marks the end of the test method
                        if (methodCallFound) {
                            analyzeTestMethod(currentTestName, currentTest.toString(), methodName, 
                                           testPatterns, edgeCases, testDataFormats, 
                                           paramValues, assertionTypes, complexExamples);
                        }
                        inTestMethod = false;
                    }
                }
            }
        }
        
        // Process the last test method if we were still collecting it
        if (inTestMethod && methodCallFound) {
            analyzeTestMethod(currentTestName, currentTest.toString(), methodName, 
                           testPatterns, edgeCases, testDataFormats, 
                           paramValues, assertionTypes, complexExamples);
        }
    }
    
    /**
     * Analyze a single test method to extract patterns and values
     */
    private void analyzeTestMethod(String testName, String testMethod, String methodName,
                                 List<String> testPatterns, List<String> edgeCases,
                                 List<String> testDataFormats, Map<String, List<String>> paramValues,
                                 Map<String, Integer> assertionTypes, List<String> complexExamples) {
        
        // Check test name for patterns
        if (testName.toLowerCase().contains("edge") || 
            testName.toLowerCase().contains("boundary") ||
            testName.toLowerCase().contains("exception") || 
            testName.toLowerCase().contains("error")) {
            // This is likely an edge case test
            edgeCases.add("Edge case test: " + testName);
        }
        
        // Extract the test pattern from the name and code
        String pattern = identifyTestPattern(testName, testMethod);
        if (pattern != null) {
            testPatterns.add(pattern);
        }
        
        // Look for method call and extract parameters
        String methodCall = extractMethodCall(testMethod, methodName);
        if (methodCall != null) {
            extractParameterValues(methodCall, paramValues);
            testDataFormats.add("Method call: " + methodCall);
        }
        
        // Look for assertions and count types
        countAssertionTypes(testMethod, assertionTypes);
        
        // Check for complex test setup
        if (isComplexTest(testMethod)) {
            complexExamples.add(testName + ": " + testMethod);
        }
    }
    
    /**
     * Identify the test pattern based on name and content
     */
    private String identifyTestPattern(String testName, String testMethod) {
        String nameLower = testName.toLowerCase();
        String methodLower = testMethod.toLowerCase();
        
        if (nameLower.contains("null") || methodLower.contains("null")) {
            return "Null value test";
        } else if (nameLower.contains("empty") || methodLower.contains("empty")) {
            return "Empty value test";
        } else if (nameLower.contains("invalid") || methodLower.contains("invalid")) {
            return "Invalid input test";
        } else if (nameLower.contains("exception") || methodLower.contains("exception") || 
                 testMethod.contains("@Test(expected = ")) {
            return "Exception test";
        } else if (nameLower.contains("boundary") || methodLower.contains("boundary") || 
                 nameLower.contains("min") || nameLower.contains("max") || 
                 nameLower.contains("limit")) {
            return "Boundary value test";
        } else if (nameLower.contains("performance") || methodLower.contains("performance") || 
                 methodLower.contains("benchmark") || methodLower.contains("timeout")) {
            return "Performance test";
        } else if (methodLower.contains("mock") || methodLower.contains("stub") || 
                 methodLower.contains("spy")) {
            return "Mock object test";
        } else if (nameLower.contains("happy") || nameLower.contains("positive") || 
                 nameLower.contains("normal")) {
            return "Happy path test";
        } else {
            // Default pattern
            return "Standard unit test";
        }
    }
    
    /**
     * Extract method call from test method
     */
    private String extractMethodCall(String testMethod, String methodName) {
        // Look for the method call in the test method
        int methodIndex = testMethod.indexOf(methodName + "(");
        if (methodIndex != -1) {
            // Find the end of the method call (closing parenthesis)
            int startIndex = methodIndex;
            int endIndex = findMatchingClosingParenthesis(testMethod, methodIndex + methodName.length());
            
            if (endIndex > startIndex) {
                return testMethod.substring(startIndex, endIndex + 1).trim();
            }
        }
        return null;
    }
    
    /**
     * Find matching closing parenthesis for a method call
     */
    private int findMatchingClosingParenthesis(String text, int openParenIndex) {
        int level = 0;
        boolean foundOpen = false;
        
        for (int i = openParenIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                foundOpen = true;
                level++;
            } else if (c == ')') {
                level--;
                if (foundOpen && level == 0) {
                    return i;
                }
            }
        }
        
        return -1;  // No matching closing parenthesis found
    }
    
    /**
     * Extract parameter values from a method call
     */
    private void extractParameterValues(String methodCall, Map<String, List<String>> paramValues) {
        // Extract parameter string between parentheses
        int openParen = methodCall.indexOf("(");
        int closeParen = methodCall.lastIndexOf(")");
        
        if (openParen != -1 && closeParen > openParen) {
            String paramsStr = methodCall.substring(openParen + 1, closeParen).trim();
            
            if (!paramsStr.isEmpty()) {
                // Split parameters by comma, accounting for nested structures
                List<String> params = splitParameterList(paramsStr);
                
                // Add parameters to the map by position
                for (int i = 0; i < params.size(); i++) {
                    String param = params.get(i).trim();
                    String paramKey = "param" + (i + 1);
                    
                    if (!paramValues.containsKey(paramKey)) {
                        paramValues.put(paramKey, new ArrayList<>());
                    }
                    
                    paramValues.get(paramKey).add(param);
                }
            }
        }
    }
    
    /**
     * Split a parameter list string, handling nested structures
     */
    private List<String> splitParameterList(String paramsStr) {
        List<String> params = new ArrayList<>();
        StringBuilder currentParam = new StringBuilder();
        int nestLevel = 0;
        
        for (int i = 0; i < paramsStr.length(); i++) {
            char c = paramsStr.charAt(i);
            
            if ((c == ',' || c == ';') && nestLevel == 0) {
                // Found parameter separator at top level
                params.add(currentParam.toString().trim());
                currentParam.setLength(0);  // Reset for next parameter
            } else {
                // Add this character to the current parameter
                currentParam.append(c);
                
                // Update nesting level
                if (c == '(' || c == '[' || c == '{') {
                    nestLevel++;
                } else if (c == ')' || c == ']' || c == '}') {
                    nestLevel--;
                }
            }
        }
        
        // Add the last parameter if there's anything left
        if (currentParam.length() > 0) {
            params.add(currentParam.toString().trim());
        }
        
        return params;
    }
    
    /**
     * Count different assertion types used in a test method
     */
    private void countAssertionTypes(String testMethod, Map<String, Integer> assertionTypes) {
        // Look for JUnit assertions
        String[] assertionPatterns = {
            "assertEquals", "assertTrue", "assertFalse", "assertNull", 
            "assertNotNull", "assertSame", "assertNotSame", "assertThat",
            "assertThrows", "assertArrayEquals", "assertAll", "fail"
        };
        
        for (String assertion : assertionPatterns) {
            int count = countOccurrences(testMethod, assertion + "(");
            if (count > 0) {
                assertionTypes.put(assertion, count);
            }
        }
    }
    
    /**
     * Check if a test method is complex based on its structure
     */
    private boolean isComplexTest(String testMethod) {
        // A test is complex if it has multiple assertions
        int assertCount = 0;
        String[] assertions = {"assert", "verify", "check", "expect"};
        
        for (String assertion : assertions) {
            assertCount += countOccurrences(testMethod, assertion);
        }
        
        // Or if it has multiple methods under test
        int methodCalls = countOccurrences(testMethod, "();") + 
                         countOccurrences(testMethod, "( );") + 
                         countOccurrences(testMethod, "());");
        
        // Or if it has complex setup with mocks/stubs
        boolean hasMocks = testMethod.contains("mock") || testMethod.contains("stub") || 
                          testMethod.contains("spy") || testMethod.contains("when(");
        
        return assertCount > 2 || methodCalls > 3 || hasMocks;
    }
    
    /**
     * Analyze a JUnit XML report file
     */
    private void analyzeJUnitXmlReport(String content, String methodName, 
                                      List<String> testPatterns, List<String> edgeCases) {
        // Simple XML parsing to extract test cases and metadata
        try {
            // Look for <testcase> elements
            int testCaseIdx = 0;
            while ((testCaseIdx = content.indexOf("<testcase", testCaseIdx)) != -1) {
                int endIdx = content.indexOf("</testcase>", testCaseIdx);
                if (endIdx == -1) {
                    // Maybe self-closing tag
                    endIdx = content.indexOf("/>", testCaseIdx);
                    if (endIdx == -1) {
                        break;
                    }
                    endIdx += 2;  // Include the closing tag
                } else {
                    endIdx += 11;  // Length of </testcase>
                }
                
                // Extract the test case element
                String testCase = content.substring(testCaseIdx, endIdx);
                
                // Check if it's related to our method
                if (testCase.contains(methodName)) {
                    // Extract test name
                    String testName = extractXmlAttribute(testCase, "name");
                    if (testName != null) {
                        // Check for patterns in the name
                        if (testName.toLowerCase().contains("edge") || 
                            testName.toLowerCase().contains("boundary") || 
                            testName.toLowerCase().contains("exception")) {
                            edgeCases.add("JUnit report edge case: " + testName);
                        }
                        
                        // Identify the test pattern
                        String pattern = identifyTestPatternFromName(testName);
                        if (pattern != null) {
                            testPatterns.add(pattern);
                        }
                    }
                    
                    // Check for failures or errors
                    if (testCase.contains("<failure") || testCase.contains("<error")) {
                        // Extract the message
                        String message = extractXmlAttribute(testCase, "message");
                        if (message != null) {
                            edgeCases.add("Failed test case: " + (testName != null ? testName : "unknown") + 
                                        " - Message: " + message);
                        }
                    }
                }
                
                testCaseIdx = endIdx;
            }
        } catch (Exception e) {
            logger.warn("Error parsing JUnit XML report: {}", e.getMessage());
        }
    }
    
    /**
     * Extract an XML attribute value
     */
    private String extractXmlAttribute(String element, String attributeName) {
        String pattern = attributeName + "=\"([^\"]*)\""; 
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(element);
        
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
    
    /**
     * Identify a test pattern from the test name alone
     */
    private String identifyTestPatternFromName(String testName) {
        String nameLower = testName.toLowerCase();
        
        if (nameLower.contains("null")) {
            return "Null value test";
        } else if (nameLower.contains("empty")) {
            return "Empty value test";
        } else if (nameLower.contains("invalid")) {
            return "Invalid input test";
        } else if (nameLower.contains("exception") || nameLower.contains("error") || 
                 nameLower.contains("failure")) {
            return "Exception test";
        } else if (nameLower.contains("boundary") || nameLower.contains("min") || 
                 nameLower.contains("max") || nameLower.contains("limit")) {
            return "Boundary value test";
        } else if (nameLower.contains("performance") || nameLower.contains("timeout") || 
                 nameLower.contains("benchmark")) {
            return "Performance test";
        } else if (nameLower.contains("mock")) {
            return "Mock object test";
        } else if (nameLower.contains("happy") || nameLower.contains("positive") || 
                 nameLower.contains("normal")) {
            return "Happy path test";
        }
        
        return null;
    }
    
    /**
     * Analyze JSON test history data
     */
    private void analyzeJsonTestHistory(String content, String methodName, 
                                       List<String> testPatterns, List<String> edgeCases,
                                       List<String> testDataFormats) {
        try {
            // Very basic JSON parsing - in a real implementation, use a proper JSON parser
            // This is a simplistic approach for demonstration
            
            // Look for the method name in the JSON
            if (content.contains(methodName)) {
                // Look for test patterns
                if (content.contains("\"pattern\":")) {
                    extractJsonValues(content, "pattern", testPatterns);
                }
                
                // Look for edge cases
                if (content.contains("\"edgeCase\":")) {
                    extractJsonValues(content, "edgeCase", edgeCases);
                }
                
                // Look for test data formats
                if (content.contains("\"format\":")) {
                    extractJsonValues(content, "format", testDataFormats);
                }
            }
        } catch (Exception e) {
            logger.warn("Error parsing JSON test history: {}", e.getMessage());
        }
    }
    
    /**
     * Extract JSON field values
     */
    private void extractJsonValues(String json, String fieldName, List<String> values) {
        String pattern = "\"" + fieldName + "\":\s*\"([^\"]*)\""; 
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(json);
        
        while (m.find()) {
            values.add(m.group(1));
        }
    }
    
    /**
     * Analyze CSV test data
     */
    private void analyzeCsvTestData(String content, String methodName, 
                                  List<String> testDataFormats, Map<String, List<String>> paramValues) {
        try {
            // Parse the CSV content
            String[] lines = content.split("\n");
            if (lines.length < 2) {
                return;  // Need at least header and one data row
            }
            
            // Check the header row
            String[] headers = splitCsvLine(lines[0]);
            boolean isRelevant = false;
            
            // Check if this CSV is relevant to our method
            for (String header : headers) {
                if (header.contains(methodName)) {
                    isRelevant = true;
                    break;
                }
            }
            
            if (isRelevant) {
                testDataFormats.add("CSV format with " + headers.length + " columns");
                
                // Process data rows
                for (int i = 1; i < lines.length; i++) {
                    String[] values = splitCsvLine(lines[i]);
                    if (values.length == headers.length) {
                        // Add values to parameters by header name
                        for (int j = 0; j < headers.length; j++) {
                            String paramName = headers[j].trim();
                            if (!paramValues.containsKey(paramName)) {
                                paramValues.put(paramName, new ArrayList<>());
                            }
                            paramValues.get(paramName).add(values[j].trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error parsing CSV test data: {}", e.getMessage());
        }
    }
    
    /**
     * Split a CSV line, handling quoted values
     */
    private String[] splitCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;  // Toggle quote state
            } else if (c == ',' && !inQuotes) {
                // End of a value
                values.add(currentValue.toString().trim());
                currentValue.setLength(0);  // Reset for next value
            } else {
                currentValue.append(c);
            }
        }
        
        // Add the last value
        values.add(currentValue.toString().trim());
        
        return values.toArray(new String[0]);
    }
    
    /**
     * Determine the file type based on filename
     */
    private String getFileType(String fileName) {
        fileName = fileName.toLowerCase();
        
        if (fileName.endsWith(".java")) {
            return "java";
        } else if (fileName.endsWith(".groovy")) {
            return "groovy";
        } else if (fileName.endsWith(".xml")) {
            return "xml";
        } else if (fileName.endsWith(".json")) {
            return "json";
        } else if (fileName.endsWith(".csv")) {
            return "csv";
        } else {
            return "other";
        }
    }
    
    private Map<String, Object> extractCodeComments(Path sourcePath, String methodSignature) {
        logger.info("Extracting code comments from {} for method {}", sourcePath, methodSignature);
        Map<String, Object> context = new HashMap<>();
        
        try {
            // Read file content
            String content = new String(java.nio.file.Files.readAllBytes(sourcePath));
            String fileName = sourcePath.getFileName().toString().toLowerCase();
            
            // Extract method name from signature for lookup
            String methodName = methodSignature;
            if (methodSignature.contains("(")) {
                methodName = methodSignature.substring(0, methodSignature.indexOf("("));
            }
            
            // Initialize collections for different types of comments
            List<String> javadocComments = new ArrayList<>();
            List<String> inlineComments = new ArrayList<>();
            List<String> blockComments = new ArrayList<>();
            Map<String, String> parameterDocs = new HashMap<>();
            String returnDoc = null;
            List<String> exceptionDocs = new ArrayList<>();
            
            // Process different file types
            if (fileName.endsWith(".java")) {
                // Java source file
                extractJavaComments(content, methodName, javadocComments, inlineComments, 
                                blockComments, parameterDocs, exceptionDocs);
                
                // Find method's return documentation
                returnDoc = findJavaReturnDoc(content, methodName);
            } else if (fileName.endsWith(".js") || fileName.endsWith(".ts")) {
                // JavaScript/TypeScript source
                extractJsComments(content, methodName, javadocComments, inlineComments, 
                               blockComments, parameterDocs, exceptionDocs);
                
                // Find method's return documentation
                returnDoc = findJsReturnDoc(content, methodName);
            } else if (fileName.endsWith(".py")) {
                // Python source
                extractPythonComments(content, methodName, javadocComments, inlineComments, 
                                   blockComments, parameterDocs, exceptionDocs);
                
                // Find method's return documentation
                returnDoc = findPythonReturnDoc(content, methodName);
            } else {
                // Generic fallback for other languages
                extractGenericComments(content, methodName, javadocComments, inlineComments, blockComments);
            }
            
            // Add extracted comments to the context
            if (!javadocComments.isEmpty()) {
                context.put("javadocComments", javadocComments);
            }
            
            if (!inlineComments.isEmpty()) {
                context.put("inlineComments", inlineComments);
            }
            
            if (!blockComments.isEmpty()) {
                context.put("blockComments", blockComments);
            }
            
            if (!parameterDocs.isEmpty()) {
                context.put("parameterDocs", parameterDocs);
            }
            
            if (returnDoc != null) {
                context.put("returnDoc", returnDoc);
            }
            
            if (!exceptionDocs.isEmpty()) {
                context.put("exceptionDocs", exceptionDocs);
            }
            
            // Add metadata
            context.put("source", "codeComments");
            context.put("path", sourcePath.toString());
            context.put("language", getLanguageFromFileName(fileName));
            context.put("parsed", true);
            
            logger.info("Successfully extracted code comments with {} Javadoc, {} inline, and {} block comments", 
                     javadocComments.size(), inlineComments.size(), blockComments.size());
        } catch (Exception e) {
            logger.error("Error extracting code comments: {}", e.getMessage(), e);
            context.put("source", "codeComments");
            context.put("path", sourcePath.toString());
            context.put("parsed", false);
            context.put("error", e.getMessage());
        }
        
        return context;
    }
    
    /**
     * Extract comments from Java source code
     * 
     * @param content Java source code content
     * @param methodName The method name to look for
     * @param javadocComments Collection to add Javadoc comments to
     * @param inlineComments Collection to add inline comments to
     * @param blockComments Collection to add block comments to
     * @param parameterDocs Map to add parameter documentation to
     * @param exceptionDocs Collection to add exception documentation to
     */
    private void extractJavaComments(String content, String methodName, 
                                   List<String> javadocComments, List<String> inlineComments, 
                                   List<String> blockComments, Map<String, String> parameterDocs, 
                                   List<String> exceptionDocs) {
        // Use regex to find the method declaration
        String methodPattern = "(?:public|protected|private|static|\\s)*(?:\\w+\\s+)+" + 
                              methodName + "\\s*\\([^\\)]*\\)\\s*(?:throws\\s+[\\w\\s,]+)?\\s*\\{";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(methodPattern);
        java.util.regex.Matcher m = p.matcher(content);
        
        if (m.find()) {
            // We found the method, now look for its Javadoc
            int methodPosition = m.start();
            String contentBeforeMethod = content.substring(0, methodPosition).trim();
            
            // Look for Javadoc comment before the method
            int javadocEndIndex = contentBeforeMethod.lastIndexOf("*/");
            if (javadocEndIndex != -1) {
                int javadocStartIndex = contentBeforeMethod.lastIndexOf("/**", javadocEndIndex);
                if (javadocStartIndex != -1) {
                    String javadoc = contentBeforeMethod.substring(javadocStartIndex, javadocEndIndex + 2).trim();
                    javadocComments.add(javadoc);
                    
                    // Extract parameter documentation
                    extractJavadocParams(javadoc, parameterDocs, exceptionDocs);
                }
            }
            
            // Get method body
            int methodBodyStart = content.indexOf("{", methodPosition) + 1;
            int methodBodyEnd = findMethodEnd(content, methodBodyStart);
            
            if (methodBodyEnd > methodBodyStart) {
                String methodBody = content.substring(methodBodyStart, methodBodyEnd);
                
                // Extract inline comments
                extractInlineComments(methodBody, inlineComments);
                
                // Extract block comments
                extractBlockComments(methodBody, blockComments);
            }
        }
    }
    
    /**
     * Extract parameter documentation from Javadoc
     */
    private void extractJavadocParams(String javadoc, Map<String, String> parameterDocs, 
                                    List<String> exceptionDocs) {
        // Extract @param tags
        String[] lines = javadoc.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            
            // Handle parameter documentation
            if (line.startsWith("* @param") || line.startsWith("*@param")) {
                String paramLine = line.replaceFirst("\\*\\s*@param\\s+", "").trim();
                int spaceIndex = paramLine.indexOf(" ");
                
                if (spaceIndex != -1) {
                    String paramName = paramLine.substring(0, spaceIndex).trim();
                    String paramDesc = paramLine.substring(spaceIndex + 1).trim();
                    parameterDocs.put(paramName, paramDesc);
                }
            }
            
            // Handle exception documentation
            if (line.startsWith("* @throws") || line.startsWith("*@throws") || 
                line.startsWith("* @exception") || line.startsWith("*@exception")) {
                
                String exceptionLine = line.replaceFirst("\\*\\s*@(throws|exception)\\s+", "").trim();
                exceptionDocs.add(exceptionLine);
            }
        }
    }
    
    /**
     * Find the return documentation in Java code
     */
    private String findJavaReturnDoc(String content, String methodName) {
        // Use regex to find the method and its Javadoc
        String methodPattern = "(?:public|protected|private|static|\\s)*(?:\\w+\\s+)+" + 
                              methodName + "\\s*\\([^\\)]*\\)\\s*(?:throws\\s+[\\w\\s,]+)?\\s*\\{";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(methodPattern);
        java.util.regex.Matcher m = p.matcher(content);
        
        if (m.find()) {
            int methodPosition = m.start();
            String contentBeforeMethod = content.substring(0, methodPosition).trim();
            
            // Look for Javadoc comment before the method
            int javadocEndIndex = contentBeforeMethod.lastIndexOf("*/");
            if (javadocEndIndex != -1) {
                int javadocStartIndex = contentBeforeMethod.lastIndexOf("/**", javadocEndIndex);
                if (javadocStartIndex != -1) {
                    String javadoc = contentBeforeMethod.substring(javadocStartIndex, javadocEndIndex + 2);
                    
                    // Look for @return tag
                    int returnIndex = javadoc.indexOf("@return");
                    if (returnIndex != -1) {
                        String returnText = javadoc.substring(returnIndex + 7).trim();
                        
                        // Extract until the next tag or end of comment
                        int nextTagIndex = returnText.indexOf("@");
                        if (nextTagIndex != -1) {
                            returnText = returnText.substring(0, nextTagIndex).trim();
                        }
                        
                        // Clean up any remaining Javadoc syntax
                        returnText = returnText.replaceAll("\\s*\\*/\\s*$", "").trim();
                        returnText = returnText.replaceAll("^\\s*\\*\\s*", "").trim();
                        returnText = returnText.replaceAll("\n\s*\\*\\s*", " ").trim();
                        
                        return returnText;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extract inline comments from code (// style)
     */
    private void extractInlineComments(String codeText, List<String> inlineComments) {
        // Split the code into lines to find line comments
        String[] lines = codeText.split("\n");
        
        for (String line : lines) {
            int commentIndex = line.indexOf("//");
            if (commentIndex != -1) {
                // Make sure it's not inside a string literal
                if (!isInsideStringLiteral(line, commentIndex)) {
                    String comment = line.substring(commentIndex + 2).trim();
                    inlineComments.add(comment);
                }
            }
        }
    }
    
    /**
     * Extract block comments from code (/* style *​/)
     */
    private void extractBlockComments(String codeText, List<String> blockComments) {
        int startIndex = 0;
        
        while ((startIndex = codeText.indexOf("/*", startIndex)) != -1) {
            // Make sure it's not inside a string literal
            if (!isInsideStringLiteral(codeText, startIndex)) {
                int endIndex = codeText.indexOf("*/", startIndex + 2);
                if (endIndex != -1) {
                    String comment = codeText.substring(startIndex + 2, endIndex).trim();
                    blockComments.add(comment);
                    startIndex = endIndex + 2;
                } else {
                    // No end comment found, stop searching
                    break;
                }
            } else {
                startIndex += 2;
            }
        }
    }
    
    /**
     * Extract comments from JavaScript/TypeScript source code
     */
    private void extractJsComments(String content, String methodName, 
                                List<String> javadocComments, List<String> inlineComments, 
                                List<String> blockComments, Map<String, String> parameterDocs, 
                                List<String> exceptionDocs) {
        // Look for both function declarations and arrow functions
        String functionPattern = "(function\\s+" + methodName + "\\s*\\([^\\)]*\\))|(const\\s+" + 
                               methodName + "\\s*=\\s*\\([^\\)]*\\)\\s*=>)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(functionPattern);
        java.util.regex.Matcher m = p.matcher(content);
        
        if (m.find()) {
            // We found the function, now look for JSDoc
            int functionPosition = m.start();
            String contentBeforeFunction = content.substring(0, functionPosition).trim();
            
            // Look for JSDoc-style comment before the function
            int jsdocEndIndex = contentBeforeFunction.lastIndexOf("*/");
            if (jsdocEndIndex != -1) {
                int jsdocStartIndex = contentBeforeFunction.lastIndexOf("/**", jsdocEndIndex);
                if (jsdocStartIndex != -1) {
                    String jsdoc = contentBeforeFunction.substring(jsdocStartIndex, jsdocEndIndex + 2).trim();
                    javadocComments.add(jsdoc);
                    
                    // Extract JSDoc @param and @throws tags
                    extractJsDocTags(jsdoc, parameterDocs, exceptionDocs);
                }
            }
            
            // Get function body
            int functionBodyStart = content.indexOf("{", functionPosition);
            if (functionBodyStart != -1) {
                functionBodyStart += 1;
                int functionBodyEnd = findMatchingBrace(content, functionBodyStart);
                
                if (functionBodyEnd > functionBodyStart) {
                    String functionBody = content.substring(functionBodyStart, functionBodyEnd);
                    
                    // Extract inline comments
                    extractInlineComments(functionBody, inlineComments);
                    
                    // Extract block comments
                    extractBlockComments(functionBody, blockComments);
                }
            }
        }
    }
    
    /**
     * Extract JSDoc tags for parameters and exceptions
     */
    private void extractJsDocTags(String jsdoc, Map<String, String> parameterDocs, 
                               List<String> exceptionDocs) {
        String[] lines = jsdoc.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            
            // Handle parameter documentation
            if (line.startsWith("* @param") || line.startsWith("*@param")) {
                String paramLine = line.replaceFirst("\\*\\s*@param\\s+", "").trim();
                
                // Handle JSDoc type definition in curly braces
                if (paramLine.startsWith("{")) {
                    int closeBrace = paramLine.indexOf("}");
                    if (closeBrace != -1) {
                        paramLine = paramLine.substring(closeBrace + 1).trim();
                    }
                }
                
                int spaceIndex = paramLine.indexOf(" ");
                if (spaceIndex != -1) {
                    String paramName = paramLine.substring(0, spaceIndex).trim();
                    // Remove optional brackets if present
                    paramName = paramName.replaceAll("^\\[|\\]$", "");
                    
                    String paramDesc = paramLine.substring(spaceIndex + 1).trim();
                    parameterDocs.put(paramName, paramDesc);
                }
            }
            
            // Handle exception documentation
            if (line.startsWith("* @throws") || line.startsWith("*@throws") || 
                line.startsWith("* @exception") || line.startsWith("*@exception")) {
                
                String exceptionLine = line.replaceFirst("\\*\\s*@(throws|exception)\\s+", "").trim();
                
                // Handle JSDoc type definition in curly braces
                if (exceptionLine.startsWith("{")) {
                    int closeBrace = exceptionLine.indexOf("}");
                    if (closeBrace != -1) {
                        exceptionLine = exceptionLine.substring(closeBrace + 1).trim();
                    }
                }
                
                exceptionDocs.add(exceptionLine);
            }
        }
    }
    
    /**
     * Find the return documentation in JavaScript/TypeScript code
     */
    private String findJsReturnDoc(String content, String methodName) {
        // Look for JSDoc @returns or @return tag
        String functionPattern = "(function\\s+" + methodName + "\\s*\\([^\\)]*\\))|(const\\s+" + 
                               methodName + "\\s*=\\s*\\([^\\)]*\\)\\s*=>)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(functionPattern);
        java.util.regex.Matcher m = p.matcher(content);
        
        if (m.find()) {
            int functionPosition = m.start();
            String contentBeforeFunction = content.substring(0, functionPosition).trim();
            
            // Look for JSDoc comment before the function
            int jsdocEndIndex = contentBeforeFunction.lastIndexOf("*/");
            if (jsdocEndIndex != -1) {
                int jsdocStartIndex = contentBeforeFunction.lastIndexOf("/**", jsdocEndIndex);
                if (jsdocStartIndex != -1) {
                    String jsdoc = contentBeforeFunction.substring(jsdocStartIndex, jsdocEndIndex + 2);
                    
                    // Look for @return or @returns tag
                    int returnIndex = jsdoc.indexOf("@return");
                    if (returnIndex == -1) {
                        // Try @returns (more common in JSDoc)
                        returnIndex = jsdoc.indexOf("@returns");
                    }
                    
                    if (returnIndex != -1) {
                        // Skip past the tag name
                        String returnText;
                        if (jsdoc.charAt(returnIndex + 7) == 's') {
                            returnText = jsdoc.substring(returnIndex + 8).trim();
                        } else {
                            returnText = jsdoc.substring(returnIndex + 7).trim();
                        }
                        
                        // Handle type definition in curly braces
                        if (returnText.startsWith("{")) {
                            int closeBrace = returnText.indexOf("}");
                            if (closeBrace != -1) {
                                returnText = returnText.substring(closeBrace + 1).trim();
                            }
                        }
                        
                        // Extract until the next tag or end of comment
                        int nextTagIndex = returnText.indexOf("@");
                        if (nextTagIndex != -1) {
                            returnText = returnText.substring(0, nextTagIndex).trim();
                        }
                        
                        // Clean up any remaining JSDoc syntax
                        returnText = returnText.replaceAll("\\s*\\*/\\s*$", "").trim();
                        returnText = returnText.replaceAll("^\\s*\\*\\s*", "").trim();
                        returnText = returnText.replaceAll("\n\s*\\*\\s*", " ").trim();
                        
                        return returnText;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extract comments from Python source code
     */
    private void extractPythonComments(String content, String methodName, 
                                     List<String> javadocComments, List<String> inlineComments, 
                                     List<String> blockComments, Map<String, String> parameterDocs, 
                                     List<String> exceptionDocs) {
        // Look for def method_name pattern
        String functionPattern = "def\\s+" + methodName + "\\s*\\([^\\)]*\\)\\s*:";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(functionPattern);
        java.util.regex.Matcher m = p.matcher(content);
        
        if (m.find()) {
            // We found the function, now look for docstring
            int functionPosition = m.start();
            int functionBodyStart = content.indexOf(":", functionPosition) + 1;
            
            // Skip whitespace after colon
            while (functionBodyStart < content.length() && 
                  Character.isWhitespace(content.charAt(functionBodyStart))) {
                functionBodyStart++;
            }
            
            // Look for triple-quoted docstring
            if (functionBodyStart + 3 < content.length()) {
                boolean hasDocstring = false;
                int docstringStart = -1;
                int docstringEnd = -1;
                
                // Check for triple double quotes
                if (content.substring(functionBodyStart).startsWith("\"\"\"")) {
                    docstringStart = functionBodyStart;
                    docstringEnd = content.indexOf("\"\"\"", docstringStart + 3);
                    hasDocstring = docstringEnd > docstringStart;
                }
                // Check for triple single quotes
                else if (content.substring(functionBodyStart).startsWith("'''")) {
                    docstringStart = functionBodyStart;
                    docstringEnd = content.indexOf("'''", docstringStart + 3);
                    hasDocstring = docstringEnd > docstringStart;
                }
                
                if (hasDocstring) {
                    // Extract the docstring
                    String docstring = content.substring(docstringStart + 3, docstringEnd).trim();
                    javadocComments.add(docstring);
                    
                    // Parse parameter docs from docstring
                    extractPythonParamDocs(docstring, parameterDocs, exceptionDocs);
                }
                
                // Find the end of the function to extract inline comments
                int functionEnd = findPythonFunctionEnd(content, functionBodyStart);
                if (functionEnd > functionBodyStart) {
                    String functionBody = content.substring(functionBodyStart, functionEnd);
                    
                    // Extract inline comments (# style)
                    String[] lines = functionBody.split("\n");
                    for (String line : lines) {
                        int commentIndex = line.indexOf("#");
                        if (commentIndex != -1) {
                            String comment = line.substring(commentIndex + 1).trim();
                            inlineComments.add(comment);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Extract parameter docs from Python docstring
     */
    private void extractPythonParamDocs(String docstring, Map<String, String> parameterDocs, 
                                      List<String> exceptionDocs) {
        String[] lines = docstring.split("\n");
        
        boolean inParams = false;
        boolean inReturns = false;
        boolean inRaises = false;
        
        String currentParam = null;
        StringBuilder currentParamDesc = new StringBuilder();
        
        for (String line : lines) {
            line = line.trim();
            
            // Check for section headers
            if (line.toLowerCase().startsWith("parameters") || line.toLowerCase().startsWith("args") || 
                line.toLowerCase().startsWith("arguments")) {
                inParams = true;
                inReturns = false;
                inRaises = false;
                continue;
            } else if (line.toLowerCase().startsWith("returns") || line.toLowerCase().startsWith("return")) {
                inParams = false;
                inReturns = true;
                inRaises = false;
                
                // Save any current parameter
                if (currentParam != null && currentParamDesc.length() > 0) {
                    parameterDocs.put(currentParam, currentParamDesc.toString().trim());
                    currentParam = null;
                    currentParamDesc.setLength(0);
                }
                continue;
            } else if (line.toLowerCase().startsWith("raises") || line.toLowerCase().startsWith("exceptions")) {
                inParams = false;
                inReturns = false;
                inRaises = true;
                
                // Save any current parameter
                if (currentParam != null && currentParamDesc.length() > 0) {
                    parameterDocs.put(currentParam, currentParamDesc.toString().trim());
                    currentParam = null;
                    currentParamDesc.setLength(0);
                }
                continue;
            } else if (line.startsWith("---") || line.equals("")) {
                // Section separator or empty line
                
                // Save any current parameter
                if (currentParam != null && currentParamDesc.length() > 0) {
                    parameterDocs.put(currentParam, currentParamDesc.toString().trim());
                    currentParam = null;
                    currentParamDesc.setLength(0);
                }
                continue;
            }
            
            // Process the line based on which section we're in
            if (inParams) {
                // Check for parameter definition
                if (line.startsWith(":param") || line.startsWith("param")) {
                    // Save previous parameter if any
                    if (currentParam != null && currentParamDesc.length() > 0) {
                        parameterDocs.put(currentParam, currentParamDesc.toString().trim());
                        currentParamDesc.setLength(0);
                    }
                    
                    // Extract new parameter
                    String paramLine = line.replaceFirst("^:?param\\s+", "").trim();
                    int colonIndex = paramLine.indexOf(":");
                    if (colonIndex != -1) {
                        currentParam = paramLine.substring(0, colonIndex).trim();
                        currentParamDesc.append(paramLine.substring(colonIndex + 1).trim());
                    } else {
                        // No colon found, try space
                        int spaceIndex = paramLine.indexOf(" ");
                        if (spaceIndex != -1) {
                            currentParam = paramLine.substring(0, spaceIndex).trim();
                            currentParamDesc.append(paramLine.substring(spaceIndex + 1).trim());
                        }
                    }
                } else if (currentParam != null) {
                    // Continuation of parameter description
                    currentParamDesc.append(" ").append(line);
                }
            } else if (inRaises) {
                // Check for exception documentation
                if (line.startsWith(":raises") || line.startsWith("raises")) {
                    String exceptionLine = line.replaceFirst("^:?raises\\s+", "").trim();
                    exceptionDocs.add(exceptionLine);
                }
            }
        }
        
        // Save the last parameter if any
        if (currentParam != null && currentParamDesc.length() > 0) {
            parameterDocs.put(currentParam, currentParamDesc.toString().trim());
        }
    }
    
    /**
     * Find the return documentation in Python code
     */
    private String findPythonReturnDoc(String content, String methodName) {
        // Look for def method_name pattern
        String functionPattern = "def\\s+" + methodName + "\\s*\\([^\\)]*\\)\\s*:";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(functionPattern);
        java.util.regex.Matcher m = p.matcher(content);
        
        if (m.find()) {
            int functionPosition = m.start();
            int functionBodyStart = content.indexOf(":", functionPosition) + 1;
            
            // Skip whitespace after colon
            while (functionBodyStart < content.length() && 
                  Character.isWhitespace(content.charAt(functionBodyStart))) {
                functionBodyStart++;
            }
            
            // Look for triple-quoted docstring
            if (functionBodyStart + 3 < content.length()) {
                int docstringStart = -1;
                int docstringEnd = -1;
                boolean hasDocstring = false;
                
                // Check for triple double quotes
                if (content.substring(functionBodyStart).startsWith("\"\"\"")) {
                    docstringStart = functionBodyStart;
                    docstringEnd = content.indexOf("\"\"\"", docstringStart + 3);
                    hasDocstring = docstringEnd > docstringStart;
                }
                // Check for triple single quotes
                else if (content.substring(functionBodyStart).startsWith("'''")) {
                    docstringStart = functionBodyStart;
                    docstringEnd = content.indexOf("'''", docstringStart + 3);
                    hasDocstring = docstringEnd > docstringStart;
                }
                
                if (hasDocstring) {
                    // Extract the docstring
                    String docstring = content.substring(docstringStart + 3, docstringEnd).trim();
                    
                    // Look for return documentation
                    String[] lines = docstring.split("\n");
                    
                    boolean inReturns = false;
                    StringBuilder returnDoc = new StringBuilder();
                    
                    for (String line : lines) {
                        line = line.trim();
                        
                        if (inReturns) {
                            // Check if we've moved to a new section
                            if (line.startsWith(":param") || line.startsWith("param") || 
                                line.startsWith(":raises") || line.startsWith("raises") || 
                                line.startsWith(":type") || line.startsWith("type") || 
                                line.startsWith("---") || line.equals("")) {
                                
                                // End of returns section
                                break;
                            } else {
                                // Continuation of return description
                                returnDoc.append(" ").append(line);
                            }
                        } else if (line.startsWith(":return") || line.startsWith("return") || 
                                 line.startsWith(":returns") || line.startsWith("returns")) {
                            // Start of returns section
                            inReturns = true;
                            String returnLine = line.replaceFirst("^:?returns?\\s+", "").trim();
                            returnDoc.append(returnLine);
                        } else if (line.toLowerCase().startsWith("returns") || 
                                 line.toLowerCase().startsWith("return")) {
                            // Returns section header
                            inReturns = true;
                        }
                    }
                    
                    if (returnDoc.length() > 0) {
                        return returnDoc.toString().trim();
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extract comments from generic source code
     */
    private void extractGenericComments(String content, String methodName, 
                                      List<String> javadocComments, List<String> inlineComments, 
                                      List<String> blockComments) {
        // Try to look for a method/function definition
        String functionPattern = "(function|def|void|int|string|boolean|object|array|var|let|const)\\s+" + 
                               methodName + "\\s*\\([^\\)]*\\)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(functionPattern);
        java.util.regex.Matcher m = p.matcher(content);
        
        // Default to the whole content if no method is found
        int startPosition = 0;
        int endPosition = content.length();
        
        if (m.find()) {
            // Method found, adjust search range
            startPosition = m.start();
            
            // Try to find the end of the method
            int bodyStart = content.indexOf("{", startPosition);
            if (bodyStart != -1) {
                int bodyEnd = findMatchingBrace(content, bodyStart + 1);
                if (bodyEnd > bodyStart) {
                    endPosition = bodyEnd;
                }
            }
        }
        
        // Extract content in the identified range
        String methodContent = content.substring(startPosition, endPosition);
        
        // Look for block comments (/* ... */)
        int blockCommentStart = 0;
        while ((blockCommentStart = methodContent.indexOf("/*", blockCommentStart)) != -1) {
            int blockCommentEnd = methodContent.indexOf("*/", blockCommentStart + 2);
            if (blockCommentEnd != -1) {
                String blockComment = methodContent.substring(blockCommentStart + 2, blockCommentEnd).trim();
                blockComments.add(blockComment);
                blockCommentStart = blockCommentEnd + 2;
            } else {
                break;
            }
        }
        
        // Look for line comments (// style)
        String[] lines = methodContent.split("\n");
        for (String line : lines) {
            // Check for C-style line comments
            int cStyleComment = line.indexOf("//");
            if (cStyleComment != -1) {
                String comment = line.substring(cStyleComment + 2).trim();
                inlineComments.add(comment);
            }
            
            // Check for Python/Shell style line comments
            int pythonStyleComment = line.indexOf("#");
            if (pythonStyleComment != -1) {
                String comment = line.substring(pythonStyleComment + 1).trim();
                inlineComments.add(comment);
            }
        }
    }
    
    /**
     * Find the end of a method in Java/C/JavaScript code
     */
    private int findMethodEnd(String content, int startIndex) {
        return findMatchingBrace(content, startIndex);
    }
    
    /**
     * Find the matching closing brace for a given opening brace position
     */
    private int findMatchingBrace(String content, int startIndex) {
        int level = 1;
        
        for (int i = startIndex; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                level++;
            } else if (c == '}') {
                level--;
                if (level == 0) {
                    return i;
                }
            }
        }
        
        return -1;  // No matching brace found
    }
    
    /**
     * Find the end of a Python function (based on indentation)
     */
    private int findPythonFunctionEnd(String content, int startIndex) {
        // Skip to the next line to get the function's indentation level
        int lineStart = content.indexOf('\n', startIndex) + 1;
        if (lineStart <= 0 || lineStart >= content.length()) {
            return content.length();
        }
        
        // Determine the indentation level of the function body
        int indentationLevel = 0;
        while (lineStart < content.length() && Character.isWhitespace(content.charAt(lineStart))) {
            indentationLevel++;
            lineStart++;
        }
        
        // Function ends when we hit a line with indentation less than or equal to the original indentation
        String[] lines = content.substring(lineStart).split("\n");
        int currentPosition = lineStart;
        
        for (String line : lines) {
            // Skip empty lines
            if (line.trim().isEmpty()) {
                currentPosition += line.length() + 1;  // +1 for the newline
                continue;
            }
            
            // Count leading whitespace
            int lineIndentation = 0;
            while (lineIndentation < line.length() && Character.isWhitespace(line.charAt(lineIndentation))) {
                lineIndentation++;
            }
            
            // If indentation is less than the function body, we've exited the function
            if (lineIndentation < indentationLevel) {
                return currentPosition;
            }
            
            currentPosition += line.length() + 1;  // +1 for the newline
        }
        
        return content.length();
    }
    
    /**
     * Check if a position is inside a string literal
     */
    private boolean isInsideStringLiteral(String code, int position) {
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;
        
        for (int i = 0; i < position; i++) {
            char c = code.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            }
        }
        
        return inSingleQuotes || inDoubleQuotes;
    }
    
    /**
     * Determine the programming language from a filename
     */
    private String getLanguageFromFileName(String fileName) {
        fileName = fileName.toLowerCase();
        
        if (fileName.endsWith(".java")) {
            return "java";
        } else if (fileName.endsWith(".js")) {
            return "javascript";
        } else if (fileName.endsWith(".ts")) {
            return "typescript";
        } else if (fileName.endsWith(".py")) {
            return "python";
        } else if (fileName.endsWith(".c") || fileName.endsWith(".h")) {
            return "c";
        } else if (fileName.endsWith(".cpp") || fileName.endsWith(".hpp")) {
            return "c++";
        } else if (fileName.endsWith(".cs")) {
            return "c#";
        } else if (fileName.endsWith(".rb")) {
            return "ruby";
        } else if (fileName.endsWith(".go")) {
            return "go";
        } else if (fileName.endsWith(".php")) {
            return "php";
        } else if (fileName.endsWith(".swift")) {
            return "swift";
        } else if (fileName.endsWith(".kt") || fileName.endsWith(".kts")) {
            return "kotlin";
        } else if (fileName.endsWith(".rs")) {
            return "rust";
        } else {
            return "unknown";
        }
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