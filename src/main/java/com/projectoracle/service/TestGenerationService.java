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

        // Generate test code using AI
        String generatedTestCode = aiModelService.generateText(prompt, 500);

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
                       .confidenceScore(0.8)
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

        // Add instructions for test generation
        promptBuilder.append("\nCreate a comprehensive JUnit 5 test that:");
        promptBuilder.append("\n1. Tests the main functionality of the method");
        promptBuilder.append("\n2. Includes appropriate assertions");
        promptBuilder.append("\n3. Handles edge cases");
        promptBuilder.append("\n4. Uses mocks where appropriate");
        promptBuilder.append("\n5. Has good test method names following the convention testMethodName_scenario_expectedBehavior");

        return promptBuilder.toString();
    }
}