package com.projectoracle.rest;

import com.projectoracle.model.TestCase;
import com.projectoracle.model.TestExecutionHistory.TestExecution;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.service.CodeAnalysisService;
import com.projectoracle.service.EnhancedTestExecutionService;
import com.projectoracle.service.MethodInfo;
import com.projectoracle.service.TestGenerationService;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    
    @Autowired
    private CodeAnalysisService codeAnalysisService;
    
    @Autowired
    private TestGenerationService testGenerationService;

    /**
     * Get all test cases
     */
    @GetMapping
    public ResponseEntity<List<TestCase>> getAllTests() {
        logger.info("Getting all test cases");
        return ResponseEntity.ok(testCaseRepository.findAll());
    }

    /**
     * Get recent test executions
     * 
     * @param limit Maximum number of executions to return (default 5)
     * @return List of recent test executions
     */
    @GetMapping("/recent-executions")
    public ResponseEntity<List<TestExecution>> getRecentExecutions(
            @RequestParam(defaultValue = "5") int limit) {
        logger.info("Getting recent test executions, limit: {}", limit);
        
        // In a real implementation, we would fetch from a repository or service
        // For now, we'll return mock data to satisfy the dashboard.js call
        List<TestExecution> recentExecutions = new ArrayList<>();
        
        // Generate some mock test executions
        String[] testNames = {"loginTest", "dataProcessingTest", "validationTest", "apiIntegrationTest", "databaseTest"};
        
        for (int i = 0; i < Math.min(limit, testNames.length); i++) {
            UUID executionId = UUID.randomUUID();
            LocalDateTime executedAt = LocalDateTime.now().minusMinutes(i * 10);
            boolean successful = Math.random() > 0.3; // 70% pass rate
            long executionTimeMs = (long) (Math.random() * 3000); // 0-3 seconds
            
            TestExecution execution = new TestExecution();
            execution.setId(executionId);
            execution.setExecutedAt(executedAt);
            execution.setSuccessful(successful);
            execution.setExecutionTimeMs(executionTimeMs);
            
            if (!successful) {
                execution.setErrorMessage("Test assertion failed: expected value did not match actual value");
                execution.setStackTrace("at com.projectoracle.test.TestClass.testMethod(" + testNames[i] + ".java:42)");
            }
            
            execution.setCodeVersion("main-" + executedAt.toEpochSecond(ZoneOffset.UTC));
            execution.setEnvironment(new HashMap<>());
            execution.setChangedFiles(new ArrayList<>());
            
            recentExecutions.add(execution);
        }
        
        return ResponseEntity.ok(recentExecutions);
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
        testCaseRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Scan code and generate real tests
     * 
     * @param sourcePath Path to the source code directory or file
     * @param maxTests Maximum number of tests to generate (default 10)
     * @return List of generated and executed test cases
     */
    @PostMapping("/scan")
    public ResponseEntity<List<TestCase>> scanCodeAndGenerateTests(
            @RequestParam String sourcePath,
            @RequestParam(defaultValue = "10") int maxTests) {
        
        logger.info("Scanning code at path: {}, max tests: {}", sourcePath, maxTests);
        
        try {
            Path path = Paths.get(sourcePath);
            if (!Files.exists(path)) {
                logger.error("Source path does not exist: {}", sourcePath);
                return ResponseEntity.badRequest().body(List.of());
            }
            
            // Find Java files to analyze
            List<File> javaFiles;
            if (Files.isDirectory(path)) {
                // Get all Java files recursively
                javaFiles = Files.walk(path)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
            } else if (sourcePath.endsWith(".java")) {
                // Single Java file
                javaFiles = List.of(path.toFile());
            } else {
                logger.error("Source path is not a Java file or directory: {}", sourcePath);
                return ResponseEntity.badRequest().body(List.of());
            }
            
            logger.info("Found {} Java files to analyze", javaFiles.size());
            
            // Analyze files to extract methods
            List<MethodInfo> methods = new ArrayList<>();
            for (File file : javaFiles) {
                try {
                    Map<String, MethodInfo> methodMap = codeAnalysisService.analyzeJavaFile(file.toPath());
                    methods.addAll(methodMap.values());
                } catch (Exception e) {
                    logger.warn("Error analyzing file {}: {}", file.getName(), e.getMessage());
                    // Continue with other files
                }
            }
            
            logger.info("Extracted {} methods for test generation", methods.size());
            
            // Limit the number of methods to process
            int methodsToProcess = Math.min(methods.size(), maxTests);
            methods = methods.subList(0, methodsToProcess);
            
            // Generate tests for each method
            List<TestCase> generatedTests = new ArrayList<>();
            for (MethodInfo method : methods) {
                try {
                    TestCase test = testGenerationService.generateTestForMethod(method);
                    if (test != null) {
                        generatedTests.add(test);
                    }
                } catch (Exception e) {
                    logger.warn("Error generating test for method {}: {}", 
                        method.getMethodName(), e.getMessage());
                    // Continue with other methods
                }
            }
            
            logger.info("Generated {} test cases", generatedTests.size());
            
            // Save tests to repository
            List<TestCase> savedTests = new ArrayList<>();
            for (TestCase test : generatedTests) {
                try {
                    TestCase savedTest = testCaseRepository.save(test);
                    savedTests.add(savedTest);
                } catch (Exception e) {
                    logger.warn("Error saving test: {}", e.getMessage());
                    // Continue with other tests
                }
            }
            
            // Execute the tests
            for (TestCase test : savedTests) {
                try {
                    CompletableFuture.runAsync(() -> {
                        testExecutionService.executeTest(test.getId());
                    });
                } catch (Exception e) {
                    logger.warn("Error executing test: {}", e.getMessage());
                    // Continue with other tests
                }
            }
            
            return ResponseEntity.ok(savedTests);
        } catch (Exception e) {
            logger.error("Error scanning code: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(List.of());
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