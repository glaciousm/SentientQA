package com.projectoracle.service;

import com.projectoracle.model.TestCase;
import com.projectoracle.model.TestCase.TestExecutionResult;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.config.AIConfig;

import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.tools.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;

/**
 * Enhanced service for executing generated test cases.
 * Handles compilation and running of the tests with actual results.
 */
@Service
public class EnhancedTestExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedTestExecutionService.class);

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private AIConfig aiConfig;

    @Autowired
    @Qualifier("backgroundExecutor")
    private Executor backgroundExecutor;

    @Value("${app.directories.output:output}")
    private String outputDir;

    /**
     * Map of current test executions (testId -> thread)
     * Using this to allow test cancellation if needed
     */
    private final Map<UUID, Thread> activeExecutions = new ConcurrentHashMap<>();
    
    /**
     * Execute a test case.
     *
     * @param testCaseId the ID of the test case to execute
     * @return a CompletableFuture that will complete when the test execution is done
     * @throws IllegalArgumentException if the test case doesn't exist or is invalid
     */
    public CompletableFuture<TestCase> executeTest(UUID testCaseId) {
        if (testCaseId == null) {
            throw new IllegalArgumentException("Test case ID cannot be null");
        }
        
        // Capture current thread for tracking
        final Thread[] executionThread = new Thread[1];
        
        return CompletableFuture.supplyAsync(() -> {
            // Store current thread for potential cancellation
            executionThread[0] = Thread.currentThread();
            activeExecutions.put(testCaseId, executionThread[0]);
            
            try {
                TestCase testCase = testCaseRepository.findById(testCaseId);
                if (testCase == null) {
                    throw new IllegalArgumentException("Test case not found: " + testCaseId);
                }

                // Validation
                if (testCase.getSourceCode() == null || testCase.getSourceCode().trim().isEmpty()) {
                    throw new IllegalArgumentException("Test case has no source code: " + testCaseId);
                }
                
                if (testCase.getPackageName() == null || testCase.getClassName() == null) {
                    throw new IllegalArgumentException("Test case missing package or class name: " + testCaseId);
                }

                logger.info("Executing test case: {}", testCase.getId());
                testCase.setStatus(TestCase.TestStatus.EXECUTING);
                testCaseRepository.save(testCase);

                try {
                    // Create a unique directory for this test execution to avoid conflicts
                    String executionId = UUID.randomUUID().toString().substring(0, 8);
                    String packagePath = testCase.getPackageName().replace('.', '/');
                    Path sourcePath = Paths.get(outputDir, "generated-tests", "execution-" + executionId, packagePath);
                    Path sourceFile = sourcePath.resolve(testCase.getClassName() + ".java");

                    Files.createDirectories(sourcePath);
                    Files.writeString(sourceFile, testCase.getSourceCode());
                    logger.debug("Wrote test source to {}", sourceFile);

                    // Compile the test
                    boolean compiled = compileTest(sourceFile);
                    if (!compiled) {
                        throw new RuntimeException("Failed to compile test: " + sourceFile);
                    }

                    // Check if execution was cancelled
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Test execution was cancelled");
                    }

                    // Run the test
                    TestExecutionResult result = runTest(testCase.getPackageName() + "." + testCase.getClassName());

                    // Update test case with results
                    testCase.setLastExecutionResult(result);
                    testCase.setLastExecutedAt(LocalDateTime.now());
                    testCase.setStatus(result.isSuccess() ? TestCase.TestStatus.PASSED : TestCase.TestStatus.FAILED);

                    // Save the test case with results
                    testCaseRepository.save(testCase);

                    logger.info("Test execution completed for: {}, success: {}",
                            testCase.getId(), result.isSuccess());

                    return testCase;
                } catch (InterruptedException e) {
                    logger.warn("Test execution cancelled: {}", testCaseId);
                    testCase.setStatus(TestCase.TestStatus.FAILED);
                    TestCase.TestExecutionResult result = TestCase.TestExecutionResult.builder()
                                                                                  .success(false)
                                                                                  .errorMessage("Execution cancelled")
                                                                                  .executedAt(LocalDateTime.now())
                                                                                  .build();
                    testCase.setLastExecutionResult(result);
                    testCaseRepository.save(testCase);
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
            } finally {
                // Clean up the thread reference
                activeExecutions.remove(testCaseId);
            }    
        }, backgroundExecutor);
    }
    
    /**
     * Cancel a running test execution
     * 
     * @param testCaseId the ID of the test to cancel
     * @return true if the test was running and cancellation was requested
     */
    public boolean cancelTestExecution(UUID testCaseId) {
        Thread executionThread = activeExecutions.get(testCaseId);
        if (executionThread != null) {
            logger.info("Cancelling test execution for: {}", testCaseId);
            executionThread.interrupt();
            return true;
        }
        return false;
    }

    /**
     * Execute all test cases with a specific status.
     *
     * @param status the status of test cases to execute
     * @return a CompletableFuture that will complete when all tests are executed
     * @throws IllegalArgumentException if status is null
     */
    public CompletableFuture<List<TestCase>> executeAllWithStatus(TestCase.TestStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        
        List<TestCase> testCases = testCaseRepository.findByStatus(status);

        logger.info("Executing {} test cases with status: {}", testCases.size(), status);
        
        if (testCases.isEmpty()) {
            // Return immediately if there are no test cases
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        // Create a batch execution ID for tracking this group of tests
        final String batchId = "batch-" + UUID.randomUUID().toString().substring(0, 8);
        logger.info("Created batch execution {} for {} tests", batchId, testCases.size());
        
        try {
            // Execute tests with proper error handling for each test
            List<CompletableFuture<TestCase>> futures = testCases.stream()
                    .map(testCase -> {
                        try {
                            return executeTest(testCase.getId());
                        } catch (Exception e) {
                            logger.error("Failed to schedule test execution for test {}: {}", 
                                    testCase.getId(), e.getMessage(), e);
                            // Create a pre-completed future with the failure
                            return CompletableFuture.completedFuture(testCase);
                        }
                    })
                    .collect(Collectors.toList());
            
            // Wait for all tests to complete and return the results
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                            .map(CompletableFuture::join) // Safe because all futures are completed
                            .collect(Collectors.toList())
                    ).exceptionally(ex -> {
                        logger.error("Batch execution {} failed: {}", batchId, ex.getMessage(), ex);
                        // Return as many results as we can
                        return futures.stream()
                                .filter(CompletableFuture::isDone)
                                .map(f -> {
                                    try {
                                        return f.join();
                                    } catch (Exception e) {
                                        return null;
                                    }
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                    });
        } catch (Exception e) {
            logger.error("Failed to execute batch {}: {}", batchId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Compile a test file
     *
     * @param testFile path to the test file
     * @return true if compilation was successful
     */
    private boolean compileTest(Path testFile) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("Java compiler not available. Make sure JDK is installed and used.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

        // Get classpath
        String classpath = getClasspath();

        // Set compilation options
        List<String> options = Arrays.asList("-classpath", classpath, "-d", Paths.get(outputDir, "classes").toString());

        // Create compilation units
        Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(testFile.toFile()));

        // Create compilation task
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null, compilationUnits);

        // Perform compilation
        boolean success = task.call();

        // Log diagnostics
        if (!success) {
            logger.error("Compilation failed:");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                logger.error("{}: Line {} in {}: {}",
                        diagnostic.getKind(),
                        diagnostic.getLineNumber(),
                        diagnostic.getSource().getName(),
                        diagnostic.getMessage(null));
            }
        }

        // Close file manager
        try {
            fileManager.close();
        } catch (IOException e) {
            logger.warn("Failed to close file manager", e);
        }

        return success;
    }

    /**
     * Run a test class
     *
     * @param testClassName fully qualified name of the test class
     * @return test execution result
     */
    private TestCase.TestExecutionResult runTest(String testClassName) {
        long startTime = System.currentTimeMillis();

        try {
            // Create launcher
            Launcher launcher = LauncherFactory.create();

            // Create request
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                                                                              .selectors(DiscoverySelectors.selectClass(testClassName))
                                                                              .filters(ClassNameFilter.includeClassNamePatterns(testClassName))
                                                                              .build();

            // Create summary listener
            SummaryGeneratingListener listener = new SummaryGeneratingListener();

            // Register listener
            launcher.registerTestExecutionListeners(listener);

            // Execute tests
            launcher.execute(request);

            // Get summary
            TestExecutionSummary summary = listener.getSummary();

            // Parse results
            boolean success = summary.getTotalFailureCount() == 0;
            long executionTime = System.currentTimeMillis() - startTime;

            // Collect failure messages
            StringBuilder errorMessages = new StringBuilder();
            StringBuilder stackTraces = new StringBuilder();

            summary.getFailures().forEach(failure -> {
                if (errorMessages.length() > 0) {
                    errorMessages.append("\n");
                    stackTraces.append("\n");
                }
                errorMessages.append(failure.getTestIdentifier().getDisplayName()).append(": ")
                             .append(failure.getException().getMessage());

                StringWriter sw = new StringWriter();
                failure.getException().printStackTrace(new java.io.PrintWriter(sw));
                stackTraces.append(sw.toString());
            });

            // Create result
            return TestCase.TestExecutionResult.builder()
                                               .success(success)
                                               .executionTimeMs(executionTime)
                                               .errorMessage(errorMessages.toString())
                                               .stackTrace(stackTraces.toString())
                                               .executedAt(LocalDateTime.now())
                                               .build();

        } catch (Exception e) {
            logger.error("Error running test: {}", testClassName, e);

            return TestCase.TestExecutionResult.builder()
                                               .success(false)
                                               .executionTimeMs(System.currentTimeMillis() - startTime)
                                               .errorMessage("Execution error: " + e.getMessage())
                                               .stackTrace(getStackTraceAsString(e))
                                               .executedAt(LocalDateTime.now())
                                               .build();
        }
    }

    /**
     * Get runtime classpath
     */
    private String getClasspath() {
        // Base classpath
        StringBuilder classpath = new StringBuilder();

        // Add current classpath
        String currentClasspath = System.getProperty("java.class.path");
        classpath.append(currentClasspath);

        // Add output classes directory
        classpath.append(File.pathSeparator).append(Paths.get(outputDir, "classes").toString());

        return classpath.toString();
    }

    /**
     * Convert a stack trace to a string.
     */
    private String getStackTraceAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}