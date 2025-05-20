package com.projectoracle.service;

import com.projectoracle.model.TestCase;
import com.projectoracle.model.TestSuggestion;
import com.projectoracle.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for suggesting additional test cases based on code analysis and existing tests.
 * Leverages AI to identify potential edge cases and untested scenarios.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TestSuggestionService {

    private final AIModelService aiModelService;
    private final CodeAnalysisService codeAnalysisService;
    private final TestCaseRepository testCaseRepository;

    /**
     * Generate test suggestions for a specific class
     *
     * @param className the fully qualified name of the class
     * @return a list of test suggestions
     */
    public List<TestSuggestion> suggestTestsForClass(String className) {
        log.info("Generating test suggestions for class: {}", className);

        try {
            // Get existing tests for this class
            List<TestCase> existingTests = testCaseRepository.findByClassName("Test" + getSimpleClassName(className));

            // Extract methods information
            Map<String, MethodInfo> methodInfos = getMethodInfosForClass(className);

            if (methodInfos.isEmpty()) {
                log.warn("No methods found for class: {}", className);
                return Collections.emptyList();
            }

            // Generate suggestions for each method
            List<TestSuggestion> suggestions = new ArrayList<>();

            for (Map.Entry<String, MethodInfo> entry : methodInfos.entrySet()) {
                String methodName = entry.getKey();
                MethodInfo methodInfo = entry.getValue();

                // Skip methods that already have comprehensive tests
                if (hasComprehensiveTests(methodName, existingTests)) {
                    continue;
                }

                List<TestSuggestion> methodSuggestions = generateSuggestionsForMethod(methodInfo, existingTests);
                suggestions.addAll(methodSuggestions);
            }

            log.info("Generated {} test suggestions for class: {}", suggestions.size(), className);
            return suggestions;
        } catch (Exception e) {
            log.error("Error generating test suggestions for class: {}", className, e);
            return Collections.emptyList();
        }
    }

    /**
     * Generate suggestions for uncovered code paths and edge cases
     *
     * @param className the fully qualified name of the class
     * @return a list of edge case test suggestions
     */
    public List<TestSuggestion> suggestEdgeCaseTests(String className) {
        log.info("Generating edge case test suggestions for class: {}", className);

        try {
            // Get existing tests for this class
            List<TestCase> existingTests = testCaseRepository.findByClassName("Test" + getSimpleClassName(className));

            // Get method information
            Map<String, MethodInfo> methodInfos = getMethodInfosForClass(className);

            if (methodInfos.isEmpty()) {
                return Collections.emptyList();
            }

            // Generate edge case suggestions
            return methodInfos.values().stream()
                              .flatMap(methodInfo -> generateEdgeCaseSuggestions(methodInfo, existingTests).stream())
                              .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating edge case test suggestions for class: {}", className, e);
            return Collections.emptyList();
        }
    }

    /**
     * Generate suggestions for methods that don't have any tests
     *
     * @return a list of untested methods across all analyzed code
     */
    public List<MethodInfo> findUntestedMethods() {
        log.info("Finding untested methods");

        try {
            // Get all test cases
            List<TestCase> allTests = testCaseRepository.findAll();

            // Extract all tested methods
            Set<String> testedMethods = new HashSet<>();
            for (TestCase test : allTests) {
                String className = test.getClassName().replace("Test", "");
                String methodName = extractMethodNameFromTestName(test.getMethodName());

                testedMethods.add(className + "." + methodName);
            }

            // Analyze all methods to find untested ones
            List<MethodInfo> allMethods = codeAnalysisService.scanDirectory(null);

            return allMethods.stream()
                             .filter(method -> !testedMethods.contains(method.getClassName() + "." + method.getMethodName()))
                             .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error finding untested methods", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get method information for a specific class
     */
    private Map<String, MethodInfo> getMethodInfosForClass(String className) {
        try {
            // This would typically analyze a file, but we'll use a more direct approach
            // for the demonstration
            return codeAnalysisService.analyzeJavaSource("");
        } catch (Exception e) {
            log.error("Error getting method information for class: {}", className, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Check if a method already has comprehensive tests
     */
    private boolean hasComprehensiveTests(String methodName, List<TestCase> existingTests) {
        // Count tests for this method
        long testCount = existingTests.stream()
                                      .filter(test -> extractMethodNameFromTestName(test.getMethodName()).equals(methodName))
                                      .count();

        // Consider having 3 or more tests as comprehensive
        return testCount >= 3;
    }

    /**
     * Extract method name from test method name
     */
    private String extractMethodNameFromTestName(String testMethodName) {
        if (testMethodName.startsWith("test")) {
            // Remove "test" prefix and extract method name
            String nameWithoutPrefix = testMethodName.substring(4);

            // Handle various test naming patterns
            int underscoreIndex = nameWithoutPrefix.indexOf('_');
            if (underscoreIndex > 0) {
                // For names like "testMethod_scenario"
                return lcFirst(nameWithoutPrefix.substring(0, underscoreIndex));
            } else {
                // For names like "testMethod"
                return lcFirst(nameWithoutPrefix);
            }
        }

        return testMethodName;
    }

    /**
     * Convert first letter to lowercase
     */
    private String lcFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * Generate suggestions for a specific method
     */
    private List<TestSuggestion> generateSuggestionsForMethod(MethodInfo methodInfo, List<TestCase> existingTests) {
        List<TestSuggestion> suggestions = new ArrayList<>();

        // Extract existing test scenarios for this method
        List<String> existingScenarios = existingTests.stream()
                                                      .filter(test -> {
                                                          String testMethodName = test.getMethodName();
                                                          String targetMethod = extractMethodNameFromTestName(testMethodName);
                                                          return targetMethod.equals(methodInfo.getMethodName());
                                                      })
                                                      .map(test -> {
                                                          String testMethodName = test.getMethodName();
                                                          int underscoreIndex = testMethodName.indexOf('_');
                                                          if (underscoreIndex > 0 && underscoreIndex < testMethodName.length() - 1) {
                                                              return testMethodName.substring(underscoreIndex + 1);
                                                          }
                                                          return "";
                                                      })
                                                      .filter(scenario -> !scenario.isEmpty())
                                                      .collect(Collectors.toList());

        // Generate test suggestions using AI
        String prompt = buildPromptForSuggestions(methodInfo, existingScenarios);
        String suggestionText = aiModelService.generateText(prompt, 1000);

        // Parse the suggestions
        List<TestSuggestion> parsedSuggestions = parseSuggestions(suggestionText, methodInfo);
        suggestions.addAll(parsedSuggestions);

        return suggestions;
    }

    /**
     * Build a prompt for the AI to generate test suggestions
     */
    private String buildPromptForSuggestions(MethodInfo methodInfo, List<String> existingScenarios) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("Generate test case suggestions for the following Java method:\n\n");

        // Add method details
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

        // Add existing test scenarios
        if (!existingScenarios.isEmpty()) {
            promptBuilder.append("Existing test scenarios:\n");
            for (String scenario : existingScenarios) {
                promptBuilder.append("- ").append(scenario).append("\n");
            }
            promptBuilder.append("\n");
        }

        // Add request for suggestions
        promptBuilder.append("Based on the method above, suggest 3-5 test cases that would provide good coverage ");
        promptBuilder.append("including edge cases and scenarios not covered by the existing tests.\n\n");
        promptBuilder.append("For each suggestion, provide:\n");
        promptBuilder.append("1. A short name/description for the test\n");
        promptBuilder.append("2. Input values for parameters\n");
        promptBuilder.append("3. Expected output/behavior\n");
        promptBuilder.append("4. The category (normal case, edge case, error case, performance case)\n\n");
        promptBuilder.append("Format each suggestion as follows:\n");
        promptBuilder.append("TEST NAME: [name]\n");
        promptBuilder.append("INPUTS: [inputs]\n");
        promptBuilder.append("EXPECTED: [expected]\n");
        promptBuilder.append("CATEGORY: [category]\n");

        return promptBuilder.toString();
    }

    /**
     * Parse the AI-generated suggestions into TestSuggestion objects
     */
    private List<TestSuggestion> parseSuggestions(String suggestionText, MethodInfo methodInfo) {
        List<TestSuggestion> suggestions = new ArrayList<>();

        // Split by test entries
        String[] testEntries = suggestionText.split("TEST NAME:");

        // Skip the first entry if it doesn't contain a test
        for (int i = 1; i < testEntries.length; i++) {
            String entry = testEntries[i].trim();

            // Parse test name
            String name = "";
            int nameEndIndex = entry.indexOf('\n');
            if (nameEndIndex > 0) {
                name = entry.substring(0, nameEndIndex).trim();
            }

            // Parse inputs
            String inputs = extractSection(entry, "INPUTS:");

            // Parse expected output
            String expected = extractSection(entry, "EXPECTED:");

            // Parse category
            String category = extractSection(entry, "CATEGORY:");

            // Create suggestion
            if (!name.isEmpty()) {
                TestSuggestion suggestion = new TestSuggestion();
                suggestion.setMethodInfo(methodInfo);
                suggestion.setTestName(name);
                suggestion.setInputValues(inputs);
                suggestion.setExpectedOutput(expected);
                suggestion.setCategory(category);
                suggestion.setCreatedAt(LocalDateTime.now());

                suggestions.add(suggestion);
            }
        }

        return suggestions;
    }

    /**
     * Extract a section from the suggestion text
     */
    private String extractSection(String text, String sectionKey) {
        int startIndex = text.indexOf(sectionKey);
        if (startIndex < 0) {
            return "";
        }

        startIndex += sectionKey.length();
        int endIndex = text.indexOf('\n', startIndex);

        if (endIndex < 0) {
            return text.substring(startIndex).trim();
        } else {
            String section = text.substring(startIndex, endIndex).trim();

            // Check if there are multiple lines (indented)
            int nextSectionIndex = text.indexOf(":", endIndex);
            if (nextSectionIndex > 0) {
                int multilineEndIndex = text.lastIndexOf('\n', nextSectionIndex);
                if (multilineEndIndex > endIndex) {
                    section = text.substring(startIndex, multilineEndIndex).trim();
                }
            }

            return section;
        }
    }

    /**
     * Generate edge case suggestions for a method
     */
    private List<TestSuggestion> generateEdgeCaseSuggestions(MethodInfo methodInfo, List<TestCase> existingTests) {
        // Build a prompt specifically for edge cases
        String prompt = buildPromptForEdgeCases(methodInfo);
        String suggestionText = aiModelService.generateText(prompt, 800);

        // Parse the suggestions
        return parseSuggestions(suggestionText, methodInfo);
    }

    /**
     * Build a prompt for edge case suggestions
     */
    private String buildPromptForEdgeCases(MethodInfo methodInfo) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("Generate edge case test suggestions for the following Java method:\n\n");

        // Add method details
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

        // Add request for edge cases
        promptBuilder.append("Suggest 3-5 edge case tests for this method. Focus on boundary values, ");
        promptBuilder.append("null inputs, empty collections, extreme values, error conditions, and other edge scenarios.\n\n");
        promptBuilder.append("For each suggestion, provide:\n");
        promptBuilder.append("1. A short name/description for the test\n");
        promptBuilder.append("2. Input values for parameters\n");
        promptBuilder.append("3. Expected output/behavior\n");
        promptBuilder.append("4. The category (always 'edge case')\n\n");
        promptBuilder.append("Format each suggestion as follows:\n");
        promptBuilder.append("TEST NAME: [name]\n");
        promptBuilder.append("INPUTS: [inputs]\n");
        promptBuilder.append("EXPECTED: [expected]\n");
        promptBuilder.append("CATEGORY: edge case\n");

        return promptBuilder.toString();
    }

    /**
     * Extract the simple class name from a fully qualified name
     */
    private String getSimpleClassName(String fullyQualifiedName) {
        int lastDotIndex = fullyQualifiedName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fullyQualifiedName.substring(lastDotIndex + 1);
        }
        return fullyQualifiedName;
    }
}