package com.projectoracle.service;


import com.projectoracle.model.AtlassianCredentials;
import com.projectoracle.model.KnowledgeSource;
import com.projectoracle.model.NLPQueryRequest;
import com.projectoracle.model.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for processing natural language queries and translating them into test generation.
 * This enables users to describe test scenarios in plain English.
 */
@Service
public class NLPQueryService {

    private static final Logger logger = LoggerFactory.getLogger(NLPQueryService.class);
    
    @Autowired
    private CodeAnalysisService codeAnalysisService;
    
    @Autowired
    @Qualifier("mainKnowledgeIntegrationService")
    private KnowledgeIntegrationService knowledgeIntegrationService;
    
    /**
     * Process a natural language query to generate test cases
     * 
     * @param request NLP query request
     * @return List of generated test cases
     */
    public List<TestCase> processQuery(NLPQueryRequest request) {
        logger.info("Processing NLP query: {}", request.getQuery());
        
        // Extract test target from the query
        TestTarget target = extractTestTarget(request);
        
        // Create a list to store generated tests
        List<TestCase> testCases = new ArrayList<>();
        
        // Process based on what we could extract
        if (target.methodSignature != null) {
            // We have a specific method to test
            logger.info("Generating tests for method: {}", target.methodSignature);
            TestCase testCase = generateTestForMethod(target.methodSignature, request);
            if (testCase != null) {
                testCases.add(testCase);
            }
        } else if (target.className != null) {
            // We have a class to test
            logger.info("Generating tests for class: {}", target.className);
            // Find all methods in the class
            List<MethodInfo> methodInfos = codeAnalysisService.findMethodsByClassName(target.className);
            
            // Generate tests for each method
            for (MethodInfo methodInfo : methodInfos) {
                TestCase testCase = generateTestForMethod(methodInfo.getSignature(), request);
                if (testCase != null) {
                    testCases.add(testCase);
                }
            }
        } else {
            // No specific target found, return error
            logger.error("Could not determine test target from query: {}", request.getQuery());
        }
        
        return testCases;
    }
    
    /**
     * Generate a test for a specific method
     * 
     * @param methodSignature Method signature
     * @param request NLP query request
     * @return Generated test case
     */
    private TestCase generateTestForMethod(String methodSignature, NLPQueryRequest request) {
        // Prepare knowledge sources
        Map<String, Path> knowledgeSources = new HashMap<>();
        
        // Add paths from the knowledge sources list
        if (request.getKnowledgeSources() != null) {
            for (KnowledgeSource source : request.getKnowledgeSources()) {
                if (source.isEnabled()) {
                    knowledgeSources.put(source.getType(), Path.of(source.getPath()));
                }
            }
        }
        
        // Get Atlassian credentials
        AtlassianCredentials credentials = request.getAtlassianCredentials();
        
        // Generate test with knowledge integration
        if (request.isUseAllAvailableKnowledge()) {
            // Use all knowledge sources including Atlassian data if provided
            String jiraProjectKey = extractJiraProjectKey(request);
            String confluenceSpaceKey = extractConfluenceSpaceKey(request);
            
            return knowledgeIntegrationService.integrateAllKnowledgeSources(
                methodSignature, knowledgeSources, credentials, jiraProjectKey, confluenceSpaceKey);
        } else {
            // Use only the file-based knowledge sources
            return knowledgeIntegrationService.integrateAllKnowledgeSources(methodSignature, knowledgeSources);
        }
    }
    
