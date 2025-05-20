package com.projectoracle.service.reporting;

import com.projectoracle.model.TestCase;
import com.projectoracle.repository.TestCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analyzing test results and providing insights.
 * Extracts patterns, trends, and statistics from test executions.
 */
@Service
public class TestResultAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(TestResultAnalyzer.class);

    @Autowired
    private TestCaseRepository testCaseRepository;

    /**
     * Analyze test results and generate a comprehensive report.
     *
     * @return a TestResultReport with insights and statistics
     */
    public TestResultReport analyzeResults() {
        logger.info("Analyzing test results");

        List<TestCase> allTests = testCaseRepository.findAll();

        // Create a new test result report
        TestResultReport report = new TestResultReport();
        report.setGeneratedAt(LocalDateTime.now());
        report.setTotalTests(allTests.size());

        // Calculate success metrics
        Map<TestCase.TestStatus, Long> statusCounts = allTests.stream()
                                                              .collect(Collectors.groupingBy(TestCase::getStatus, Collectors.counting()));

        report.setPassedTests(statusCounts.getOrDefault(TestCase.TestStatus.PASSED, 0L).intValue());
        report.setFailedTests(statusCounts.getOrDefault(TestCase.TestStatus.FAILED, 0L).intValue());
        report.setBrokenTests(statusCounts.getOrDefault(TestCase.TestStatus.BROKEN, 0L).intValue());
        report.setHealedTests(statusCounts.getOrDefault(TestCase.TestStatus.HEALED, 0L).intValue());

        // Calculate success rate
        if (allTests.size() > 0) {
            double successRate = (double) report.getPassedTests() / allTests.size() * 100;
            report.setSuccessRate(Math.round(successRate * 100.0) / 100.0); // Round to 2 decimal places
        }

        // Calculate test type distribution
        Map<TestCase.TestType, Long> testTypeCounts = allTests.stream()
                                                              .collect(Collectors.groupingBy(TestCase::getType, Collectors.counting()));
        report.setTestTypeDistribution(testTypeCounts);

        // Find most frequent failures
        List<TestCase> failedTests = allTests.stream()
                                             .filter(t -> t.getStatus() == TestCase.TestStatus.FAILED)
                                             .collect(Collectors.toList());

        // Group failed tests by error message patterns
        Map<String, List<TestCase>> failureGroups = groupFailuresByErrorPattern(failedTests);
        List<FailurePattern> topFailurePatterns = failureGroups.entrySet().stream()
                                                               .map(entry -> new FailurePattern(entry.getKey(), entry.getValue().size(), entry.getValue()))
                                                               .sorted(Comparator.comparing(FailurePattern::getOccurrences).reversed())
                                                               .limit(10)
                                                               .collect(Collectors.toList());

        report.setTopFailurePatterns(topFailurePatterns);

        // Calculate recent trends
        calculateRecentTrends(allTests, report);

        // Calculate test execution times
        calculateExecutionMetrics(allTests, report);

        // Find flaky tests (tests that sometimes pass, sometimes fail)
        identifyFlakyTests(allTests, report);

        logger.info("Test result analysis completed");
        return report;
    }

    /**
     * Group failures by similar error patterns
     */
    private Map<String, List<TestCase>> groupFailuresByErrorPattern(List<TestCase> failedTests) {
        Map<String, List<TestCase>> failureGroups = new HashMap<>();

        for (TestCase test : failedTests) {
            if (test.getLastExecutionResult() == null || test.getLastExecutionResult().getErrorMessage() == null) {
                continue;
            }

            String errorMessage = test.getLastExecutionResult().getErrorMessage();
            String pattern = extractErrorPattern(errorMessage);

            failureGroups.computeIfAbsent(pattern, k -> new ArrayList<>()).add(test);
        }

        return failureGroups;
    }

    /**
     * Extract a pattern from an error message by removing specific details
     */
    private String extractErrorPattern(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return "Unknown error";
        }

        // Simplify error messages by removing specifics
        // This is a simple implementation - a more sophisticated approach would use
        // NLP or regex patterns to identify common error types

        // Remove line numbers
        String pattern = errorMessage.replaceAll("at line \\d+", "at line X");

        // Remove specific element identifiers
        pattern = pattern.replaceAll("Element \\[[^\\]]+\\]", "Element [X]");

        // Remove specific exception details
        pattern = pattern.replaceAll("(Exception|Error): [\\w\\s.]+ in class", "$1: X in class");

        // Truncate to keep only the main error part
        int endIndex = pattern.indexOf('\n');
        if (endIndex > 0) {
            pattern = pattern.substring(0, endIndex);
        }

        // Limit length
        if (pattern.length() > 100) {
            pattern = pattern.substring(0, 100) + "...";
        }

        return pattern;
    }

    /**
     * Calculate recent execution trends
     */
    private void calculateRecentTrends(List<TestCase> allTests, TestResultReport report) {
        // Get tests with execution results
        List<TestCase> executedTests = allTests.stream()
                                               .filter(t -> t.getLastExecutedAt() != null)
                                               .sorted(Comparator.comparing(TestCase::getLastExecutedAt).reversed())
                                               .collect(Collectors.toList());

        if (executedTests.isEmpty()) {
            return;
        }

        // Recent execution trend (last 7 days)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(7);

        Map<String, TrendPoint> dailyTrends = new HashMap<>();

        for (int i = 0; i < 7; i++) {
            LocalDateTime day = now.minusDays(i);
            String dateStr = day.toLocalDate().toString();
            dailyTrends.put(dateStr, new TrendPoint(dateStr, 0, 0));
        }

        // Calculate daily success/failure counts
        for (TestCase test : executedTests) {
            if (test.getLastExecutedAt().isAfter(sevenDaysAgo)) {
                String dateStr = test.getLastExecutedAt().toLocalDate().toString();
                TrendPoint point = dailyTrends.get(dateStr);

                if (point != null) {
                    if (test.getStatus() == TestCase.TestStatus.PASSED) {
                        point.setSuccess(point.getSuccess() + 1);
                    } else if (test.getStatus() == TestCase.TestStatus.FAILED ||
                            test.getStatus() == TestCase.TestStatus.BROKEN) {
                        point.setFailure(point.getFailure() + 1);
                    }
                }
            }
        }

        report.setDailyTrends(new ArrayList<>(dailyTrends.values()));

        // Calculate success trend
        double currentSuccessRate = report.getSuccessRate();

        // Calculate previous period success rate (7-14 days ago)
        LocalDateTime fourteenDaysAgo = now.minusDays(14);

        long previousPeriodTotal = executedTests.stream()
                                                .filter(t -> t.getLastExecutedAt().isAfter(fourteenDaysAgo) &&
                                                        t.getLastExecutedAt().isBefore(sevenDaysAgo))
                                                .count();

        long previousPeriodPassed = executedTests.stream()
                                                 .filter(t -> t.getLastExecutedAt().isAfter(fourteenDaysAgo) &&
                                                         t.getLastExecutedAt().isBefore(sevenDaysAgo) &&
                                                         t.getStatus() == TestCase.TestStatus.PASSED)
                                                 .count();

        double previousSuccessRate = 0;
        if (previousPeriodTotal > 0) {
            previousSuccessRate = (double) previousPeriodPassed / previousPeriodTotal * 100;
        }

        report.setSuccessRateTrend(currentSuccessRate - previousSuccessRate);
    }

    /**
     * Calculate test execution time metrics
     */
    private void calculateExecutionMetrics(List<TestCase> allTests, TestResultReport report) {
        List<TestCase> executedTests = allTests.stream()
                                               .filter(t -> t.getLastExecutionResult() != null)
                                               .collect(Collectors.toList());

        if (executedTests.isEmpty()) {
            return;
        }

        // Calculate average execution time
        double avgExecutionTime = executedTests.stream()
                                               .mapToLong(t -> t.getLastExecutionResult().getExecutionTimeMs())
                                               .average()
                                               .orElse(0);

        report.setAverageExecutionTimeMs((long) avgExecutionTime);

        // Find slowest tests
        List<TestCase> slowestTests = executedTests.stream()
                                                   .sorted(Comparator.comparing(t -> t.getLastExecutionResult().getExecutionTimeMs(), Comparator.reverseOrder()))
                                                   .limit(10)
                                                   .collect(Collectors.toList());

        report.setSlowestTests(slowestTests);
    }

    /**
     * Identify flaky tests (tests that sometimes pass, sometimes fail)
     */
    private void identifyFlakyTests(List<TestCase> allTests, TestResultReport report) {
        // In a real implementation, we would track test execution history
        // For now, we'll identify potentially flaky tests based on their healing history

        List<TestCase> healedTests = allTests.stream()
                                             .filter(t -> t.getStatus() == TestCase.TestStatus.HEALED)
                                             .collect(Collectors.toList());

        report.setPotentiallyFlakyTests(healedTests);
    }

    /**
     * Test Result Report class to hold analysis results
     */
    public static class TestResultReport {
        private LocalDateTime generatedAt;
        private int totalTests;
        private int passedTests;
        private int failedTests;
        private int brokenTests;
        private int healedTests;
        private double successRate;
        private double successRateTrend;
        private Map<TestCase.TestType, Long> testTypeDistribution;
        private List<FailurePattern> topFailurePatterns;
        private List<TrendPoint> dailyTrends;
        private long averageExecutionTimeMs;
        private List<TestCase> slowestTests;
        private List<TestCase> potentiallyFlakyTests;

        // Getters and setters
        public LocalDateTime getGeneratedAt() {
            return generatedAt;
        }

        public void setGeneratedAt(LocalDateTime generatedAt) {
            this.generatedAt = generatedAt;
        }

        public int getTotalTests() {
            return totalTests;
        }

        public void setTotalTests(int totalTests) {
            this.totalTests = totalTests;
        }

        public int getPassedTests() {
            return passedTests;
        }

        public void setPassedTests(int passedTests) {
            this.passedTests = passedTests;
        }

        public int getFailedTests() {
            return failedTests;
        }

        public void setFailedTests(int failedTests) {
            this.failedTests = failedTests;
        }

        public int getBrokenTests() {
            return brokenTests;
        }

        public void setBrokenTests(int brokenTests) {
            this.brokenTests = brokenTests;
        }

        public int getHealedTests() {
            return healedTests;
        }

        public void setHealedTests(int healedTests) {
            this.healedTests = healedTests;
        }

        public double getSuccessRate() {
            return successRate;
        }

        public void setSuccessRate(double successRate) {
            this.successRate = successRate;
        }

        public double getSuccessRateTrend() {
            return successRateTrend;
        }

        public void setSuccessRateTrend(double successRateTrend) {
            this.successRateTrend = successRateTrend;
        }

        public Map<TestCase.TestType, Long> getTestTypeDistribution() {
            return testTypeDistribution;
        }

        public void setTestTypeDistribution(Map<TestCase.TestType, Long> testTypeDistribution) {
            this.testTypeDistribution = testTypeDistribution;
        }

        public List<FailurePattern> getTopFailurePatterns() {
            return topFailurePatterns;
        }

        public void setTopFailurePatterns(List<FailurePattern> topFailurePatterns) {
            this.topFailurePatterns = topFailurePatterns;
        }

        public List<TrendPoint> getDailyTrends() {
            return dailyTrends;
        }

        public void setDailyTrends(List<TrendPoint> dailyTrends) {
            this.dailyTrends = dailyTrends;
        }

        public long getAverageExecutionTimeMs() {
            return averageExecutionTimeMs;
        }

        public void setAverageExecutionTimeMs(long averageExecutionTimeMs) {
            this.averageExecutionTimeMs = averageExecutionTimeMs;
        }

        public List<TestCase> getSlowestTests() {
            return slowestTests;
        }

        public void setSlowestTests(List<TestCase> slowestTests) {
            this.slowestTests = slowestTests;
        }

        public List<TestCase> getPotentiallyFlakyTests() {
            return potentiallyFlakyTests;
        }

        public void setPotentiallyFlakyTests(List<TestCase> potentiallyFlakyTests) {
            this.potentiallyFlakyTests = potentiallyFlakyTests;
        }
    }

    /**
     * Represents a pattern of test failures
     */
    public static class FailurePattern {
        private String pattern;
        private int occurrences;
        private List<TestCase> affectedTests;

        public FailurePattern(String pattern, int occurrences, List<TestCase> affectedTests) {
            this.pattern = pattern;
            this.occurrences = occurrences;
            this.affectedTests = affectedTests;
        }

        public String getPattern() {
            return pattern;
        }

        public int getOccurrences() {
            return occurrences;
        }

        public List<TestCase> getAffectedTests() {
            return affectedTests;
        }
    }

    /**
     * Represents a point in a trend line
     */
    public static class TrendPoint {
        private String date;
        private int success;
        private int failure;

        public TrendPoint(String date, int success, int failure) {
            this.date = date;
            this.success = success;
            this.failure = failure;
        }

        public String getDate() {
            return date;
        }

        public int getSuccess() {
            return success;
        }

        public void setSuccess(int success) {
            this.success = success;
        }

        public int getFailure() {
            return failure;
        }

        public void setFailure(int failure) {
            this.failure = failure;
        }
    }
}