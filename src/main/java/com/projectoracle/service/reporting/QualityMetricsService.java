package com.projectoracle.service.reporting;

import com.projectoracle.model.TestCase;
import com.projectoracle.model.TestExecutionHistory;
import com.projectoracle.repository.TestCaseRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for collecting and analyzing quality metrics.
 * Provides data for the Quality Intelligence Dashboard.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QualityMetricsService {

    private final TestCaseRepository testCaseRepository;
    private final TestResultAnalyzer resultAnalyzer;
    private final TestCoverageCalculator coverageCalculator;
    
    // In a real implementation, this would be autowired
    private Map<String, List<TestExecutionHistory>> executionHistoryCache = new HashMap<>();

    /**
     * Get dashboard summary data
     * 
     * @return Summary metrics for the dashboard
     */
    public DashboardSummary getDashboardSummary() {
        log.info("Generating dashboard summary");
        
        List<TestCase> allTests = testCaseRepository.findAll();
        
        // Check if we have any real test data
        boolean hasRealData = !allTests.isEmpty();
        
        if (hasRealData) {
            log.info("Using real test data for dashboard summary (found {} tests)", allTests.size());
            
            // Build dashboard summary from real data
            return DashboardSummary.builder()
                .totalTests(allTests.size())
                .passingTests((int) allTests.stream().filter(t -> t.getStatus() == TestCase.TestStatus.PASSED).count())
                .failingTests((int) allTests.stream().filter(t -> t.getStatus() == TestCase.TestStatus.FAILED).count())
                .flakyTests(detectFlakyTests(allTests).size())
                .averagePassRate(calculateOverallPassRate(allTests))
                .testCoverage(coverageCalculator.calculateOverallCoverage())
                .testTrend(calculateTestTrend(allTests))
                .healthScore(calculateHealthScore(allTests))
                .build();
        } else {
            log.info("No real test data found, using mock data for dashboard summary");
            
            // Return mock data
            return getDashboardSummaryMock();
        }
    }
    
    /**
     * Get mock dashboard summary data (for demo purposes)
     */
    private DashboardSummary getDashboardSummaryMock() {
        // Return mock dashboard data
        return DashboardSummary.builder()
            .totalTests(125)
            .passingTests(98)
            .failingTests(18)
            .flakyTests(9)
            .averagePassRate(0.78)
            .testCoverage(0.65)
            .testTrend(TestTrend.IMPROVING)  // Trend is improving
            .healthScore(72)
            .build();
    }
    
    /**
     * Get detailed failure trend data
     * 
     * @param timeframe Timeframe in days (7, 30, 90)
     * @return List of failure trend points
     */
    public List<TrendPoint> getFailureTrends(int timeframe) {
        log.info("Generating failure trends for {} days", timeframe);
        
        // For now, generate mock data
        // In a real implementation, this would analyze actual test history
        List<TrendPoint> trends = new ArrayList<>();
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minus(timeframe, ChronoUnit.DAYS);
        
        // Generate daily trend points
        for (int i = 0; i <= timeframe; i++) {
            LocalDateTime pointDate = start.plus(i, ChronoUnit.DAYS);
            
            // Create trend point with mock data
            // In real implementation, this would aggregate actual results for each day
            TrendPoint point = TrendPoint.builder()
                .timestamp(pointDate)
                .totalTests(100 + (int)(Math.random() * 20) - 10)
                .failedTests(5 + (int)(Math.random() * 10))
                .passRate(calculateMockPassRate(i, timeframe))
                .build();
            
            trends.add(point);
        }
        
        return trends;
    }
    
    /**
     * Get flaky test report
     * 
     * @return List of flaky tests with details
     */
    public List<FlakyTestInfo> getFlakyTestReport() {
        log.info("Generating flaky test report");
        
        List<TestCase> allTests = testCaseRepository.findAll();
        List<TestCase> flakyTests = detectFlakyTests(allTests);
        
        return flakyTests.stream()
            .map(this::createFlakyTestInfo)
            .collect(Collectors.toList());
    }
    
    /**
     * Get test health metrics by category
     * 
     * @return Map of test categories to health metrics
     */
    public Map<String, CategoryHealth> getHealthByCategory() {
        log.info("Generating health metrics by category");
        
        List<TestCase> allTests = testCaseRepository.findAll();
        Map<String, List<TestCase>> testsByCategory = allTests.stream()
            .collect(Collectors.groupingBy(this::getCategoryForTest));
        
        Map<String, CategoryHealth> healthByCategory = new HashMap<>();
        
        // Calculate health metrics for each category
        for (Map.Entry<String, List<TestCase>> entry : testsByCategory.entrySet()) {
            String category = entry.getKey();
            List<TestCase> testsInCategory = entry.getValue();
            
            CategoryHealth health = CategoryHealth.builder()
                .totalTests(testsInCategory.size())
                .passingTests((int) testsInCategory.stream()
                    .filter(t -> t.getStatus() == TestCase.TestStatus.PASSED).count())
                .failingTests((int) testsInCategory.stream()
                    .filter(t -> t.getStatus() == TestCase.TestStatus.FAILED).count())
                .flakyTests(detectFlakyTests(testsInCategory).size())
                .passRate(calculatePassRate(testsInCategory))
                .healthScore(calculateHealthScore(testsInCategory))
                .build();
            
            healthByCategory.put(category, health);
        }
        
        return healthByCategory;
    }
    
    /**
     * Get most frequently failing tests
     * 
     * @param limit Maximum number of tests to return
     * @return List of tests with highest failure counts
     */
    public List<FailureFrequency> getMostFrequentlyFailingTests(int limit) {
        log.info("Finding most frequently failing tests, limit: {}", limit);
        
        List<TestCase> allTests = testCaseRepository.findAll();
        List<FailureFrequency> failureFrequencies = new ArrayList<>();
        
        // For each test, calculate failure frequency
        for (TestCase test : allTests) {
            List<TestExecutionHistory> histories = getTestExecutionHistories(test);
            
            // Skip tests with no execution history
            if (histories.isEmpty()) {
                continue;
            }
            
            // Find the history with the most executions
            TestExecutionHistory history = histories.stream()
                .max((h1, h2) -> Integer.compare(h1.getTotalExecutions(), h2.getTotalExecutions()))
                .orElse(null);
            
            if (history != null && history.getFailedExecutions() > 0) {
                FailureFrequency frequency = FailureFrequency.builder()
                    .testId(test.getId())
                    .testName(test.getName())
                    .className(test.getClassName())
                    .methodName(test.getMethodName())
                    .failureCount(history.getFailedExecutions())
                    .executionCount(history.getTotalExecutions())
                    .failureRate((double) history.getFailedExecutions() / history.getTotalExecutions())
                    .trend(history.getTrend())
                    .build();
                
                failureFrequencies.add(frequency);
            }
        }
        
        // Sort by failure count (descending) and return top 'limit'
        return failureFrequencies.stream()
            .sorted((f1, f2) -> Integer.compare(f2.getFailureCount(), f1.getFailureCount()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Get test execution histories for a test
     */
    private List<TestExecutionHistory> getTestExecutionHistories(TestCase test) {
        // In a real implementation, this would fetch from a repository
        // For now, return an empty or cached list
        return executionHistoryCache.getOrDefault(test.getClassName(), List.of());
    }
    
    /**
     * Detect flaky tests based on execution history
     */
    private List<TestCase> detectFlakyTests(List<TestCase> tests) {
        // A test is considered flaky if it has alternating pass/fail results
        return tests.stream()
            .filter(this::isTestFlaky)
            .collect(Collectors.toList());
    }
    
    /**
     * Check if a test is flaky based on its execution history
     */
    private boolean isTestFlaky(TestCase test) {
        List<TestExecutionHistory> histories = getTestExecutionHistories(test);
        
        // Check for any history with FLAKY trend
        return histories.stream()
            .anyMatch(h -> h.getTrend() == TestExecutionHistory.TestExecutionTrend.FLAKY);
    }
    
    /**
     * Calculate mock pass rate for trending data
     */
    private double calculateMockPassRate(int day, int timeframe) {
        // Generate a somewhat realistic pass rate trend
        double baseRate = 0.85; // 85% base pass rate
        double variation = 0.10; // 10% variation
        
        // Add a slight upward trend over time
        double trend = (double) day / timeframe * 0.05;
        
        // Add some randomness
        double random = (Math.random() * variation * 2) - variation;
        
        return Math.min(1.0, Math.max(0.0, baseRate + trend + random));
    }
    
    /**
     * Calculate pass rate for a list of tests
     */
    private double calculatePassRate(List<TestCase> tests) {
        if (tests == null || tests.isEmpty()) {
            return 0.0;
        }
        
        long passingTests = tests.stream()
            .filter(t -> t.getStatus() == TestCase.TestStatus.PASSED)
            .count();
        
        return (double) passingTests / tests.size();
    }
    
    /**
     * Calculate overall pass rate including historical data
     */
    private double calculateOverallPassRate(List<TestCase> tests) {
        // For now, use current pass rate
        // In a real implementation, this would incorporate historical data
        return calculatePassRate(tests);
    }
    
    /**
     * Calculate test trend (improving, deteriorating, stable)
     */
    private TestTrend calculateTestTrend(List<TestCase> tests) {
        // If we have no tests, return STABLE
        if (tests == null || tests.isEmpty()) {
            return TestTrend.STABLE;
        }
        
        // Calculate pass rate
        double passRate = calculatePassRate(tests);
        
        // For simplicity, use thresholds to determine trend
        if (passRate >= 0.8) {
            return TestTrend.IMPROVING;
        } else if (passRate >= 0.6) {
            return TestTrend.STABLE;
        } else {
            return TestTrend.DETERIORATING;
        }
    }
    
    /**
     * Calculate health score (0-100) based on various metrics
     */
    private int calculateHealthScore(List<TestCase> tests) {
        if (tests == null || tests.isEmpty()) {
            return 0;
        }
        
        // Calculate pass rate
        double passRate = calculatePassRate(tests);
        
        // Calculate flaky test percentage
        double flakyPercentage = (double) detectFlakyTests(tests).size() / tests.size();
        
        // Calculate coverage (mock value for now)
        double coverage = coverageCalculator.calculateOverallCoverage();
        
        // Calculate health score
        // 70% based on pass rate, 20% based on coverage, 10% penalty for flaky tests
        int score = (int) ((passRate * 70) + (coverage * 20) - (flakyPercentage * 10));
        
        // Ensure score is between 0 and 100
        return Math.min(100, Math.max(0, score));
    }
    
    /**
     * Get category for a test (API, UI, Unit, etc.)
     */
    private String getCategoryForTest(TestCase test) {
        if (test.getType() != null) {
            return test.getType().toString();
        }
        
        // Try to infer from class name
        String className = test.getClassName();
        
        if (className.contains("API") || className.contains("Rest")) {
            return "API";
        } else if (className.contains("UI") || className.contains("Web")) {
            return "UI";
        } else if (className.contains("Integration")) {
            return "Integration";
        } else {
            return "Unit";
        }
    }
    
    /**
     * Create flaky test info from test case
     */
    private FlakyTestInfo createFlakyTestInfo(TestCase test) {
        List<TestExecutionHistory> histories = getTestExecutionHistories(test);
        TestExecutionHistory history = !histories.isEmpty() ? histories.get(0) : null;
        
        return FlakyTestInfo.builder()
            .testId(test.getId())
            .testName(test.getName())
            .className(test.getClassName())
            .methodName(test.getMethodName())
            .passRate(history != null ? history.getPassRate() : 0.0)
            .executionCount(history != null ? history.getTotalExecutions() : 0)
            .alternatingSessions(calculateAlternatingSessions(history))
            .lastExecuted(history != null ? history.getLastExecuted() : null)
            .suggestedAction(generateFlakySuggestedAction(test, history))
            .build();
    }
    
    /**
     * Calculate number of alternating pass/fail sessions
     */
    private int calculateAlternatingSessions(TestExecutionHistory history) {
        if (history == null || history.getRecentExecutions() == null || 
            history.getRecentExecutions().size() < 2) {
            return 0;
        }
        
        int alternations = 0;
        boolean lastResult = history.getRecentExecutions().get(0).isSuccessful();
        
        for (int i = 1; i < history.getRecentExecutions().size(); i++) {
            boolean currentResult = history.getRecentExecutions().get(i).isSuccessful();
            if (currentResult != lastResult) {
                alternations++;
                lastResult = currentResult;
            }
        }
        
        return alternations;
    }
    
    /**
     * Generate suggested action for flaky test
     */
    private String generateFlakySuggestedAction(TestCase test, TestExecutionHistory history) {
        if (history == null) {
            return "Investigate test implementation";
        }
        
        // Analyze failure patterns if available
        if (history.getDetectedPatterns() != null && !history.getDetectedPatterns().isEmpty()) {
            // Get most frequent pattern
            TestExecutionHistory.FailurePattern topPattern = history.getDetectedPatterns().stream()
                .max((p1, p2) -> Integer.compare(p1.getOccurrences(), p2.getOccurrences()))
                .orElse(null);
            
            if (topPattern != null) {
                switch (topPattern.getPatternType()) {
                    case "Timeout":
                        return "Increase timeout threshold or optimize test";
                    case "ConcurrentModification":
                        return "Add synchronization or use thread-safe collections";
                    case "Assertion":
                        return "Review assertion conditions, may need tolerance for calculations";
                    case "NullPointer":
                        return "Add null checks and proper initialization";
                    default:
                        return "Fix " + topPattern.getPatternType() + " issue";
                }
            }
        }
        
        // Default suggestions based on test type
        if (test.getClassName().contains("UI") || test.getClassName().contains("Web")) {
            return "Add waits for UI elements to stabilize";
        } else if (test.getClassName().contains("API")) {
            return "Check for race conditions or external dependencies";
        } else {
            return "Investigate race conditions, static state, or resource leaks";
        }
    }
    
    /**
     * Test health trend enum
     */
    public enum TestTrend {
        IMPROVING,
        STABLE,
        DETERIORATING
    }
    
    /**
     * Dashboard summary data
     */
    @Data
    @Builder
    public static class DashboardSummary {
        private int totalTests;
        private int passingTests;
        private int failingTests;
        private int flakyTests;
        private double averagePassRate;
        private double testCoverage;
        private TestTrend testTrend;
        private int healthScore;
    }
    
    /**
     * Test failure trend point
     */
    @Data
    @Builder
    public static class TrendPoint {
        private LocalDateTime timestamp;
        private int totalTests;
        private int failedTests;
        private double passRate;
    }
    
    /**
     * Flaky test information
     */
    @Data
    @Builder
    public static class FlakyTestInfo {
        private java.util.UUID testId;
        private String testName;
        private String className;
        private String methodName;
        private double passRate;
        private int executionCount;
        private int alternatingSessions;
        private LocalDateTime lastExecuted;
        private String suggestedAction;
    }
    
    /**
     * Test failure frequency
     */
    @Data
    @Builder
    public static class FailureFrequency {
        private java.util.UUID testId;
        private String testName;
        private String className;
        private String methodName;
        private int failureCount;
        private int executionCount;
        private double failureRate;
        private TestExecutionHistory.TestExecutionTrend trend;
    }
    
    /**
     * Category health metrics
     */
    @Data
    @Builder
    public static class CategoryHealth {
        private int totalTests;
        private int passingTests;
        private int failingTests;
        private int flakyTests;
        private double passRate;
        private int healthScore;
    }
}