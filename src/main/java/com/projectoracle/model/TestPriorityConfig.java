package com.projectoracle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration for test prioritization.
 * Defines weights and rules for ordering test execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestPriorityConfig {
    private boolean enabled;                    // Whether test prioritization is enabled
    private int failureWeight;                  // Weight for test failure history
    private int changeCorrelationWeight;        // Weight for correlation with code changes
    private int executionTimeWeight;            // Weight for test execution time
    private int complexityWeight;               // Weight for test complexity
    private int coverageWeight;                 // Weight for code coverage
    private int dependencyWeight;               // Weight for test dependencies
    private boolean prioritizeFlaky;            // Whether to prioritize flaky tests
    private boolean prioritizeNew;              // Whether to prioritize new tests
    private int maxFastTrackTests;              // Maximum number of fast-track tests to run first
    private List<String> excludedTestPatterns;  // Test patterns to exclude from prioritization
    private List<String> includedTestPatterns;  // Test patterns to include in prioritization
    
    /**
     * Returns default configuration settings
     * 
     * @return Default configuration
     */
    public static TestPriorityConfig getDefaults() {
        return TestPriorityConfig.builder()
            .enabled(true)
            .failureWeight(3)             // Higher weight for failing tests
            .changeCorrelationWeight(4)   // Highest weight for tests correlated with changes
            .executionTimeWeight(2)       // Medium weight for execution time
            .complexityWeight(1)          // Lower weight for test complexity
            .coverageWeight(2)            // Medium weight for code coverage
            .dependencyWeight(1)          // Lower weight for dependencies
            .prioritizeFlaky(true)        // Prioritize flaky tests
            .prioritizeNew(true)          // Prioritize new tests
            .maxFastTrackTests(10)        // Fast-track up to 10 tests
            .build();
    }
    
    /**
     * Calculate priority score for a test based on execution history
     * 
     * @param history Test execution history
     * @param isNewTest Whether this is a new test
     * @param changeCorrelation Correlation score with recent changes (0-1)
     * @param coverageScore Code coverage score (0-1)
     * @param complexity Test complexity score (0-1)
     * @param hasDependencies Whether test has dependencies
     * @return Priority score (higher = higher priority)
     */
    public int calculatePriorityScore(TestExecutionHistory history, boolean isNewTest, 
                                    double changeCorrelation, double coverageScore,
                                    double complexity, boolean hasDependencies) {
        
        if (!enabled) {
            return 0;  // Prioritization disabled
        }
        
        int score = 0;
        
        // Score from existing history
        if (history != null) {
            // Failure history
            if (history.getTrend() == TestExecutionHistory.TestExecutionTrend.DETERIORATING) {
                score += 30 * failureWeight;
            } else if (history.getTrend() == TestExecutionHistory.TestExecutionTrend.FLAKY && prioritizeFlaky) {
                score += 20 * failureWeight;
            } else if (history.getTrend() == TestExecutionHistory.TestExecutionTrend.STABLE_FAIL) {
                score += 15 * failureWeight;
            }
            
            // Execution time (faster tests get higher score)
            double speedScore = 1.0 - Math.min(1.0, history.getAverageExecutionTime() / 10000.0);
            score += (int)(speedScore * 10 * executionTimeWeight);
        }
        
        // New test score
        if (isNewTest && prioritizeNew) {
            score += 20;
        }
        
        // Change correlation
        score += (int)(changeCorrelation * 40 * changeCorrelationWeight);
        
        // Coverage score
        score += (int)(coverageScore * 10 * coverageWeight);
        
        // Complexity
        score += (int)(complexity * 10 * complexityWeight);
        
        // Dependencies
        if (hasDependencies) {
            score += 5 * dependencyWeight;
        }
        
        return score;
    }
}