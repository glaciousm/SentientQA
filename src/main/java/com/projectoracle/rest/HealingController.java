package com.projectoracle.rest;

import com.projectoracle.model.TestCase;
import com.projectoracle.service.TestHealingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for test healing endpoints.
 * Provides API access to test healing functionality.
 */
@RestController
@RequestMapping("/api/v1/healing")
public class HealingController {

    private static final Logger logger = LoggerFactory.getLogger(HealingController.class);

    @Autowired
    private TestHealingService testHealingService;

    /**
     * Heal a specific test case
     */
    @PostMapping("/test/{id}")
    public ResponseEntity<TestCase> healTest(@PathVariable UUID id) {
        logger.info("Healing test case: {}", id);

        try {
            // Start healing asynchronously
            CompletableFuture<TestCase> future = testHealingService.healTest(id);

            // Return the test case with updated status immediately
            // The healing will continue in the background
            return ResponseEntity.accepted().build();
        } catch (Exception e) {
            logger.error("Error healing test case: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Heal all broken tests
     */
    @PostMapping("/all")
    public ResponseEntity<Void> healAllBrokenTests() {
        logger.info("Healing all broken tests");

        try {
            // Start healing asynchronously
            CompletableFuture<List<TestCase>> future = testHealingService.healAllBrokenTests();

            // The healing will continue in the background
            return ResponseEntity.accepted().build();
        } catch (Exception e) {
            logger.error("Error healing broken tests", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Analyze the impact of code changes on existing tests
     */
    @PostMapping("/analyze-impact")
    public ResponseEntity<List<TestCase>> analyzeChangeImpact(
            @RequestParam(required = false) String oldFilePath,
            @RequestParam(required = false) String newFilePath,
            @RequestBody(required = false) ImpactAnalysisRequest request) {

        logger.info("Analyzing impact of code changes");

        try {
            // Use file paths if provided, otherwise use request body
            String oldCode = request != null ? request.getOldSourceCode() : null;
            String newCode = request != null ? request.getNewSourceCode() : null;

            // Analyze impact
            List<TestCase> affectedTests = testHealingService.analyzeChangeImpact(oldCode, newCode);

            return ResponseEntity.ok(affectedTests);
        } catch (Exception e) {
            logger.error("Error analyzing change impact", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Request object for impact analysis
     */
    public static class ImpactAnalysisRequest {
        private String oldSourceCode;
        private String newSourceCode;

        public String getOldSourceCode() {
            return oldSourceCode;
        }

        public void setOldSourceCode(String oldSourceCode) {
            this.oldSourceCode = oldSourceCode;
        }

        public String getNewSourceCode() {
            return newSourceCode;
        }

        public void setNewSourceCode(String newSourceCode) {
            this.newSourceCode = newSourceCode;
        }
    }
}