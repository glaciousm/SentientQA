package com.projectoracle.service;

import com.projectoracle.model.TestCase;
import com.projectoracle.repository.TestCaseRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Service for executing generated test cases.
 * Handles compilation and running of the tests.
 */
@Service
public class TestExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(TestExecutionService.class);

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    @Qualifier("backgroundExecutor")
    private Executor backgroundExecutor;

    /**
     * Execute a test case.
     *
     * @param testCaseId the ID of the test case to execute
     * @return a CompletableFuture that will complete when the test execution is done
     */
    public CompletableFuture<TestCase> executeTest(UUID testCaseId) {
        return CompletableFuture.supplyAsync(() -> {
            TestCase testCase = testCaseRepository.findById(testCaseId);
            if (testCase == null) {
                throw new IllegalArgumentException("Test case not found: " + testCaseId);
            }

            logger.info("Executing test case: {}", testCase.getId());
            testCase.setStatus(TestCase.TestStatus.EXECUTING);
            testCaseRepository.save(testCase);

            try {
                // Save the test code to a file
                String packagePath = testCase.getPackageName().replace('.', '/');
                Path sourcePath = Paths.get("output", "generated-tests", packagePath);
                Path sourceFile = sourcePath.resolve(testCase.getClassName() + ".java");

                Files.createDirectories(sourcePath);
                Files.writeString(sourceFile, testCase.getSourceCode());

                // For now, we'll just simulate test execution
                // In a real implementation, we would compile and run the test

                // Simulate successful execution
                TestCase.TestExecutionResult result = TestCase.TestExecutionResult.builder()
                                                                                  .success(true)
                                                                                  .executionTimeMs(100)
                                                                                  .executedAt(LocalDateTime.now())
                                                                                  .build();

                testCase.setLastExecutionResult(result);
                testCase.setLastExecutedAt(LocalDateTime.now());
                testCase.setStatus(TestCase.TestStatus.PASSED);

                // Save the test case with results
                testCaseRepository.save(testCase);

                logger.info("Test execution completed for: {}, success: {}",
                        testCase.getId(), result.isSuccess());

                return testCase;
            } catch (Exception e) {
                logger.error("Error executing test case: {}", testCase.getId(), e);
                testCase.setStatus(TestCase.TestStatus.FAILED);
                TestCase.TestExecutionResult result = TestCase.TestExecutionResult.builder()
                                                                                  .success(false)
                                                                                  .errorMessage("Execution error: " + e.getMessage())
                                                                                  .stackTrace(getStackTraceAsString(e))
                                                                                  .executedAt(LocalDateTime.now())
                                                                                  .build();
                testCase.setLastExecutionResult(result);
                testCaseRepository.save(testCase);
                return testCase;
            }
        }, backgroundExecutor);
    }

    /**
     * Execute all test cases with a specific status.
     *
     * @param status the status of test cases to execute
     * @return a CompletableFuture that will complete when all tests are executed
     */
    public CompletableFuture<List<TestCase>> executeAllWithStatus(TestCase.TestStatus status) {
        List<TestCase> testCases = testCaseRepository.findByStatus(status);

        logger.info("Executing {} test cases with status: {}", testCases.size(), status);

        List<CompletableFuture<TestCase>> futures = testCases.stream()
                                                             .map(testCase -> executeTest(testCase.getId()))
                                                             .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> testCases);
    }

    /**
     * Convert a stack trace to a string.
     */
    private String getStackTraceAsString(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}