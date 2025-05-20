package com.projectoracle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents the execution history of a test case,
 * including metrics and patterns to support test prioritization
 * and self-healing features.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestExecutionHistory {
    private UUID testId;                                      // ID of the test case
    private String testName;                                  // Name of the test case
    private int totalExecutions;                              // Total number of times the test was executed
    private int passedExecutions;                             // Number of successful executions
    private int failedExecutions;                             // Number of failed executions
    private double passRate;                                  // Pass rate (successful / total)
    private double averageExecutionTime;                      // Average execution time in milliseconds
    private LocalDateTime firstExecuted;                      // When the test was first executed
    private LocalDateTime lastExecuted;                       // When the test was last executed
    private TestExecutionTrend trend;                         // Recent trend in test execution results
    private List<TestExecution> recentExecutions;             // Recent test executions (limited to last 10)
    private List<FailurePattern> detectedPatterns;            // Detected failure patterns
    private Map<String, Integer> codeChangeCorrelations;      // Correlation with code changes (file path -> count)
    private int priorityScore;                                // Score for test prioritization (higher = run first)
    private List<String> healingAttempts;                     // Previous healing attempts
    private double healingSuccessRate;                        // Success rate of healing attempts
    private Map<String, Integer> codeErrorTypes;              // Types of code errors detected
    
    /**
     * Execution trend for the test case
     */
    public enum TestExecutionTrend {
        STABLE_PASS,      // Consistently passing
        STABLE_FAIL,      // Consistently failing
        IMPROVING,        // Was failing, now passing more
        DETERIORATING,    // Was passing, now failing more
        FLAKY             // Alternating between pass and fail
    }
    
    /**
     * Represents a single test execution record
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestExecution {
        private UUID id;                      // Unique execution ID
        private LocalDateTime executedAt;     // When it was executed
        private boolean successful;           // Whether it passed
        private long executionTimeMs;         // Execution time in milliseconds
        private String errorMessage;          // Error message if it failed
        private String stackTrace;            // Stack trace if it failed
        private String codeVersion;           // Version of code (e.g., git commit)
        private Map<String, String> environment; // Environment variables/settings
        private List<String> changedFiles;    // Files changed since last execution
    }
    
    /**
     * Represents a pattern in test failures
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailurePattern {
        private String patternId;             // Unique pattern ID
        private String patternType;           // Type of pattern (NullPointer, IOException, etc.)
        private String description;           // Human-readable description
        private String errorSignature;        // Signature of the error
        private int occurrences;              // Number of times this pattern occurred
        private double confidenceScore;       // Confidence score for this pattern
        private Map<String, String> properties; // Additional properties for this pattern
        private List<String> suggestedFixes;  // Suggested fixes for this pattern
    }
    
    /**
     * Add a new test execution to the history
     * 
     * @param execution The execution record to add
     */
    public void addExecution(TestExecution execution) {
        if (recentExecutions == null) {
            recentExecutions = new ArrayList<>();
        }
        
        // Add to recent executions, keeping only last 10
        recentExecutions.add(0, execution);
        if (recentExecutions.size() > 10) {
            recentExecutions.remove(recentExecutions.size() - 1);
        }
        
        // Update statistics
        totalExecutions++;
        if (execution.isSuccessful()) {
            passedExecutions++;
        } else {
            failedExecutions++;
        }
        
        // Update pass rate
        passRate = (double) passedExecutions / totalExecutions;
        
        // Update execution times
        if (firstExecuted == null || execution.getExecutedAt().isBefore(firstExecuted)) {
            firstExecuted = execution.getExecutedAt();
        }
        
        lastExecuted = execution.getExecutedAt();
        
        // Update average execution time
        double totalTime = averageExecutionTime * (totalExecutions - 1) + execution.getExecutionTimeMs();
        averageExecutionTime = totalTime / totalExecutions;
        
        // Update trend based on recent executions
        updateTrend();
        
        // Update error types
        if (!execution.isSuccessful() && execution.getErrorMessage() != null) {
            String errorType = classifyError(execution.getErrorMessage(), execution.getStackTrace());
            if (codeErrorTypes == null) {
                codeErrorTypes = new HashMap<>();
            }
            codeErrorTypes.put(errorType, codeErrorTypes.getOrDefault(errorType, 0) + 1);
        }
        
        // Update code change correlations
        if (!execution.isSuccessful() && execution.getChangedFiles() != null) {
            for (String file : execution.getChangedFiles()) {
                if (codeChangeCorrelations == null) {
                    codeChangeCorrelations = new HashMap<>();
                }
                codeChangeCorrelations.put(file, codeChangeCorrelations.getOrDefault(file, 0) + 1);
            }
        }
        
        // Recalculate priority score
        calculatePriorityScore();
    }
    
    /**
     * Add a detected failure pattern
     * 
     * @param pattern The failure pattern to add
     */
    public void addFailurePattern(FailurePattern pattern) {
        if (detectedPatterns == null) {
            detectedPatterns = new ArrayList<>();
        }
        
        // Check if this pattern already exists
        for (FailurePattern existing : detectedPatterns) {
            if (existing.getPatternId().equals(pattern.getPatternId())) {
                // Update occurrences
                existing.setOccurrences(existing.getOccurrences() + pattern.getOccurrences());
                // Update confidence score (weighted average)
                double newScore = (existing.getConfidenceScore() * existing.getOccurrences() + 
                                  pattern.getConfidenceScore() * pattern.getOccurrences()) / 
                                  (existing.getOccurrences() + pattern.getOccurrences());
                existing.setConfidenceScore(newScore);
                // Merge suggested fixes
                if (pattern.getSuggestedFixes() != null) {
                    if (existing.getSuggestedFixes() == null) {
                        existing.setSuggestedFixes(new ArrayList<>());
                    }
                    for (String fix : pattern.getSuggestedFixes()) {
                        if (!existing.getSuggestedFixes().contains(fix)) {
                            existing.getSuggestedFixes().add(fix);
                        }
                    }
                }
                return;
            }
        }
        
        // Add new pattern
        detectedPatterns.add(pattern);
    }
    
    /**
     * Add a healing attempt
     * 
     * @param attempt Description of the healing attempt
     * @param successful Whether it was successful
     */
    public void addHealingAttempt(String attempt, boolean successful) {
        if (healingAttempts == null) {
            healingAttempts = new ArrayList<>();
        }
        
        healingAttempts.add(attempt);
        
        // Update healing success rate
        int successCount = 0;
        for (int i = 0; i < healingAttempts.size(); i++) {
            if (healingAttempts.get(i).startsWith("[SUCCESS]")) {
                successCount++;
            }
        }
        
        healingSuccessRate = (double) successCount / healingAttempts.size();
    }
    
    /**
     * Update the execution trend based on recent executions
     */
    private void updateTrend() {
        if (recentExecutions == null || recentExecutions.size() < 3) {
            // Not enough data to determine trend
            if (passRate > 0.9) {
                trend = TestExecutionTrend.STABLE_PASS;
            } else if (passRate < 0.1) {
                trend = TestExecutionTrend.STABLE_FAIL;
            } else {
                trend = TestExecutionTrend.FLAKY;
            }
            return;
        }
        
        // Check recent executions (last 5 or fewer)
        int recentCount = Math.min(5, recentExecutions.size());
        int recentPass = 0;
        boolean alternating = false;
        boolean lastSuccess = recentExecutions.get(0).isSuccessful();
        
        for (int i = 0; i < recentCount; i++) {
            if (recentExecutions.get(i).isSuccessful()) {
                recentPass++;
            }
            
            // Check for alternating pattern
            if (i > 0 && recentExecutions.get(i).isSuccessful() != lastSuccess) {
                alternating = true;
                lastSuccess = recentExecutions.get(i).isSuccessful();
            }
        }
        
        double recentPassRate = (double) recentPass / recentCount;
        
        // Determine trend
        if (alternating && Math.abs(recentPassRate - 0.5) < 0.3) {
            trend = TestExecutionTrend.FLAKY;
        } else if (recentPassRate > 0.8) {
            if (passRate < 0.7) {
                trend = TestExecutionTrend.IMPROVING;
            } else {
                trend = TestExecutionTrend.STABLE_PASS;
            }
        } else if (recentPassRate < 0.2) {
            if (passRate > 0.3) {
                trend = TestExecutionTrend.DETERIORATING;
            } else {
                trend = TestExecutionTrend.STABLE_FAIL;
            }
        } else if (recentPassRate > passRate + 0.2) {
            trend = TestExecutionTrend.IMPROVING;
        } else if (recentPassRate < passRate - 0.2) {
            trend = TestExecutionTrend.DETERIORATING;
        } else {
            trend = TestExecutionTrend.FLAKY;
        }
    }
    
    /**
     * Calculate priority score for test prioritization
     */
    private void calculatePriorityScore() {
        int score = 0;
        
        // Base score: Favor tests that have failed more recently
        if (trend == TestExecutionTrend.DETERIORATING) {
            score += 30;
        } else if (trend == TestExecutionTrend.FLAKY) {
            score += 20;
        } else if (trend == TestExecutionTrend.STABLE_FAIL) {
            score += 15;
        } else if (trend == TestExecutionTrend.IMPROVING) {
            score += 10;
        }
        
        // Add score for recent failures
        if (recentExecutions != null && !recentExecutions.isEmpty()) {
            if (!recentExecutions.get(0).isSuccessful()) {
                score += 25; // Failed last time
            }
            
            // Count recent failures (max 5)
            int recentCount = Math.min(5, recentExecutions.size());
            int recentFailures = 0;
            for (int i = 0; i < recentCount; i++) {
                if (!recentExecutions.get(i).isSuccessful()) {
                    recentFailures++;
                }
            }
            score += recentFailures * 5;
        }
        
        // Add score for high failure rate
        if (passRate < 0.5) {
            score += 10;
        }
        
        // Add score for quick execution (favor fast tests)
        if (averageExecutionTime < 100) {
            score += 10;
        } else if (averageExecutionTime < 500) {
            score += 5;
        }
        
        // Add score for code change correlations
        if (codeChangeCorrelations != null && !codeChangeCorrelations.isEmpty()) {
            // Max 20 points for change correlations
            score += Math.min(20, codeChangeCorrelations.size() * 5);
        }
        
        // Add score for failure patterns
        if (detectedPatterns != null) {
            // Add 5 points per detected pattern, max 20
            score += Math.min(20, detectedPatterns.size() * 5);
        }
        
        priorityScore = score;
    }
    
    /**
     * Classify an error based on message and stack trace
     * 
     * @param errorMessage The error message
     * @param stackTrace The stack trace
     * @return The error type
     */
    private String classifyError(String errorMessage, String stackTrace) {
        if (errorMessage == null) {
            return "Unknown";
        }
        
        if (errorMessage.contains("NullPointerException")) {
            return "NullPointer";
        } else if (errorMessage.contains("ArrayIndexOutOfBoundsException")) {
            return "ArrayIndexOutOfBounds";
        } else if (errorMessage.contains("ClassCastException")) {
            return "ClassCast";
        } else if (errorMessage.contains("NoSuchElementException")) {
            return "NoSuchElement";
        } else if (errorMessage.contains("IllegalArgumentException")) {
            return "IllegalArgument";
        } else if (errorMessage.contains("FileNotFoundException")) {
            return "FileNotFound";
        } else if (errorMessage.contains("IOException")) {
            return "IO";
        } else if (errorMessage.contains("AssertionError")) {
            return "Assertion";
        } else if (errorMessage.contains("TimeoutException")) {
            return "Timeout";
        } else if (errorMessage.contains("ConcurrentModificationException")) {
            return "ConcurrentModification";
        } else if (errorMessage.contains("OutOfMemoryError")) {
            return "OutOfMemory";
        } else if (errorMessage.contains("StackOverflowError")) {
            return "StackOverflow";
        } else {
            // Extract error class name from message or stack trace
            if (stackTrace != null && !stackTrace.isEmpty()) {
                String[] lines = stackTrace.split("\n");
                if (lines.length > 0) {
                    String firstLine = lines[0];
                    if (firstLine.contains(":")) {
                        return firstLine.substring(0, firstLine.indexOf(":")).trim();
                    }
                }
            }
            
            return "Unknown";
        }
    }
}