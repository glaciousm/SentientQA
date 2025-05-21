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
     * @param useRealData Whether to use real data or allow fallback to mock data
     * @return Dashboard summary metrics
     */
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummary> getDashboardSummary(
            @RequestParam(required = false, defaultValue = "false") boolean useRealData) {
        log.info("Getting dashboard summary, useRealData={}", useRealData);
        
        DashboardSummary summary = qualityMetricsService.getDashboardSummary(useRealData);
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Get failure trends data
     * 
     * @param timeframe Timeframe in days (7, 30, 90)
     * @param useRealData Whether to use real data or allow fallback to mock data
     * @return List of trend points
     */
    @GetMapping("/trends")
    public ResponseEntity<List<TrendPoint>> getFailureTrends(
            @RequestParam(defaultValue = "30") int timeframe,
            @RequestParam(required = false, defaultValue = "false") boolean useRealData) {
        
        log.info("Getting failure trends for {} days, useRealData={}", timeframe, useRealData);
        
        // Limit timeframe to supported values
        int validTimeframe = validateTimeframe(timeframe);
        
        List<TrendPoint> trends = qualityMetricsService.getFailureTrends(validTimeframe, useRealData);
        return ResponseEntity.ok(trends);
    }
    
    /**
     * Get flaky test report
     * 
     * @param useRealData Whether to use real data or allow fallback to mock data
     * @return List of flaky tests with details
     */
    @GetMapping("/flaky-tests")
    public ResponseEntity<List<FlakyTestInfo>> getFlakyTestReport(
            @RequestParam(required = false, defaultValue = "false") boolean useRealData) {
        log.info("Getting flaky test report, useRealData={}", useRealData);
        
        List<FlakyTestInfo> flakyTests = qualityMetricsService.getFlakyTestReport(useRealData);
        return ResponseEntity.ok(flakyTests);
    }
    
    /**
     * Get test health metrics by category
     * 
     * @param useRealData Whether to use real data or allow fallback to mock data
     * @return Map of test categories to health metrics
     */
    @GetMapping("/health-by-category")
    public ResponseEntity<Map<String, CategoryHealth>> getHealthByCategory(
            @RequestParam(required = false, defaultValue = "false") boolean useRealData) {
        log.info("Getting health metrics by category, useRealData={}", useRealData);
        
        Map<String, CategoryHealth> healthByCategory = qualityMetricsService.getHealthByCategory(useRealData);
        return ResponseEntity.ok(healthByCategory);
    }
    
    /**
     * Get most frequently failing tests
     * 
     * @param limit Maximum number of tests to return
     * @param useRealData Whether to use real data or allow fallback to mock data
     * @return List of tests with highest failure counts
     */
    @GetMapping("/most-failing")
    public ResponseEntity<List<FailureFrequency>> getMostFrequentlyFailingTests(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false, defaultValue = "false") boolean useRealData) {
        
        log.info("Getting most frequently failing tests, limit: {}, useRealData={}", limit, useRealData);
        
        // Ensure limit is reasonable
        int validLimit = Math.min(50, Math.max(1, limit));
        
        List<FailureFrequency> failingTests = qualityMetricsService.getMostFrequentlyFailingTests(validLimit, useRealData);
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