    /**
     * Extract test target information from the query
     * 
     * @param request NLP query request
     * @return TestTarget containing method and class information
     */
    private TestTarget extractTestTarget(NLPQueryRequest request) {
        TestTarget target = new TestTarget();
        
        // First, check if target class and method are explicitly provided
        if (request.getTargetClass() != null && !request.getTargetClass().isEmpty()) {
            target.className = request.getTargetClass();
        }
        
        if (request.getTargetMethod() != null && !request.getTargetMethod().isEmpty()) {
            target.methodName = request.getTargetMethod();
            // Try to construct a method signature if we have both class and method
            if (target.className != null) {
                MethodInfo methodInfo = codeAnalysisService.findMethodByClassAndName(target.className, target.methodName);
                if (methodInfo != null) {
                    target.methodSignature = methodInfo.getSignature();
                }
            }
            return target;
        }
        
        // If not explicitly provided, try to extract from the query
        String query = request.getQuery().toLowerCase();
        
        // Look for method references
        Pattern methodPattern = Pattern.compile("test(ing|s)? (the |for )?([a-zA-Z0-9_]+)\\s*(method|function)?");
        Matcher methodMatcher = methodPattern.matcher(query);
        if (methodMatcher.find()) {
            target.methodName = methodMatcher.group(3);
        }
        
        // Look for class references
        Pattern classPattern = Pattern.compile("(class|in|from) ([a-zA-Z0-9_]+)");
        Matcher classMatcher = classPattern.matcher(query);
        if (classMatcher.find()) {
            target.className = classMatcher.group(2);
        }
        
        // Look for full method signature with parameters
        Pattern signaturePattern = Pattern.compile("([a-zA-Z0-9_]+)\\s*\\(([^)]*?)\\)");
        Matcher signatureMatcher = signaturePattern.matcher(query);
        if (signatureMatcher.find()) {
            // This is a simplistic approach - in a real implementation,
            // you'd use more sophisticated NLP to identify method signatures
            String methodName = signatureMatcher.group(1);
            target.methodName = methodName;
            
            // Try to find exact method from code analysis
            if (target.className != null) {
                MethodInfo methodInfo = codeAnalysisService.findMethodByClassAndName(target.className, methodName);
                if (methodInfo != null) {
                    target.methodSignature = methodInfo.getSignature();
                }
            }
        }
        
        return target;
    }
    
    /**
     * Extract Jira project key from the request
     * 
     * @param request NLP query request
     * @return Jira project key or null
     */
    private String extractJiraProjectKey(NLPQueryRequest request) {
        // If we have Atlassian credentials, try to find a Jira project key
        if (request.getAtlassianCredentials() != null) {
            // Look in knowledge sources
            if (request.getKnowledgeSources() != null) {
                for (KnowledgeSource source : request.getKnowledgeSources()) {
                    if ("jira".equalsIgnoreCase(source.getType()) && source.isEnabled()) {
                        // Try to extract project key from path
                        String path = source.getPath();
                        if (path != null && path.contains("/projects/")) {
                            int index = path.indexOf("/projects/");
                            String subPath = path.substring(index + 10);
                            if (subPath.contains("/")) {
                                return subPath.substring(0, subPath.indexOf("/"));
                            } else {
                                return subPath;
                            }
                        }
                    }
                }
            }
            
            // As a fallback, look for project key in the query
            String query = request.getQuery();
            Pattern pattern = Pattern.compile("project[:\\s]+(\\w+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(query);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        return null;
    }
    
    /**
     * Extract Confluence space key from the request
     * 
     * @param request NLP query request
     * @return Confluence space key or null
     */
    private String extractConfluenceSpaceKey(NLPQueryRequest request) {
        // If we have Atlassian credentials, try to find a Confluence space key
        if (request.getAtlassianCredentials() != null) {
            // Look in knowledge sources
            if (request.getKnowledgeSources() != null) {
                for (KnowledgeSource source : request.getKnowledgeSources()) {
                    if ("confluence".equalsIgnoreCase(source.getType()) && source.isEnabled()) {
                        // Try to extract space key from path
                        String path = source.getPath();
                        if (path != null && path.contains("/spaces/")) {
                            int index = path.indexOf("/spaces/");
                            String subPath = path.substring(index + 8);
                            if (subPath.contains("/")) {
                                return subPath.substring(0, subPath.indexOf("/"));
                            } else {
                                return subPath;
                            }
                        }
                    }
                }
            }
            
            // As a fallback, look for space key in the query
            String query = request.getQuery();
            Pattern pattern = Pattern.compile("space[:\\s]+(\\w+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(query);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        return null;
    }
    
    /**
     * Helper class to store test target information
     */
    private static class TestTarget {
        String className;
        String methodName;
        String methodSignature;
    }
}