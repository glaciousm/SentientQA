package com.projectoracle.service;

import com.projectoracle.model.TestCase;
import com.projectoracle.model.TestSuggestion;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.repository.TestSuggestionRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for implementing test cases from suggestions.
 * Converts test suggestions into executable test cases.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TestImplementationService {

    private final TestSuggestionRepository suggestionRepository;
    private final TestCaseRepository testCaseRepository;
    private final AIModelService aiModelService;

    /**
     * Implement a test case from a suggestion
     *
     * @param suggestionId the ID of the suggestion to implement
     * @return the implemented test case
     */
    public TestCase implementSuggestion(UUID suggestionId) {
        log.info("Implementing test case from suggestion: {}", suggestionId);

        // Get the suggestion
        TestSuggestion suggestion = suggestionRepository.findById(suggestionId);
        if (suggestion == null) {
            log.warn("Suggestion not found: {}", suggestionId);
            return null;
        }

        // Generate test code
        String testCode = generateImplementedTestCode(suggestion);

        // Create test case
        TestCase testCase = TestCase.builder()
                                    .id(UUID.randomUUID())
                                    .name(suggestion.getTestName())
                                    .description("Implemented from suggestion: " + suggestion.getTestName())
                                    .type(TestCase.TestType.UNIT)
                                    .priority(TestCase.TestPriority.MEDIUM)
                                    .status(TestCase.TestStatus.GENERATED)
                                    .packageName(suggestion.getMethodInfo().getPackageName() + ".test")
                                    .className("Test" + suggestion.getMethodInfo().getClassName())
                                    .methodName(suggestion.generateTestMethodName())
                                    .sourceCode(testCode)
                                    .createdAt(LocalDateTime.now())
                                    .modifiedAt(LocalDateTime.now())
                                    .confidenceScore(0.9)
                                    .build();

        // Save the test case
        TestCase savedTestCase = testCaseRepository.save(testCase);

        // Mark suggestion as implemented
        suggestionRepository.markAsImplemented(suggestionId, savedTestCase.getId());

        log.info("Implemented test case from suggestion: {}", suggestionId);
        return savedTestCase;
    }

    /**
     * Generate a fully implemented test case code
     */
    private String generateImplementedTestCode(TestSuggestion suggestion) {
        MethodInfo methodInfo = suggestion.getMethodInfo();

        // First, get a skeleton test case
        String skeletonCode = suggestion.generateTestCode();

        // Build a prompt for the AI model to complete the implementation
        String prompt = buildCompletionPrompt(methodInfo, suggestion, skeletonCode);

        // Generate completed test code
        return aiModelService.generateText(prompt, 2000);
    }

    /**
     * Build a prompt for the AI to complete the test implementation
     */
    private String buildCompletionPrompt(MethodInfo methodInfo, TestSuggestion suggestion, String skeletonCode) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("Complete the following JUnit 5 test implementation based on the method information and test suggestion:\n\n");

        // Add method details
        promptBuilder.append("Method Information:\n");
        promptBuilder.append("Package: ").append(methodInfo.getPackageName()).append("\n");
        promptBuilder.append("Class: ").append(methodInfo.getClassName()).append("\n");
        promptBuilder.append("Method: ").append(methodInfo.getMethodName()).append("\n");
        promptBuilder.append("Signature: ").append(methodInfo.getSignature()).append("\n");
        promptBuilder.append("Return type: ").append(methodInfo.getReturnType()).append("\n\n");

        // Add parameters
        if (methodInfo.getParameters() != null && !methodInfo.getParameters().isEmpty()) {
            promptBuilder.append("Parameters:\n");
            for (ParameterInfo param : methodInfo.getParameters()) {
                promptBuilder.append("- ").append(param.getType()).append(" ").append(param.getName()).append("\n");
            }
            promptBuilder.append("\n");
        }

        // Add method body if available
        if (methodInfo.getBody() != null && !methodInfo.getBody().isEmpty()) {
            promptBuilder.append("Method body:\n").append(methodInfo.getBody()).append("\n\n");
        }

        // Add test suggestion details
        promptBuilder.append("Test Suggestion:\n");
        promptBuilder.append("Test Name: ").append(suggestion.getTestName()).append("\n");
        promptBuilder.append("Input Values: ").append(suggestion.getInputValues()).append("\n");
        promptBuilder.append("Expected Output: ").append(suggestion.getExpectedOutput()).append("\n");
        promptBuilder.append("Category: ").append(suggestion.getCategory()).append("\n\n");

        // Add skeleton code
        promptBuilder.append("Skeleton Test Code:\n");
        promptBuilder.append(skeletonCode).append("\n\n");

        // Add instructions for completion
        promptBuilder.append("Please complete the test implementation by:\n");
        promptBuilder.append("1. Adding appropriate parameter values based on the 'Input Values' section\n");
        promptBuilder.append("2. Adding proper assertions based on the 'Expected Output' section\n");
        promptBuilder.append("3. Adding any necessary setup/teardown code\n");
        promptBuilder.append("4. Making sure the test matches the category (").append(suggestion.getCategory()).append(")\n\n");

        promptBuilder.append("Return only the completed test code with no additional explanations or comments.");

        return promptBuilder.toString();
    }

    /**
     * Implement all unimplemented suggestions for a class
     *
     * @param className the class name
     * @return the number of implemented tests
     */
    public int implementAllSuggestionsForClass(String className) {
        log.info("Implementing all suggestions for class: {}", className);

        // Get all unimplemented suggestions for this class
        List<TestSuggestion> suggestions = suggestionRepository.findByClassName(className).stream()
                                                               .filter(s -> !s.isImplemented())
                                                               .toList();

        int implementedCount = 0;

        // Implement each suggestion
        for (TestSuggestion suggestion : suggestions) {
            try {
                TestCase testCase = implementSuggestion(suggestion.getId());
                if (testCase != null) {
                    implementedCount++;
                }
            } catch (Exception e) {
                log.error("Error implementing suggestion: {}", suggestion.getId(), e);
            }
        }

        log.info("Implemented {} test cases for class: {}", implementedCount, className);
        return implementedCount;
    }

    /**
     * Get statistics about implemented and unimplemented suggestions
     *
     * @return implementation statistics
     */
    public ImplementationStats getImplementationStats() {
        ImplementationStats stats = new ImplementationStats();

        stats.setTotalSuggestions(suggestionRepository.count());
        stats.setImplementedSuggestions(suggestionRepository.countImplemented());
        stats.setUnimplementedSuggestions(suggestionRepository.countUnimplemented());

        // Calculate implementation rate
        if (stats.getTotalSuggestions() > 0) {
            stats.setImplementationRate((double) stats.getImplementedSuggestions() / stats.getTotalSuggestions());
        } else {
            stats.setImplementationRate(0.0);
        }

        return stats;
    }

    /**
     * Statistics class for test implementation
     */
    @Data
    public static class ImplementationStats {
        private int totalSuggestions;
        private int implementedSuggestions;
        private int unimplementedSuggestions;
        private double implementationRate;
    }
}