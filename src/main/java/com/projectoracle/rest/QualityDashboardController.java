package com.projectoracle.rest;

import com.projectoracle.service.reporting.QualityMetricsService;
import com.projectoracle.service.reporting.QualityMetricsService.CategoryHealth;
import com.projectoracle.service.reporting.QualityMetricsService.DashboardSummary;
import com.projectoracle.service.reporting.QualityMetricsService.FailureFrequency;
import com.projectoracle.service.reporting.QualityMetricsService.FlakyTestInfo;
import com.projectoracle.service.reporting.QualityMetricsService.TrendPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Quality Intelligence Dashboard endpoints.
 * Provides API access to quality metrics and dashboards.
 */
@RestController
@RequestMapping("/api/v1/quality-dashboard")
@Slf4j
@RequiredArgsConstructor
public class QualityDashboardController {

    private final QualityMetricsService qualityMetricsService;

    /**
     * Get dashboard summary data
     * 
     * @return Dashboard summary metrics
     */
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummary> getDashboardSummary() {
        log.info("Getting dashboard summary");
        
        DashboardSummary summary = qualityMetricsService.getDashboardSummary();
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Get failure trends data
     * 
     * @param timeframe Timeframe in days (7, 30, 90)
     * @return List of trend points
     */
    @GetMapping("/trends")
    public ResponseEntity<List<TrendPoint>> getFailureTrends(
            @RequestParam(defaultValue = "30") int timeframe) {
        
        log.info("Getting failure trends for {} days", timeframe);
        
        // Limit timeframe to supported values
        int validTimeframe = validateTimeframe(timeframe);
        
        List<TrendPoint> trends = qualityMetricsService.getFailureTrends(validTimeframe);
        return ResponseEntity.ok(trends);
    }
    
    /**
     * Get flaky test report
     * 
     * @return List of flaky tests with details
     */
    @GetMapping("/flaky-tests")
    public ResponseEntity<List<FlakyTestInfo>> getFlakyTestReport() {
        log.info("Getting flaky test report");
        
        List<FlakyTestInfo> flakyTests = qualityMetricsService.getFlakyTestReport();
        return ResponseEntity.ok(flakyTests);
    }
    
    /**
     * Get test health metrics by category
     * 
     * @return Map of test categories to health metrics
     */
    @GetMapping("/health-by-category")
    public ResponseEntity<Map<String, CategoryHealth>> getHealthByCategory() {
        log.info("Getting health metrics by category");
        
        Map<String, CategoryHealth> healthByCategory = qualityMetricsService.getHealthByCategory();
        return ResponseEntity.ok(healthByCategory);
    }
    
    /**
     * Get most frequently failing tests
     * 
     * @param limit Maximum number of tests to return
     * @return List of tests with highest failure counts
     */
    @GetMapping("/most-failing")
    public ResponseEntity<List<FailureFrequency>> getMostFrequentlyFailingTests(
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("Getting most frequently failing tests, limit: {}", limit);
        
        // Ensure limit is reasonable
        int validLimit = Math.min(50, Math.max(1, limit));
        
        List<FailureFrequency> failingTests = qualityMetricsService.getMostFrequentlyFailingTests(validLimit);
        return ResponseEntity.ok(failingTests);
    }
    
    /**
     * Validate timeframe parameter
     * 
     * @param timeframe Requested timeframe
     * @return Validated timeframe (7, 30, or 90)
     */
    private int validateTimeframe(int timeframe) {
        // Only support specific timeframes: 7, 30, 90 days
        if (timeframe <= 7) {
            return 7;
        } else if (timeframe <= 30) {
            return 30;
        } else {
            return 90;
        }
    }
}