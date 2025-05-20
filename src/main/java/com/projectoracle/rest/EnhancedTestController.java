package com.projectoracle.rest;

import com.projectoracle.model.TestCase;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.service.EnhancedTestExecutionService;
import com.projectoracle.service.TestDependencyManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Enhanced controller for test execution operations.
 * Provides enhanced endpoints for executing tests and managing test environments.
 */
@RestController
@RequestMapping("/api/v1/enhanced-tests")
public class EnhancedTestController {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedTestController.class);

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private EnhancedTestExecutionService testExecutionService;

    @Autowired
    private TestDependencyManager dependencyManager;

    /**
     * Initialize the test environment
     */
    @PostMapping("/init-environment")
    public ResponseEntity<String> initializeEnvironment() {
        logger.info("Initializing test environment");

        try {
            dependencyManager.initializeTestEnvironment();
            dependencyManager.extractTestResources();
            return ResponseEntity.ok("Test environment initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize test environment", e);
            return ResponseEntity.internalServerError().body("Failed to initialize test environment: " + e.getMessage());
        }
    }

    /**
     * Execute a test case
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<TestExecutionResponse> executeTest(@PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean waitForResult) {
        logger.info("Executing test case: {}", id);

        TestCase testCase = testCaseRepository.findById(id);
        if (testCase == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            // Start execution asynchronously
            CompletableFuture<TestCase> future = testExecutionService.executeTest(id);

            if (waitForResult) {
                // Wait for the result if requested
                try {
                    TestCase result = future.get(30, TimeUnit.SECONDS);
                    return ResponseEntity.ok(new TestExecutionResponse(
                            "completed",
                            "Test execution completed",
                            result.getStatus().toString(),
                            result.getLastExecutionResult()
                    ));
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    logger.error("Error waiting for test execution: {}", id, e);
                    return ResponseEntity.ok(new TestExecutionResponse(
                            "error",
                            "Error executing test: " + e.getMessage(),
                            null,
                            null
                    ));
                }
            } else {
                // Return immediately
                return ResponseEntity.accepted().body(new TestExecutionResponse(
                        "started",
                        "Test execution started",
                        testCase.getStatus().toString(),
                        null
                ));
            }
        } catch (Exception e) {
            logger.error("Error executing test case: {}", id, e);
            return ResponseEntity.internalServerError().body(new TestExecutionResponse(
                    "error",
                    "Error executing test: " + e.getMessage(),
                    null,
                    null
            ));
        }
    }

    /**
     * Execute all test cases with a specific status
     */
    @PostMapping("/execute/byStatus")
    public ResponseEntity<BatchExecutionResponse> executeAllWithStatus(
            @RequestParam TestCase.TestStatus status) {
        logger.info("Executing all test cases with status: {}", status);

        List<TestCase> testCases = testCaseRepository.findByStatus(status);
        if (testCases.isEmpty()) {
            return ResponseEntity.ok(new BatchExecutionResponse(
                    "no_tests",
                    "No tests found with status: " + status,
                    0,
                    0
            ));
        }

        try {
            // Start execution asynchronously
            CompletableFuture<List<TestCase>> future = testExecutionService.executeAllWithStatus(status);

            // Return immediately
            return ResponseEntity.accepted().body(new BatchExecutionResponse(
                    "started",
                    "Batch execution started for " + testCases.size() + " tests",
                    testCases.size(),
                    0
            ));
        } catch (Exception e) {
            logger.error("Error executing test cases with status: {}", status, e);
            return ResponseEntity.internalServerError().body(new BatchExecutionResponse(
                    "error",
                    "Error executing tests: " + e.getMessage(),
                    testCases.size(),
                    0
            ));
        }
    }

    /**
     * Get the status of a batch execution
     */
    @GetMapping("/batch/{batchId}/status")
    public ResponseEntity<BatchExecutionResponse> getBatchStatus(@PathVariable String batchId) {
        // This would typically retrieve the status from a batch tracking system
        // For now, we'll return a placeholder
        return ResponseEntity.ok(new BatchExecutionResponse(
                "in_progress",
                "Batch execution in progress",
                10,
                5
        ));
    }

    /**
     * Response class for test execution
     */
    public static class TestExecutionResponse {
        private String status;
        private String message;
        private String testStatus;
        private TestCase.TestExecutionResult result;

        public TestExecutionResponse(String status, String message, String testStatus,
                TestCase.TestExecutionResult result) {
            this.status = status;
            this.message = message;
            this.testStatus = testStatus;
            this.result = result;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public String getTestStatus() {
            return testStatus;
        }

        public TestCase.TestExecutionResult getResult() {
            return result;
        }
    }

    /**
     * Response class for batch execution
     */
    public static class BatchExecutionResponse {
        private String status;
        private String message;
        private int totalTests;
        private int completedTests;

        public BatchExecutionResponse(String status, String message, int totalTests, int completedTests) {
            this.status = status;
            this.message = message;
            this.totalTests = totalTests;
            this.completedTests = completedTests;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public int getTotalTests() {
            return totalTests;
        }

        public int getCompletedTests() {
            return completedTests;
        }
    }
}