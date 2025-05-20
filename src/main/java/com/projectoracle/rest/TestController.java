package com.projectoracle.rest;

import com.projectoracle.model.TestCase;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.service.EnhancedTestExecutionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for test case management and execution.
 */
@RestController
@RequestMapping("/api/v1/tests")
public class TestController {

    private static final Logger logger = LoggerFactory.getLogger(TestController.class);

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private EnhancedTestExecutionService testExecutionService;

    /**
     * Get all test cases
     */
    @GetMapping
    public ResponseEntity<List<TestCase>> getAllTests() {
        logger.info("Getting all test cases");
        return ResponseEntity.ok(testCaseRepository.findAll());
    }

    /**
     * Get a test case by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<TestCase> getTestById(@PathVariable UUID id) {
        logger.info("Getting test case: {}", id);
        TestCase testCase = testCaseRepository.findById(id);
        if (testCase == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(testCase);
    }

    /**
     * Save a test case
     */
    @PostMapping
    public ResponseEntity<TestCase> saveTest(@RequestBody TestCase testCase) {
        if (testCase == null) {
            return ResponseEntity.badRequest().build();
        }
        
        if (testCase.getName() == null || testCase.getName().trim().isEmpty()) {
            logger.warn("Attempted to save test case with empty name");
            throw new IllegalArgumentException("Test case name cannot be empty");
        }
        
        logger.info("Saving test case: {}", testCase.getName());
        try {
            TestCase savedTestCase = testCaseRepository.save(testCase);
            return ResponseEntity.ok(savedTestCase);
        } catch (Exception e) {
            logger.error("Error saving test case: {}", e.getMessage(), e);
            throw e; // Global handler will catch this
        }
    }

    /**
     * Update a test case
     */
    @PutMapping("/{id}")
    public ResponseEntity<TestCase> updateTest(@PathVariable UUID id, @RequestBody TestCase testCase) {
        logger.info("Updating test case: {}", id);

        if (!id.equals(testCase.getId())) {
            return ResponseEntity.badRequest().build();
        }

        if (testCaseRepository.findById(id) == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(testCaseRepository.save(testCase));
    }

    /**
     * Delete a test case
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTest(@PathVariable UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Test case ID cannot be null");
        }
        
        logger.info("Deleting test case: {}", id);

        // Check if test case exists
        TestCase testCase = testCaseRepository.findById(id);
        if (testCase == null) {
            logger.warn("Test case not found for deletion: {}", id);
            return ResponseEntity.notFound().build();
        }

        try {
            testCaseRepository.deleteById(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error deleting test case: {}", e.getMessage(), e);
            throw e; // Global handler will catch this
        }
    }

    /**
     * Find test cases by class name
     */
    @GetMapping("/search/byClass")
    public ResponseEntity<List<TestCase>> findByClassName(@RequestParam String className) {
        logger.info("Finding test cases by class name: {}", className);
        return ResponseEntity.ok(testCaseRepository.findByClassName(className));
    }

    /**
     * Find test cases by package
     */
    @GetMapping("/search/byPackage")
    public ResponseEntity<List<TestCase>> findByPackage(@RequestParam String packageName) {
        logger.info("Finding test cases by package: {}", packageName);
        return ResponseEntity.ok(testCaseRepository.findByPackage(packageName));
    }

    /**
     * Find test cases by status
     */
    @GetMapping("/search/byStatus")
    public ResponseEntity<List<TestCase>> findByStatus(@RequestParam TestCase.TestStatus status) {
        logger.info("Finding test cases by status: {}", status);
        return ResponseEntity.ok(testCaseRepository.findByStatus(status));
    }

    /**
     * Execute a test case
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<TestCase> executeTest(@PathVariable UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Test case ID cannot be null");
        }
        
        logger.info("Executing test case: {}", id);

        // Validate test case exists
        TestCase testCase = testCaseRepository.findById(id);
        if (testCase == null) {
            logger.warn("Test case not found for execution: {}", id);
            return ResponseEntity.notFound().build();
        }
        
        // Validate test case has source code
        if (testCase.getSourceCode() == null || testCase.getSourceCode().trim().isEmpty()) {
            logger.error("Cannot execute test with empty source code: {}", id);
            throw new IllegalArgumentException("Cannot execute test with empty source code");
        }

        try {
            // Track current thread for diagnostics
            String threadName = Thread.currentThread().getName();
            logger.debug("Starting test execution on thread: {}", threadName);
            
            // Start execution asynchronously
            CompletableFuture<TestCase> future = testExecutionService.executeTest(id);

            // Return the test case with updated status immediately
            // The execution will continue in the background
            return ResponseEntity.accepted().body(testCaseRepository.findById(id));
        } catch (Exception e) {
            logger.error("Error executing test case: {}", id, e);
            throw new RuntimeException("Failed to execute test: " + e.getMessage(), e);
        }
    }

    /**
     * Execute all test cases with a specific status
     */
    @PostMapping("/execute/byStatus")
    public ResponseEntity<List<TestCase>> executeAllWithStatus(@RequestParam TestCase.TestStatus status) {
        logger.info("Executing all test cases with status: {}", status);

        try {
            // Start execution asynchronously
            CompletableFuture<List<TestCase>> future = testExecutionService.executeAllWithStatus(status);

            // Return the test cases with updated status immediately
            // The execution will continue in the background
            return ResponseEntity.ok(testCaseRepository.findByStatus(status));
        } catch (Exception e) {
            logger.error("Error executing test cases with status: {}", status, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}