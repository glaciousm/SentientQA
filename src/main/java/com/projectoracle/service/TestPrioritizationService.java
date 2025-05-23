package com.projectoracle.service;

import com.projectoracle.model.TestCase;
import com.projectoracle.model.TestSuggestion;
import com.projectoracle.model.TestExecutionHistory;
import com.projectoracle.model.TestPriorityConfig;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.repository.TestSuggestionRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
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
    
    // Repository for test execution history (mock declaration - would be autowired in real impl)
    private Map<UUID, TestExecutionHistory> testExecutionHistoryRepo = new HashMap<>();
    
    // Default configuration
    private TestPriorityConfig config = TestPriorityConfig.getDefaults();

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
    
    /**
     * Set prioritization configuration
     * 
     * @param config The prioritization configuration
     */
    public void setConfig(TestPriorityConfig config) {
        this.config = config;
    }
    
    /**
     * Get current prioritization configuration
     * 
     * @return The current configuration
     */
    public TestPriorityConfig getConfig() {
        return config;
    }
    
    /**
     * Get all tests prioritized for execution
     * 
     * @return All test cases prioritized by execution importance
     */
    public List<TestCase> getAllTestsPrioritized() {
        log.info("Getting all tests prioritized for execution");
        
        // Get all test cases
        List<TestCase> allTests = testCaseRepository.findAll();
        
        // If no tests exist, return empty list
        if (allTests.isEmpty()) {
            return allTests;
        }
        
        // Prioritize them without any changed files (general prioritization)
        return prioritizeTestExecution(allTests, null);
    }
    
    /**
     * Prioritize tests for execution
     * 
     * @param testCases The test cases to prioritize
     * @param changedFiles List of files that have changed since last execution
     * @return Prioritized list of test cases
     */
    public List<TestCase> prioritizeTestExecution(List<TestCase> testCases, List<String> changedFiles) {
        if (!config.isEnabled() || testCases == null || testCases.isEmpty()) {
            return testCases; // No prioritization
        }
        
        log.info("Prioritizing {} tests for execution", testCases.size());
        
        // Calculate priority scores for each test
        Map<UUID, Integer> priorityScores = calculateExecutionPriorityScores(testCases, changedFiles);
        
        // Sort tests by priority score (descending)
        List<TestCase> prioritizedTests = new ArrayList<>(testCases);
        prioritizedTests.sort((t1, t2) -> {
            Integer score1 = priorityScores.getOrDefault(t1.getId(), 0);
            Integer score2 = priorityScores.getOrDefault(t2.getId(), 0);
            return score2.compareTo(score1); // Descending order
        });
        
        // Apply fast-track for high-priority tests
        if (config.getMaxFastTrackTests() > 0 && prioritizedTests.size() > config.getMaxFastTrackTests()) {
            // Move top N tests to the front for fast-track execution
            int fastTrackCount = Math.min(config.getMaxFastTrackTests(), prioritizedTests.size());
            List<TestCase> fastTrackTests = prioritizedTests.subList(0, fastTrackCount);
            
            log.info("Fast-tracking {} high-priority tests", fastTrackCount);
            
            // Log the fast-tracked tests for visibility
            for (int i = 0; i < fastTrackTests.size(); i++) {
                TestCase test = fastTrackTests.get(i);
                Integer score = priorityScores.getOrDefault(test.getId(), 0);
                log.debug("Fast-track {}: {} (score: {})", i + 1, test.getName(), score);
            }
        }
        
        return prioritizedTests;
    }
    
    /**
     * Calculate priority scores for test execution
     * 
     * @param testCases The test cases to score
     * @param changedFiles List of files that have changed
     * @return Map of test ID to priority score
     */
    private Map<UUID, Integer> calculateExecutionPriorityScores(List<TestCase> testCases, List<String> changedFiles) {
        Map<UUID, Integer> scores = new HashMap<>();
        
        // Set of recently changed files (normalized paths)
        Set<String> changedFileSet = changedFiles != null ? 
                                    changedFiles.stream()
                                               .map(this::normalizePath)
                                               .collect(Collectors.toSet()) : 
                                    new HashSet<>();
        
        // Calculate scores for each test
        for (TestCase testCase : testCases) {
            int score = 0;
            
            // Get execution history
            TestExecutionHistory history = testExecutionHistoryRepo.get(testCase.getId());
            
            // Check if this is a new test
            boolean isNewTest = history == null || history.getTotalExecutions() == 0;
            
            // Calculate correlation with changed files
            double changeCorrelation = calculateChangeCorrelation(testCase, history, changedFileSet);
            
            // Calculate code coverage (mock implementation)
            double coverageScore = 0.5; // Default medium coverage
            
            // Calculate complexity (mock implementation)
            double complexity = calculateTestComplexity(testCase);
            
            // Calculate final score using the configuration
            score = config.calculatePriorityScore(
                history, 
                isNewTest, 
                changeCorrelation, 
                coverageScore, 
                complexity, 
                testHasDependencies(testCase)
            );
            
            scores.put(testCase.getId(), score);
            
            // Update the priority score in history if available
            if (history != null) {
                history.setPriorityScore(score);
                testExecutionHistoryRepo.put(testCase.getId(), history);
            }
        }
        
        return scores;
    }
    
    /**
     * Calculate correlation between a test and changed files
     * 
     * @param testCase The test case
     * @param history Test execution history
     * @param changedFiles Set of changed files
     * @return Correlation score (0-1)
     */
    private double calculateChangeCorrelation(TestCase testCase, TestExecutionHistory history, Set<String> changedFiles) {
        if (changedFiles.isEmpty()) {
            return 0.0; // No changes
        }
        
        // If we have history with code change correlations, use it
        if (history != null && history.getCodeChangeCorrelations() != null) {
            Map<String, Integer> correlations = history.getCodeChangeCorrelations();
            
            int matchingChanges = 0;
            for (String file : changedFiles) {
                if (correlations.containsKey(file)) {
                    matchingChanges += correlations.get(file);
                }
            }
            
            // Calculate correlation score based on matches
            if (matchingChanges > 0) {
                return Math.min(1.0, matchingChanges / 5.0); // Max out at 1.0
            }
        }
        
        // If no history, use a simpler heuristic
        String testClassName = testCase.getClassName();
        String testMethodName = testCase.getMethodName();
        
        // Check if any changed files might be related to this test
        for (String file : changedFiles) {
            // Extract class name from file path
            String fileName = Path.of(file).getFileName().toString();
            if (fileName.endsWith(".java")) {
                String className = fileName.substring(0, fileName.length() - 5);
                
                // Check for direct class name match (without "Test" suffix)
                if (testClassName.equals(className) || testClassName.equals(className + "Test")) {
                    return 0.9; // High correlation
                }
                
                // Check if method name might be related
                if (testMethodName != null) {
                    String normalizedMethodName = testMethodName.toLowerCase();
                    if (normalizedMethodName.startsWith("test")) {
                        normalizedMethodName = normalizedMethodName.substring(4);
                    }
                    
                    if (className.toLowerCase().contains(normalizedMethodName) || 
                        normalizedMethodName.contains(className.toLowerCase())) {
                        return 0.7; // Medium correlation
                    }
                }
                
                // Check for classes in the same package
                if (testCase.getPackageName() != null && file.contains(testCase.getPackageName().replace(".", "/"))) {
                    return 0.5; // Some correlation
                }
            }
        }
        
        return 0.1; // Low default correlation
    }
    
    /**
     * Check if a test has dependencies on other tests
     * 
     * @param testCase The test case to check
     * @return True if test has dependencies
     */
    private boolean testHasDependencies(TestCase testCase) {
        // Check explicit dependencies
        if (testCase.getDependencies() != null && !testCase.getDependencies().isEmpty()) {
            return true;
        }
        
        // Check for dependency annotations in code
        if (testCase.getSourceCode() != null) {
            if (testCase.getSourceCode().contains("@DependsOn") || 
                testCase.getSourceCode().contains("@Order(") ||
                testCase.getSourceCode().contains("@TestMethodOrder")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Calculate test complexity based on code structure
     * 
     * @param testCase The test case
     * @return Complexity score (0-1)
     */
    private double calculateTestComplexity(TestCase testCase) {
        if (testCase.getSourceCode() == null) {
            return 0.5; // Default medium complexity
        }
        
        // Count decision points, loops, etc. as indicators of complexity
        String sourceCode = testCase.getSourceCode();
        
        int decisionPoints = countOccurrences(sourceCode, "if (") + 
                            countOccurrences(sourceCode, "else ") + 
                            countOccurrences(sourceCode, "switch(") + 
                            countOccurrences(sourceCode, "case ") * 2;
                            
        int loops = countOccurrences(sourceCode, "for (") + 
                   countOccurrences(sourceCode, "while (") + 
                   countOccurrences(sourceCode, "do {") * 2;
                   
        int assertions = countOccurrences(sourceCode, "assert") + 
                        countOccurrences(sourceCode, "assertEquals") + 
                        countOccurrences(sourceCode, "assertTrue") + 
                        countOccurrences(sourceCode, "assertFalse");
                        
        int exceptionHandling = countOccurrences(sourceCode, "try {") * 3 + 
                              countOccurrences(sourceCode, "catch (") * 2 + 
                              countOccurrences(sourceCode, "throws ") + 
                              countOccurrences(sourceCode, "@Test(expected = ");
        
        // Calculate complexity score (0-1 scale)
        int totalComplexity = decisionPoints + loops + assertions / 2 + exceptionHandling;
        
        return Math.min(1.0, totalComplexity / 20.0); // Normalize to 0-1 range
    }
    
    /**
     * Normalize file path for consistent comparison
     * 
     * @param path The file path to normalize
     * @return Normalized path
     */
    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        
        // Simplify by just converting to lowercase and normalizing slashes
        return path.toLowerCase().replace('\\', '/');
    }
    
    /**
     * Update test execution history after test runs
     * 
     * @param testCase The executed test case
     * @param successful Whether the test passed
     * @param executionTimeMs Execution time in milliseconds
     * @param errorMessage Error message if test failed
     * @param stackTrace Stack trace if test failed
     * @param codeVersion Code version identifier (e.g., git commit)
     * @param changedFiles Files changed since last execution
     */
    public void recordTestExecution(TestCase testCase, boolean successful,
                                  long executionTimeMs, String errorMessage,
                                  String stackTrace, String codeVersion,
                                  List<String> changedFiles) {
        
        if (testCase == null || testCase.getId() == null) {
            return;
        }
        
        UUID testId = testCase.getId();
        TestExecutionHistory history = testExecutionHistoryRepo.get(testId);
        
        if (history == null) {
            // Create new history
            history = new TestExecutionHistory();
            history.setTestId(testId);
            history.setTestName(testCase.getName());
            history.setFirstExecuted(LocalDateTime.now());
            history.setLastExecuted(LocalDateTime.now());
            history.setTotalExecutions(0);
            history.setPassedExecutions(0);
            history.setFailedExecutions(0);
            history.setAverageExecutionTime(0);
            history.setPassRate(0);
            history.setRecentExecutions(new ArrayList<>());
        }
        
        // Create execution record
        TestExecutionHistory.TestExecution execution = new TestExecutionHistory.TestExecution();
        execution.setId(UUID.randomUUID());
        execution.setExecutedAt(LocalDateTime.now());
        execution.setSuccessful(successful);
        execution.setExecutionTimeMs(executionTimeMs);
        execution.setErrorMessage(errorMessage);
        execution.setStackTrace(stackTrace);
        execution.setCodeVersion(codeVersion);
        execution.setEnvironment(new HashMap<>());
        execution.setChangedFiles(changedFiles);
        
        // Add to history
        history.addExecution(execution);
        
        // Save history
        testExecutionHistoryRepo.put(testId, history);
    }
    
    /**
     * Get prioritized test execution order based on dependency graph
     * and priority scores
     * 
     * @param testCases All test cases
     * @return Ordered list of test cases respecting dependencies
     */
    public List<TestCase> getOrderedExecutionPlan(List<TestCase> testCases) {
        if (testCases == null || testCases.isEmpty()) {
            return List.of();
        }
        
        // Build dependency graph
        Map<UUID, Set<UUID>> dependencyGraph = buildDependencyGraph(testCases);
        
        // Calculate priority scores
        Map<UUID, Integer> priorityScores = calculateExecutionPriorityScores(testCases, List.of());
        
        // Find roots (tests with no dependencies)
        List<TestCase> roots = testCases.stream()
                                       .filter(t -> !dependencyGraph.containsKey(t.getId()) || 
                                                  dependencyGraph.get(t.getId()).isEmpty())
                                       .collect(Collectors.toList());
        
        // Sort roots by priority
        roots.sort((t1, t2) -> {
            Integer score1 = priorityScores.getOrDefault(t1.getId(), 0);
            Integer score2 = priorityScores.getOrDefault(t2.getId(), 0);
            return score2.compareTo(score1); // Descending
        });
        
        // Topological sort with priority
        List<TestCase> result = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        
        for (TestCase root : roots) {
            visit(root, dependencyGraph, visited, result, testCases, priorityScores);
        }
        
        // Add any remaining tests (in case of cycles)
        for (TestCase test : testCases) {
            if (!visited.contains(test.getId())) {
                visit(test, dependencyGraph, visited, result, testCases, priorityScores);
            }
        }
        
        return result;
    }
    
    /**
     * Build dependency graph from test cases
     * 
     * @param testCases The test cases
     * @return Map of test ID to set of dependency IDs
     */
    private Map<UUID, Set<UUID>> buildDependencyGraph(List<TestCase> testCases) {
        Map<UUID, Set<UUID>> graph = new HashMap<>();
        Map<String, TestCase> testsByName = new HashMap<>();
        
        // Index tests by name for dependency lookup
        for (TestCase test : testCases) {
            testsByName.put(test.getName(), test);
            testsByName.put(test.getClassName() + "." + test.getMethodName(), test);
        }
        
        // Build graph from explicit dependencies
        for (TestCase test : testCases) {
            if (test.getDependencies() != null && !test.getDependencies().isEmpty()) {
                Set<UUID> deps = new HashSet<>();
                
                for (String dep : test.getDependencies()) {
                    // Look up dependency by name
                    TestCase depTest = testsByName.get(dep);
                    if (depTest != null) {
                        deps.add(depTest.getId());
                    }
                }
                
                if (!deps.isEmpty()) {
                    graph.put(test.getId(), deps);
                }
            }
        }
        
        return graph;
    }
    
    /**
     * Visit node in topological sort
     * 
     * @param test Current test
     * @param graph Dependency graph
     * @param visited Set of visited tests
     * @param result Result list
     * @param testCases All test cases
     * @param priorityScores Priority scores
     */
    private void visit(TestCase test, Map<UUID, Set<UUID>> graph, Set<UUID> visited,
                      List<TestCase> result, List<TestCase> testCases, Map<UUID, Integer> priorityScores) {
        
        if (visited.contains(test.getId())) {
            return;
        }
        
        visited.add(test.getId());
        
        // Visit dependencies first
        if (graph.containsKey(test.getId())) {
            // Get all dependencies
            List<TestCase> deps = new ArrayList<>();
            for (UUID depId : graph.get(test.getId())) {
                for (TestCase depTest : testCases) {
                    if (depTest.getId().equals(depId)) {
                        deps.add(depTest);
                        break;
                    }
                }
            }
            
            // Sort dependencies by priority
            deps.sort((t1, t2) -> {
                Integer score1 = priorityScores.getOrDefault(t1.getId(), 0);
                Integer score2 = priorityScores.getOrDefault(t2.getId(), 0);
                return score2.compareTo(score1); // Descending
            });
            
            // Visit dependencies in priority order
            for (TestCase dep : deps) {
                visit(dep, graph, visited, result, testCases, priorityScores);
            }
        }
        
        // Add test to result
        result.add(test);
    }
}