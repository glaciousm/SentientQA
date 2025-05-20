package com.projectoracle.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.projectoracle.model.TestCase;
import com.projectoracle.model.TestExecutionHistory;
import com.projectoracle.repository.TestCaseRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Service for healing broken tests.
 * Analyzes test failures and attempts to repair tests affected by code changes.
 */
@Service
public class TestHealingService {

    private static final Logger logger = LoggerFactory.getLogger(TestHealingService.class);

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private CodeAnalysisService codeAnalysisService;

    @Autowired
    private TestGenerationService testGenerationService;

    @Autowired
    private EnhancedTestExecutionService testExecutionService;

    @Autowired
    private AIModelService aiModelService;
    
    @Autowired
    private FailurePatternRecognizer failurePatternRecognizer;

    @Autowired
    @Qualifier("backgroundExecutor")
    private Executor backgroundExecutor;
    
    // Repository for test execution history (mock declaration - would be autowired in real impl)
    private Map<UUID, TestExecutionHistory> testExecutionHistoryRepo = new HashMap<>();

    private final JavaParser javaParser = new JavaParser();

    /**
     * Attempt to heal a broken test.
     *
     * @param testCaseId the ID of the test case to heal
     * @return a CompletableFuture that will complete when the healing is done
     */
    public CompletableFuture<TestCase> healTest(UUID testCaseId) {
        return CompletableFuture.supplyAsync(() -> {
            TestCase testCase = testCaseRepository.findById(testCaseId);
            if (testCase == null) {
                throw new IllegalArgumentException("Test case not found: " + testCaseId);
            }

            if (testCase.getStatus() != TestCase.TestStatus.BROKEN &&
                    testCase.getStatus() != TestCase.TestStatus.FAILED) {
                logger.info("Test case {} is not broken or failed, skipping healing", testCaseId);
                return testCase;
            }

            logger.info("Attempting to heal test case: {}", testCaseId);

            try {
                // Get the current implementation of the test target
                String className = testCase.getClassName().replace("Test", "");
                MethodInfo methodInfo = findLatestMethodImplementation(className, testCase);

                if (methodInfo == null) {
                    logger.error("Could not find method implementation for test case: {}", testCaseId);
                    return testCase;
                }

                // Analyze what's broken in the test
                Map<String, String> analysisResults = analyzeTestFailure(testCase, methodInfo);

                // Generate a healed test
                TestCase healedTest = healTestCase(testCase, methodInfo, analysisResults);

                // Save the healed test
                testCaseRepository.save(healedTest);
                
                // Record the healing attempt in history
                recordHealingAttempt(testCase.getId(), "Applied automated healing based on pattern analysis", true);

                // Execute the healed test to verify it works
                CompletableFuture<TestCase> executionResult = testExecutionService.executeTest(healedTest.getId());
                TestCase executedTest = executionResult.join();
                
                // Update history based on execution result
                boolean healingSuccessful = (executedTest.getStatus() == TestCase.TestStatus.PASSED);
                if (!healingSuccessful) {
                    recordHealingAttempt(testCase.getId(), "Healing attempt failed on execution", false);
                }

                return executedTest;
            } catch (Exception e) {
                logger.error("Error healing test case: {}", testCaseId, e);
                // Update test status to reflect the healing attempt failed
                testCase.setStatus(TestCase.TestStatus.BROKEN);
                testCase.setModifiedAt(LocalDateTime.now());
                testCaseRepository.save(testCase);
                
                // Record the failed healing attempt
                recordHealingAttempt(testCase.getId(), "Healing failed with error: " + e.getMessage(), false);
                
                return testCase;
            }
        }, backgroundExecutor);
    }

    /**
     * Find the latest implementation of a method for a test case.
     */
    private MethodInfo findLatestMethodImplementation(String className, TestCase testCase) {
        // This is a simplified version. In a real system, we would:
        // 1. Find the actual source file for the class under test
        // 2. Parse and extract the current implementation of the target method
        // For now, we'll use a mock implementation

        // Extract the method name from the test case
        String testMethodName = testCase.getMethodName();
        String targetMethodName = testMethodName.startsWith("test") ?
                testMethodName.substring(4, 5).toLowerCase() + testMethodName.substring(5) :
                testMethodName;

        // Mock method info for demonstration
        return MethodInfo.builder()
                         .packageName(testCase.getPackageName())
                         .className(className)
                         .methodName(targetMethodName)
                         .returnType("void")
                         .parameters(new ArrayList<>())
                         .exceptions(new ArrayList<>())
                         .body("{ /* Implementation here */ }")
                         .isPublic(true)
                         .isStatic(false)
                         .build();
    }

