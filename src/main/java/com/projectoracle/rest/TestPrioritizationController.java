package com.projectoracle.rest;

import com.projectoracle.model.TestCase;
import com.projectoracle.model.TestPriorityConfig;
import com.projectoracle.service.TestPrioritizationService;
import com.projectoracle.service.TestPrioritizationService.PrioritizedSuggestion;
import com.projectoracle.service.TestPrioritizationService.PriorityStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for test prioritization endpoints.
 * Provides API access for prioritizing test suggestions.
 */
@RestController
@RequestMapping("/api/v1/priorities")
@Slf4j
@RequiredArgsConstructor
public class TestPrioritizationController {

    private final TestPrioritizationService prioritizationService;

    /**
     * Get prioritized test suggestions for a class
     */
    @GetMapping("/class")
    public ResponseEntity<List<PrioritizedSuggestion>> getPrioritizedSuggestions(@RequestParam String className) {
        log.info("Getting prioritized test suggestions for class: {}", className);

        List<PrioritizedSuggestion> suggestions = prioritizationService.prioritizeSuggestions(className);
        return ResponseEntity.ok(suggestions);
    }

    /**
     * Get priority statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<PriorityStats> getPriorityStats() {
        log.info("Getting priority statistics");

        PriorityStats stats = prioritizationService.getPriorityStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Prioritize test cases for execution
     * 
     * @param tests Test cases to prioritize
     * @param changedFiles Files that have changed since last execution
     * @return Prioritized list of test cases
     */
    @PostMapping("/execution")
    public ResponseEntity<List<TestCase>> prioritizeTestExecution(
            @RequestBody List<TestCase> tests,
            @RequestParam(required = false) List<String> changedFiles) {
        
        log.info("Prioritizing {} tests for execution", tests.size());
        
        List<TestCase> prioritizedTests = prioritizationService.prioritizeTestExecution(tests, changedFiles);
        return ResponseEntity.ok(prioritizedTests);
    }
    
    /**
     * Get ordered execution plan respecting test dependencies
     * 
     * @param tests Test cases to order
     * @return Ordered test execution plan
     */
    @PostMapping("/execution-plan")
    public ResponseEntity<List<TestCase>> getExecutionPlan(@RequestBody List<TestCase> tests) {
        log.info("Creating execution plan for {} tests", tests.size());
        
        List<TestCase> orderedTests = prioritizationService.getOrderedExecutionPlan(tests);
        return ResponseEntity.ok(orderedTests);
    }
    
    /**
     * Get current prioritization configuration
     * 
     * @return Test priority configuration
     */
    @GetMapping("/config")
    public ResponseEntity<TestPriorityConfig> getPriorityConfig() {
        log.info("Getting test priority configuration");
        
        TestPriorityConfig config = prioritizationService.getConfig();
        return ResponseEntity.ok(config);
    }
    
    /**
     * Update prioritization configuration
     * 
     * @param config New priority configuration
     * @return Updated configuration
     */
    @PutMapping("/config")
    public ResponseEntity<TestPriorityConfig> updatePriorityConfig(@RequestBody TestPriorityConfig config) {
        log.info("Updating test priority configuration");
        
        prioritizationService.setConfig(config);
        return ResponseEntity.ok(config);
    }
}