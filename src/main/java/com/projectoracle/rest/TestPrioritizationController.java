package com.projectoracle.rest;

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
}