    /**
     * Record a healing attempt in the test execution history
     * 
     * @param testId The test ID
     * @param attemptDescription Description of the healing attempt
     * @param successful Whether the attempt was successful
     */
    private void recordHealingAttempt(UUID testId, String attemptDescription, boolean successful) {
        TestExecutionHistory history = testExecutionHistoryRepo.get(testId);
        
        if (history == null) {
            // Create new history if none exists
            TestCase testCase = testCaseRepository.findById(testId);
            if (testCase != null) {
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
                history.setTrend(TestExecutionHistory.TestExecutionTrend.STABLE_FAIL);
                history.setPriorityScore(0);
                history.setHealingSuccessRate(0);
            } else {
                return; // Can't find test case
            }
        }
        
        // Add healing attempt to history
        String attemptRecord = (successful ? "[SUCCESS] " : "[FAILURE] ") + attemptDescription;
        history.addHealingAttempt(attemptRecord, successful);
        
        // Save history
        testExecutionHistoryRepo.put(testId, history);
    }
    
    /**
     * Analyze why a test is failing and identify the issues.
     */
    private Map<String, String> analyzeTestFailure(TestCase testCase, MethodInfo currentImplementation) {
        Map<String, String> analysisResults = new HashMap<>();

        // Extract the test source code
        String testSourceCode = testCase.getSourceCode();
        
        // Get the test execution history
        TestExecutionHistory history = testExecutionHistoryRepo.get(testCase.getId());
        
        // If we have a history of recent failures, use it to detect patterns
        if (history != null && history.getRecentExecutions() != null && !history.getRecentExecutions().isEmpty()) {
            TestExecutionHistory.TestExecution latestExecution = history.getRecentExecutions().get(0);
            
            if (!latestExecution.isSuccessful()) {
                // Use the failure pattern recognizer to analyze the failure
                List<TestExecutionHistory.FailurePattern> patterns = failurePatternRecognizer.analyzeFailure(
                    testCase, 
                    latestExecution.getErrorMessage(), 
                    latestExecution.getStackTrace()
                );
                
                // Add detected patterns to the history
                for (TestExecutionHistory.FailurePattern pattern : patterns) {
                    history.addFailurePattern(pattern);
                    
                    // Add detected pattern to analysis results
                    analysisResults.put("pattern_" + pattern.getPatternType(), pattern.getDescription());
                    
                    // Add suggested fixes
                    if (pattern.getSuggestedFixes() != null && !pattern.getSuggestedFixes().isEmpty()) {
                        for (int i = 0; i < pattern.getSuggestedFixes().size(); i++) {
                            analysisResults.put("fix_" + pattern.getPatternType() + "_" + i, 
                                                pattern.getSuggestedFixes().get(i));
                        }
                    }
                }
                
                // Save the updated history
                testExecutionHistoryRepo.put(testCase.getId(), history);
            }
        }

        try {
            // Parse the test code
            ParseResult<CompilationUnit> parseResult = javaParser.parse(testSourceCode);
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();

                // Look for common issues

                // 1. Method signature changes
                boolean methodSignatureChanged = !methodSignaturesMatch(testCase, currentImplementation);
                if (methodSignatureChanged) {
                    analysisResults.put("methodSignatureChanged", "true");
                    analysisResults.put("oldSignature", testCase.getGenerationPrompt());
                    analysisResults.put("newSignature", buildPromptForMethod(currentImplementation));
                }

                // 2. Missing imports
                List<String> missingImports = findMissingImports(cu);
                if (!missingImports.isEmpty()) {
                    analysisResults.put("missingImports", String.join(",", missingImports));
                }

                // 3. Invalid assertions
                List<String> invalidAssertions = findInvalidAssertions(cu, currentImplementation);
                if (!invalidAssertions.isEmpty()) {
                    analysisResults.put("invalidAssertions", String.join(",", invalidAssertions));
                }

                // 4. Check for renamed variables, methods, etc.
                RenameVisitor renameVisitor = new RenameVisitor(currentImplementation);
                cu.accept(renameVisitor, null);
                Map<String, String> renamedElements = renameVisitor.getRenamedElements();
                if (!renamedElements.isEmpty()) {
                    analysisResults.put("renamedElements", renamedElements.toString());
                }
            }
        } catch (Exception e) {
            logger.error("Error analyzing test failure: {}", testCase.getId(), e);
            analysisResults.put("error", "Failed to analyze test: " + e.getMessage());
        }

        // If no specific issues found, mark for complete regeneration
        if (analysisResults.isEmpty()) {
            analysisResults.put("completeRegeneration", "true");
        }

