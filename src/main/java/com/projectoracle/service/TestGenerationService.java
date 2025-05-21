package com.projectoracle.service;

import com.projectoracle.model.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for generating test cases using AI models.
 * Orchestrates the test generation process by integrating
 * code analysis with AI-powered test creation.
 */
@Service
public class TestGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(TestGenerationService.class);

    @Autowired
    private AIModelService aiModelService;

    @Autowired
    private CodeAnalysisService codeAnalysisService;
    
    @Autowired
    private RuleBasedFallbackService fallbackService;
    
    @Autowired
    private com.projectoracle.config.AIConfig aiConfig;

    /**
     * Generate a test case for a specific method
     *
     * @param methodInfo information about the method to test
     * @return the generated test case
     */
    public TestCase generateTestForMethod(MethodInfo methodInfo) {
        logger.info("Generating test for method: {}", methodInfo.getSignature());

        // Construct a prompt for the AI model
        String prompt = buildPromptForMethod(methodInfo);

        // Generate test code using AI with fallback to rule-based generation
        String generatedTestCode;
        try {
            // Try using AI first
            generatedTestCode = aiModelService.generateText(prompt, 500);
            
            // Check if we got an error message instead of actual code
            if (generatedTestCode.startsWith("Error") || generatedTestCode.startsWith("AI model")) {
                logger.warn("AI model returned an error, falling back to rule-based generation");
                // If AI failed and fallback is enabled, use rule-based generation
                if (aiConfig.isFallbackToRuleBased()) {
                    generatedTestCode = fallbackService.generateTestForMethod(methodInfo.getSignature(), methodInfo.getClassName());
                }
            }
        } catch (Exception e) {
            logger.error("Error using AI model for test generation: {}", e.getMessage());
            
            // If fallback is enabled, use rule-based generation
            if (aiConfig.isFallbackToRuleBased()) {
                logger.info("Falling back to rule-based test generation");
                generatedTestCode = fallbackService.generateTestForMethod(methodInfo.getSignature(), methodInfo.getClassName());
            } else {
                // If fallback is disabled, just return a placeholder
                generatedTestCode = "// Error generating test: " + e.getMessage();
            }
        }
        
        // Calculate confidence score
        double confidenceScore = 0.8;
        double knowledgeEnhancementScore = 0.0;
        
        // If we used fallback generation, confidence is lower
        if (generatedTestCode.contains("// This is a generic test created as a fallback")) {
            logger.info("Using fallback generation with lower confidence score");
            confidenceScore = 0.4; // Lower confidence for rule-based generation
        }
        
        // If additional context was provided, boost the confidence score
        if (methodInfo.getAdditionalContext() != null && !methodInfo.getAdditionalContext().isEmpty()) {
            knowledgeEnhancementScore = 0.15; // 15% improvement from knowledge integration
            confidenceScore += knowledgeEnhancementScore;
            if (confidenceScore > 1.0) {
                confidenceScore = 1.0;
            }
            logger.info("Knowledge integration improved confidence score by {}", knowledgeEnhancementScore);
        }
        
        // Extract knowledge sources from methodInfo
        List<com.projectoracle.model.KnowledgeSource> knowledgeSources = new ArrayList<>();
        if (methodInfo.getAdditionalContext() != null) {
            if (methodInfo.getAdditionalContext().containsKey("apiDoc")) {
                knowledgeSources.add(new com.projectoracle.model.KnowledgeSource(
                    "api", 
                    methodInfo.getAdditionalContext().get("path") != null ? 
                        methodInfo.getAdditionalContext().get("path").toString() : "",
                    "swagger", 
                    true
                ));
            }
            
            if (methodInfo.getAdditionalContext().containsKey("projectDoc")) {
                knowledgeSources.add(new com.projectoracle.model.KnowledgeSource(
                    "docs", 
                    methodInfo.getAdditionalContext().get("path") != null ? 
                        methodInfo.getAdditionalContext().get("path").toString() : "",
                    "markdown", 
                    true
                ));
            }
            
            if (methodInfo.getAdditionalContext().containsKey("testHistory")) {
                knowledgeSources.add(new com.projectoracle.model.KnowledgeSource(
                    "history", 
                    methodInfo.getAdditionalContext().get("path") != null ? 
                        methodInfo.getAdditionalContext().get("path").toString() : "",
                    "junit", 
                    true
                ));
            }
            
            if (methodInfo.getAdditionalContext().containsKey("codeComments")) {
                knowledgeSources.add(new com.projectoracle.model.KnowledgeSource(
                    "source", 
                    methodInfo.getAdditionalContext().get("path") != null ? 
                        methodInfo.getAdditionalContext().get("path").toString() : "",
                    "java", 
                    true
                ));
            }
        }

        // Create and return a test case
        return TestCase.builder()
                       .id(UUID.randomUUID())
                       .name("Test" + methodInfo.getMethodName())
                       .description("Test for " + methodInfo.getSignature())
                       .type(TestCase.TestType.UNIT)
                       .priority(TestCase.TestPriority.MEDIUM)
                       .status(TestCase.TestStatus.GENERATED)
                       .packageName(methodInfo.getPackageName())
                       .className("Test" + methodInfo.getClassName())
                       .methodName("test" + methodInfo.getMethodName())
                       .sourceCode(generatedTestCode)
                       .assertions(extractAssertions(generatedTestCode))
                       .createdAt(LocalDateTime.now())
                       .modifiedAt(LocalDateTime.now())
                       .confidenceScore(confidenceScore)
                       .knowledgeEnhancementScore(knowledgeEnhancementScore)
                       .knowledgeSources(knowledgeSources.isEmpty() ? null : knowledgeSources)
                       .generationPrompt(prompt)
                       .build();
    }

    /**
     * Extract assertions from generated test code
     * This is a simple implementation that looks for assert statements
     */
    private List<String> extractAssertions(String testCode) {
        List<String> assertions = new ArrayList<>();
        if (testCode == null || testCode.isEmpty()) {
            return assertions;
        }

        // Split the code into lines
        String[] lines = testCode.split("\n");
        for (String line : lines) {
            line = line.trim();
            // Look for assertions (simple implementation)
            if (line.contains("assert") && !line.startsWith("//") && !line.startsWith("*")) {
                assertions.add(line);
            }
        }

        return assertions;
    }

    /**
     * Build a prompt for the AI model to generate a test
     *
     * @param methodInfo information about the method to test
     * @return prompt for the AI model
     */
    private String buildPromptForMethod(MethodInfo methodInfo) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("Generate a JUnit 5 test for the following Java method:\n\n");

        // Add method signature and details
        promptBuilder.append("Package: ").append(methodInfo.getPackageName()).append("\n");
        promptBuilder.append("Class: ").append(methodInfo.getClassName()).append("\n");
        promptBuilder.append("Method signature: ").append(methodInfo.getSignature()).append("\n");
        promptBuilder.append("Return type: ").append(methodInfo.getReturnType()).append("\n");

        // Add parameters
        if (methodInfo.getParameters() != null && !methodInfo.getParameters().isEmpty()) {
            promptBuilder.append("Parameters:\n");
            for (ParameterInfo param : methodInfo.getParameters()) {
                promptBuilder.append("- ").append(param.getType()).append(" ").append(param.getName()).append("\n");
            }
        }

        // Add exceptions
        if (methodInfo.getExceptions() != null && !methodInfo.getExceptions().isEmpty()) {
            promptBuilder.append("Throws:\n");
            for (String exception : methodInfo.getExceptions()) {
                promptBuilder.append("- ").append(exception).append("\n");
            }
        }

        // Add Javadoc if available
        if (methodInfo.getJavadoc() != null && !methodInfo.getJavadoc().isEmpty()) {
            promptBuilder.append("\nJavadoc:\n").append(methodInfo.getJavadoc()).append("\n");
        }

        // Add method body if available
        if (methodInfo.getBody() != null && !methodInfo.getBody().isEmpty()) {
            promptBuilder.append("\nMethod body:\n").append(methodInfo.getBody()).append("\n");
        }
        
        // Include additional context from knowledge integration if available
        if (methodInfo.getAdditionalContext() != null && !methodInfo.getAdditionalContext().isEmpty()) {
            promptBuilder.append("\nAdditional Context from Knowledge Integration:\n");
            
            // Include API documentation information
            if (methodInfo.getAdditionalContext().containsKey("apiDoc")) {
                promptBuilder.append("\nAPI Documentation:\n");
                Object apiDoc = methodInfo.getAdditionalContext().get("apiDoc");
                promptBuilder.append(apiDoc.toString()).append("\n");
            }
            
            // Include project documentation information
            if (methodInfo.getAdditionalContext().containsKey("projectDoc")) {
                promptBuilder.append("\nProject Documentation:\n");
                Object projectDoc = methodInfo.getAdditionalContext().get("projectDoc");
                promptBuilder.append(projectDoc.toString()).append("\n");
            }
            
            // Include historical test data
            if (methodInfo.getAdditionalContext().containsKey("testHistory")) {
                promptBuilder.append("\nHistorical Test Patterns:\n");
                Object testHistory = methodInfo.getAdditionalContext().get("testHistory");
                promptBuilder.append(testHistory.toString()).append("\n");
            }
            
            // Include code comments
            if (methodInfo.getAdditionalContext().containsKey("codeComments")) {
                promptBuilder.append("\nCode Comments:\n");
                Object codeComments = methodInfo.getAdditionalContext().get("codeComments");
                promptBuilder.append(codeComments.toString()).append("\n");
            }
        }

        // Add instructions for test generation
        promptBuilder.append("\nCreate a comprehensive JUnit 5 test that:");
        promptBuilder.append("\n1. Tests the main functionality of the method");
        promptBuilder.append("\n2. Includes appropriate assertions");
        promptBuilder.append("\n3. Handles edge cases");
        promptBuilder.append("\n4. Uses mocks where appropriate");
        promptBuilder.append("\n5. Has good test method names following the convention testMethodName_scenario_expectedBehavior");
        
        // Add additional instructions based on knowledge integration
        if (methodInfo.getAdditionalContext() != null && !methodInfo.getAdditionalContext().isEmpty()) {
            promptBuilder.append("\n6. Incorporates insights from the additional context provided");
            promptBuilder.append("\n7. Uses realistic test data based on the documentation");
            promptBuilder.append("\n8. Follows established patterns from historical tests");
            promptBuilder.append("\n9. Addresses all requirements mentioned in the documentation");
        }

        return promptBuilder.toString();
    }
}