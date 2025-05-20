package com.projectoracle.service;

import com.projectoracle.model.TestCase;
import com.projectoracle.model.TestSuggestion;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.repository.TestSuggestionRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for prioritizing test suggestions based on various factors.
 * Helps determine which test cases should be implemented first.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TestPrioritizationService {

    private final TestSuggestionRepository suggestionRepository;
    private final TestCaseRepository testCaseRepository;

    /**
     * Prioritize test suggestions for a class
     *
     * @param className the class name
     * @return a list of prioritized test suggestions
     */
    public List<PrioritizedSuggestion> prioritizeSuggestions(String className) {
        log.info("Prioritizing test suggestions for class: {}", className);

        // Get all unimplemented suggestions for this class
        List<TestSuggestion> suggestions = suggestionRepository.findByClassName(className).stream()
                                                               .filter(s -> !s.isImplemented())
                                                               .collect(Collectors.toList());

        // Get existing tests for this class
        List<TestCase> existingTests = testCaseRepository.findByClassName("Test" + className);

        // Calculate priorities
        List<PrioritizedSuggestion> prioritizedSuggestions = new ArrayList<>();

        for (TestSuggestion suggestion : suggestions) {
            double priority = calculatePriority(suggestion, existingTests);

            PrioritizedSuggestion prioritized = new PrioritizedSuggestion();
            prioritized.setSuggestion(suggestion);
            prioritized.setPriorityScore(priority);
            prioritized.setPriorityFactors(calculatePriorityFactors(suggestion, existingTests));

            prioritizedSuggestions.add(prioritized);
        }

        // Sort by priority (highest first)
        prioritizedSuggestions.sort(Comparator.comparing(PrioritizedSuggestion::getPriorityScore).reversed());

        return prioritizedSuggestions;
    }

    /**
     * Calculate priority score for a suggestion
     */
    private double calculatePriority(TestSuggestion suggestion, List<TestCase> existingTests) {
        double score = 0.0;

        // Base priority by category
        switch (suggestion.getCategory().toLowerCase()) {
            case "edge case":
                score += 3.0;
                break;
            case "error case":
                score += 4.0;
                break;
            case "performance case":
                score += 2.0;
                break;
            case "normal case":
            default:
                score += 1.0;
                break;
        }

        // Adjust priority based on method complexity
        MethodInfo methodInfo = suggestion.getMethodInfo();
        if (methodInfo != null && methodInfo.getBody() != null) {
            // Simple complexity heuristic based on number of lines, conditionals, and loops
            String body = methodInfo.getBody();
            int lines = body.split("\n").length;
            int conditionals = countOccurrences(body, "if") + countOccurrences(body, "switch");
            int loops = countOccurrences(body, "for") + countOccurrences(body, "while");

            double complexity = Math.min(5.0, (lines / 10.0) + conditionals + loops);
            score += complexity;
        }

        // Adjust priority based on existing test coverage
        String methodName = methodInfo != null ? methodInfo.getMethodName() : "";
        long existingTestCount = existingTests.stream()
                                              .filter(t -> t.getMethodName().contains(methodName))
                                              .count();

        // Higher priority for methods with fewer tests
        score += Math.max(0, 3.0 - existingTestCount);

        return score;
    }

    /**
     * Calculate detailed priority factors
     */
    private Map<String, Object> calculatePriorityFactors(TestSuggestion suggestion, List<TestCase> existingTests) {
        Map<String, Object> factors = new HashMap<>();

        // Category factor
        String category = suggestion.getCategory().toLowerCase();
        double categoryScore = 0.0;
        switch (category) {
            case "edge case":
                categoryScore = 3.0;
                break;
            case "error case":
                categoryScore = 4.0;
                break;
            case "performance case":
                categoryScore = 2.0;
                break;
            case "normal case":
            default:
                categoryScore = 1.0;
                break;
        }
        factors.put("category", category);
        factors.put("categoryScore", categoryScore);

        // Method complexity factor
        MethodInfo methodInfo = suggestion.getMethodInfo();
        double complexityScore = 0.0;
        if (methodInfo != null && methodInfo.getBody() != null) {
            String body = methodInfo.getBody();
            int lines = body.split("\n").length;
            int conditionals = countOccurrences(body, "if") + countOccurrences(body, "switch");
            int loops = countOccurrences(body, "for") + countOccurrences(body, "while");

            complexityScore = Math.min(5.0, (lines / 10.0) + conditionals + loops);

            factors.put("methodLines", lines);
            factors.put("conditionals", conditionals);
            factors.put("loops", loops);
        }
        factors.put("complexityScore", complexityScore);

        // Existing coverage factor
        String methodName = methodInfo != null ? methodInfo.getMethodName() : "";
        long existingTestCount = existingTests.stream()
                                              .filter(t -> t.getMethodName().contains(methodName))
                                              .count();
        double coverageScore = Math.max(0, 3.0 - existingTestCount);

        factors.put("existingTests", existingTestCount);
        factors.put("coverageScore", coverageScore);

        // Total score
        factors.put("totalScore", categoryScore + complexityScore + coverageScore);

        return factors;
    }

    /**
     * Count occurrences of a substring in a string
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;

        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }

        return count;
    }

    /**
     * Get priority statistics across all suggestions
     */
    public PriorityStats getPriorityStats() {
        List<TestSuggestion> allSuggestions = suggestionRepository.findAll();
        List<TestCase> allTests = testCaseRepository.findAll();

        PriorityStats stats = new PriorityStats();

        // Count by category
        Map<String, Integer> categoryCounts = new HashMap<>();
        for (TestSuggestion suggestion : allSuggestions) {
            String category = suggestion.getCategory().toLowerCase();
            categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
        }
        stats.setCategoryCounts(categoryCounts);

        // Count methods with no tests
        Set<String> testedMethods = new HashSet<>();
        for (TestCase test : allTests) {
            String className = test.getClassName().replace("Test", "");
            String methodName = extractMethodNameFromTestName(test.getMethodName());

            testedMethods.add(className + "." + methodName);
        }

        Set<String> allMethods = new HashSet<>();
        for (TestSuggestion suggestion : allSuggestions) {
            if (suggestion.getMethodInfo() != null) {
                String methodKey = suggestion.getMethodInfo().getClassName() + "." +
                        suggestion.getMethodInfo().getMethodName();
                allMethods.add(methodKey);
            }
        }

        allMethods.removeAll(testedMethods);
        stats.setUntestedMethodCount(allMethods.size());

        // Count high priority suggestions
        int highPriorityCount = 0;
        for (TestSuggestion suggestion : allSuggestions) {
            if (!suggestion.isImplemented()) {
                List<TestCase> classTests = testCaseRepository.findByClassName(
                        "Test" + suggestion.getMethodInfo().getClassName());
                double priority = calculatePriority(suggestion, classTests);

                if (priority >= 5.0) {
                    highPriorityCount++;
                }
            }
        }
        stats.setHighPrioritySuggestionCount(highPriorityCount);

        return stats;
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
     * Prioritized suggestion with priority score and factors
     */
    @Data
    public static class PrioritizedSuggestion {
        private TestSuggestion suggestion;
        private double priorityScore;
        private Map<String, Object> priorityFactors;
    }

    /**
     * Priority statistics across all suggestions
     */
    @Data
    public static class PriorityStats {
        private Map<String, Integer> categoryCounts;
        private int untestedMethodCount;
        private int highPrioritySuggestionCount;
    }
}