        return analysisResults;
    }

    /**
     * Check if method signatures match between test and current implementation.
     */
    private boolean methodSignaturesMatch(TestCase testCase, MethodInfo currentImplementation) {
        // This is a simplified check. In a real system, we would:
        // 1. Extract the method signature from the test
        // 2. Compare with the current implementation signature
        return true;
    }

    /**
     * Find missing imports in the test file.
     */
    private List<String> findMissingImports(CompilationUnit cu) {
        // This is a simplified implementation. In a real system, we would:
        // 1. Extract all referenced classes in the test
        // 2. Check if they are imported or in java.lang
        return new ArrayList<>();
    }

    /**
     * Find assertions that might be invalid due to API changes.
     */
    private List<String> findInvalidAssertions(CompilationUnit cu, MethodInfo currentImplementation) {
        // This is a simplified implementation. In a real system, we would:
        // 1. Extract all assertions in the test
        // 2. Check if they use methods or properties that exist in the current implementation
        return new ArrayList<>();
    }

    /**
     * Generate a prompt for the AI model based on method info.
     */
    private String buildPromptForMethod(MethodInfo methodInfo) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("Method Info for Healing:\n\n");

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

        return promptBuilder.toString();
    }

    /**
     * Heal a test case based on analysis results.
     */
    private TestCase healTestCase(TestCase testCase, MethodInfo currentImplementation, Map<String, String> analysisResults) {
        // Create a new test case based on the original
        TestCase healedTest = TestCase.builder()
                                      .id(testCase.getId())
                                      .name(testCase.getName())
                                      .description(testCase.getDescription() + " (Healed)")
                                      .type(testCase.getType())
                                      .priority(testCase.getPriority())
                                      .status(TestCase.TestStatus.HEALED)
                                      .packageName(testCase.getPackageName())
                                      .className(testCase.getClassName())
                                      .methodName(testCase.getMethodName())
                                      .createdAt(testCase.getCreatedAt())
                                      .modifiedAt(LocalDateTime.now())
                                      .build();

        // Check if we need a complete regeneration
        if (analysisResults.containsKey("completeRegeneration") ||
                analysisResults.containsKey("methodSignatureChanged")) {

            // Generate a completely new test
            String prompt = buildHealingPrompt(testCase, currentImplementation, analysisResults);
            String generatedTestCode = aiModelService.generateText(prompt, 1000);

            healedTest.setSourceCode(generatedTestCode);
            healedTest.setGenerationPrompt(prompt);
            healedTest.setAssertions(extractAssertions(generatedTestCode));

            return healedTest;
        }

        // Handle partial fixes
        String originalCode = testCase.getSourceCode();
        String healedCode = originalCode;

        // Fix missing imports
        if (analysisResults.containsKey("missingImports")) {
            healedCode = addImports(healedCode, analysisResults.get("missingImports").split(","));
        }

        // Fix invalid assertions
        if (analysisResults.containsKey("invalidAssertions")) {
            healedCode = fixAssertions(healedCode, analysisResults.get("invalidAssertions").split(","), currentImplementation);
        }

        // Fix renamed elements
        if (analysisResults.containsKey("renamedElements")) {
            healedCode = fixRenamedElements(healedCode, analysisResults.get("renamedElements"));
        }

        healedTest.setSourceCode(healedCode);
        healedTest.setGenerationPrompt(testCase.getGenerationPrompt());
        healedTest.setAssertions(extractAssertions(healedCode));

        return healedTest;
    }

    /**
     * Build a prompt for healing a broken test.
     */
    private String buildHealingPrompt(TestCase testCase, MethodInfo currentImplementation, Map<String, String> analysisResults) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("Heal the following broken JUnit 5 test for a Java method. ");
        promptBuilder.append("The test is failing and needs to be fixed.\n\n");

        // Add current method info
        promptBuilder.append("CURRENT METHOD IMPLEMENTATION:\n");
        promptBuilder.append(buildPromptForMethod(currentImplementation)).append("\n");

        // Add the broken test
        promptBuilder.append("BROKEN TEST:\n");
        promptBuilder.append(testCase.getSourceCode()).append("\n");

        // Add analysis of the issues
        promptBuilder.append("TEST ISSUES:\n");
        
        // Group analysis results by type
        Map<String, List<String>> groupedResults = new HashMap<>();
        
        analysisResults.forEach((key, value) -> {
            if (key.startsWith("pattern_")) {
                String patternType = key.substring("pattern_".length());
                groupedResults.computeIfAbsent("detected_patterns", k -> new ArrayList<>()).add(patternType + ": " + value);
            } else if (key.startsWith("fix_")) {
                String[] parts = key.split("_");
                if (parts.length >= 3) {
                    String patternType = parts[1];
                    groupedResults.computeIfAbsent("suggested_fixes_" + patternType, k -> new ArrayList<>()).add(value);
                }
            } else {
                groupedResults.computeIfAbsent("other_issues", k -> new ArrayList<>()).add(key + ": " + value);
            }
        });
        
        // Add detected patterns
        if (groupedResults.containsKey("detected_patterns")) {
            promptBuilder.append("\nDETECTED FAILURE PATTERNS:\n");
            for (String pattern : groupedResults.get("detected_patterns")) {
                promptBuilder.append("- ").append(pattern).append("\n");
            }
        }
        
        // Add suggested fixes for each pattern type
        groupedResults.keySet().stream()
                     .filter(k -> k.startsWith("suggested_fixes_"))
                     .forEach(key -> {
                         String patternType = key.substring("suggested_fixes_".length());
                         promptBuilder.append("\nSUGGESTED FIXES FOR ").append(patternType).append(":\n");
                         List<String> fixes = groupedResults.get(key);
                         for (int i = 0; i < fixes.size(); i++) {
                             promptBuilder.append(i+1).append(". ").append(fixes.get(i)).append("\n");
                         }
                     });
        
        // Add other issues
        if (groupedResults.containsKey("other_issues")) {
            promptBuilder.append("\nOTHER ISSUES:\n");
            for (String issue : groupedResults.get("other_issues")) {
                promptBuilder.append("- ").append(issue).append("\n");
            }
        }

        // Add instructions for healing
        promptBuilder.append("\nPlease create a fixed version of this test that:");
        promptBuilder.append("\n1. Addresses the specific failure patterns identified above");
        promptBuilder.append("\n2. Applies the most appropriate fix from the suggested fixes");
        promptBuilder.append("\n3. Works with the current method implementation");
        promptBuilder.append("\n4. Maintains the same test coverage and intent");
        promptBuilder.append("\n5. Uses proper JUnit 5 syntax");
        promptBuilder.append("\n6. Includes appropriate imports");
        promptBuilder.append("\n7. Provides meaningful assertions");
        
        // Add specific guidance based on failure patterns
        if (analysisResults.keySet().stream().anyMatch(k -> k.startsWith("pattern_NullPointerException"))) {
            promptBuilder.append("\n\nFor null pointer exceptions, make sure to:\n");
            promptBuilder.append("- Add null checks before accessing objects\n");
            promptBuilder.append("- Initialize objects before use\n");
            promptBuilder.append("- Use assertNotNull where appropriate\n");
        }
        
        if (analysisResults.keySet().stream().anyMatch(k -> k.startsWith("pattern_AssertionError"))) {
            promptBuilder.append("\n\nFor assertion errors, make sure to:\n");
            promptBuilder.append("- Verify expected values match the actual behavior\n");
            promptBuilder.append("- Consider using more specific assertions (assertEquals, assertFalse, etc.)\n");
            promptBuilder.append("- Add appropriate delta values for floating point comparisons\n");
        }
        
        if (analysisResults.keySet().stream().anyMatch(k -> k.startsWith("pattern_IndexOutOfBoundsException"))) {
            promptBuilder.append("\n\nFor index out of bounds exceptions, make sure to:\n");
            promptBuilder.append("- Check array/collection bounds before accessing elements\n");
            promptBuilder.append("- Remember that indices are zero-based (max index is length-1)\n");
            promptBuilder.append("- Check if collections are empty before accessing the first element\n");
        }

        return promptBuilder.toString();
    }

    /**
     * Add missing imports to the test code.
     */
    private String addImports(String code, String[] missingImports) {
        // This is a simplified implementation. In a real system, we would:
        // 1. Parse the code
        // 2. Add the imports in the right place
        // 3. Generate the updated code
        return code;
    }

    /**
     * Fix invalid assertions in the test code.
     */
    private String fixAssertions(String code, String[] invalidAssertions, MethodInfo currentImplementation) {
        // This is a simplified implementation. In a real system, we would:
        // 1. Parse the code
        // 2. Replace the invalid assertions
        // 3. Generate the updated code
        return code;
    }

    /**
     * Fix renamed elements in the test code.
     */
    private String fixRenamedElements(String code, String renamedElementsString) {
        // This is a simplified implementation. In a real system, we would:
        // 1. Parse the renamedElementsString into a map
        // 2. Parse the code
        // 3. Replace all occurrences of the old names with the new names
        // 4. Generate the updated code
        return code;
    }

    /**
     * Extract assertions from a test.
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
     * Visitor to identify renamed elements in the code.
     */
    private static class RenameVisitor extends VoidVisitorAdapter<Void> {
        private final MethodInfo currentImplementation;
        private final Map<String, String> renamedElements = new HashMap<>();

        public RenameVisitor(MethodInfo currentImplementation) {
            this.currentImplementation = currentImplementation;
        }

        @Override
        public void visit(NameExpr n, Void arg) {
            // This is a simplified implementation. In a real system, we would:
            // 1. Keep track of all identifiers in the old code
            // 2. Compare with identifiers in the new code
            // 3. Identify potential renames
            super.visit(n, arg);
        }

        public Map<String, String> getRenamedElements() {
            return renamedElements;
        }
    }

    /**
     * Heal all broken tests.
     *
     * @return a CompletableFuture that will complete when all tests are healed
     */
    public CompletableFuture<List<TestCase>> healAllBrokenTests() {
        List<TestCase> brokenTests = testCaseRepository.findByStatus(TestCase.TestStatus.BROKEN);

        logger.info("Healing {} broken tests", brokenTests.size());

        List<CompletableFuture<TestCase>> futures = brokenTests.stream()
                                                               .map(testCase -> healTest(testCase.getId()))
                                                               .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> brokenTests);
    }

    /**
     * Analyze the impact of code changes on existing tests.
     *
     * @param oldSourceCode the old source code
     * @param newSourceCode the new source code
     * @return list of potentially affected test cases
     */
    public List<TestCase> analyzeChangeImpact(String oldSourceCode, String newSourceCode) {
        logger.info("Analyzing impact of code changes");

        try {
            // Parse old and new source
            Map<String, MethodInfo> oldMethods = codeAnalysisService.analyzeJavaSource(oldSourceCode);
            Map<String, MethodInfo> newMethods = codeAnalysisService.analyzeJavaSource(newSourceCode);

            // Find all tests that might be affected
            List<TestCase> affectedTests = new ArrayList<>();

            for (String methodKey : oldMethods.keySet()) {
                MethodInfo oldMethod = oldMethods.get(methodKey);

                // Check if method was removed
                if (!newMethods.containsKey(methodKey)) {
                    // Find tests targeting this method
                    List<TestCase> testsForMethod = findTestsForMethod(oldMethod);
                    affectedTests.addAll(testsForMethod);
                    continue;
                }

                // Check if method was changed
                MethodInfo newMethod = newMethods.get(methodKey);
                if (!methodsAreEquivalent(oldMethod, newMethod)) {
                    // Find tests targeting this method
                    List<TestCase> testsForMethod = findTestsForMethod(oldMethod);
                    affectedTests.addAll(testsForMethod);
                }
            }

            // Mark affected tests as potentially broken
            for (TestCase testCase : affectedTests) {
                testCase.setStatus(TestCase.TestStatus.BROKEN);
                testCase.setModifiedAt(LocalDateTime.now());
                testCaseRepository.save(testCase);
            }

            return affectedTests;
        } catch (Exception e) {
            logger.error("Error analyzing change impact", e);
            return List.of();
        }
    }

    /**
     * Find tests that target a specific method.
     */
    private List<TestCase> findTestsForMethod(MethodInfo methodInfo) {
        // This is a simplified implementation. In a real system, we would:
        // 1. Query the test case repository for tests that target this method
        String className = methodInfo.getClassName();
        String methodName = methodInfo.getMethodName();

        return testCaseRepository.findByClassName("Test" + className).stream()
                                 .filter(tc -> tc.getMethodName().equals("test" + capitalize(methodName)) ||
                                         tc.getMethodName().startsWith("test" + capitalize(methodName) + "_"))
                                 .collect(Collectors.toList());
    }

    /**
     * Check if two method implementations are equivalent.
     */
    private boolean methodsAreEquivalent(MethodInfo oldMethod, MethodInfo newMethod) {
        // This is a simplified implementation. In a real system, we would:
        // 1. Compare signatures (return type, parameters)
        // 2. Compare exceptions
        // 3. Potentially analyze the body for semantic equivalence

        if (!oldMethod.getReturnType().equals(newMethod.getReturnType())) {
            return false;
        }

        if (oldMethod.getParameters().size() != newMethod.getParameters().size()) {
            return false;
        }

        // Compare parameters
        for (int i = 0; i < oldMethod.getParameters().size(); i++) {
            ParameterInfo oldParam = oldMethod.getParameters().get(i);
            ParameterInfo newParam = newMethod.getParameters().get(i);

            if (!oldParam.getType().equals(newParam.getType())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Capitalize the first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}