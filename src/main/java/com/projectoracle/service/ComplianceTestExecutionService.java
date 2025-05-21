package com.projectoracle.service;

import com.projectoracle.model.TestCase;
import com.projectoracle.repository.TestCaseRepository;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for executing compliance test cases.
 * Handles compilation, execution and result analysis for compliance tests.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ComplianceTestExecutionService {

    private final TestCaseRepository testCaseRepository;
    private final CodeAnalysisService codeAnalysisService;
    
    // Using a thread pool for parallel test execution
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    
    // Base directory for test compilation and execution
    private final String baseTestDir = System.getProperty("java.io.tmpdir") + "/compliance-tests";
    
    /**
     * Execute a single compliance test case
     * 
     * @param testId The ID of the test to execute
     * @return The updated test case with execution results
     */
    public TestCase executeTest(String testId) {
        log.info("Executing compliance test with ID: {}", testId);
        
        // Get test case from repository
        UUID uuid = UUID.fromString(testId);
        TestCase test = testCaseRepository.findById(uuid);
        if (test == null) {
            throw new IllegalArgumentException("Test case not found: " + testId);
        }
        
        // Update test status to running
        test.setStatus(TestCase.TestStatus.RUNNING);
        test = testCaseRepository.save(test);
        
        try {
            // Prepare test environment
            String testPath = prepareTestEnvironment(test);
            
            // Compile test
            boolean compileSuccess = compileTest(testPath, test);
            if (!compileSuccess) {
                test.setStatus(TestCase.TestStatus.FAILED);
                test.setErrorMessage("Compilation failed");
                return testCaseRepository.save(test);
            }
            
            // Execute test
            TestResult result = runTest(testPath, test);
            
            // Update test case with results
            test.setStatus(result.isPassed() ? TestCase.TestStatus.PASSED : TestCase.TestStatus.FAILED);
            test.setLastExecuted(LocalDateTime.now());
            test.setExecutionTime(result.getExecutionTime());
            test.setErrorMessage(result.getError());
            test.setExecutionOutput(result.getOutput());
            
            // Save and return updated test case
            return testCaseRepository.save(test);
            
        } catch (Exception e) {
            log.error("Error executing compliance test: {}", e.getMessage(), e);
            test.setStatus(TestCase.TestStatus.FAILED);
            test.setErrorMessage("Test execution error: " + e.getMessage());
            return testCaseRepository.save(test);
        }
    }
    
    /**
     * Execute multiple compliance tests in parallel
     * 
     * @param testIds List of test IDs to execute
     * @return Map of test IDs to execution results
     */
    public Map<String, TestCase> executeBatch(List<String> testIds) {
        log.info("Executing batch of {} compliance tests", testIds.size());
        
        // Create futures for parallel execution
        List<CompletableFuture<TestCase>> futures = testIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> executeTest(id), executorService))
                .collect(Collectors.toList());
        
        // Wait for all tests to complete and collect results
        Map<String, TestCase> results = new HashMap<>();
        futures.forEach(future -> {
            try {
                TestCase test = future.join();
                results.put(test.getId().toString(), test);
            } catch (Exception e) {
                log.error("Error executing test in batch: {}", e.getMessage(), e);
            }
        });
        
        return results;
    }
    
    /**
     * Execute all compliance tests for a specific standard
     * 
     * @param className The class to test
     * @param standard The compliance standard to test against
     * @return List of executed test cases
     */
    public List<TestCase> executeComplianceTestsForStandard(String className, String standard) {
        log.info("Executing all compliance tests for {}.{}", className, standard);
        
        // Find all tests for this class and standard
        List<TestCase> tests = testCaseRepository.findByClassNameAndDescriptionContaining(
                className, standard);
        
        if (tests.isEmpty()) {
            log.warn("No compliance tests found for {}.{}", className, standard);
            return List.of();
        }
        
        // Extract test IDs
        List<String> testIds = tests.stream()
                .map(test -> test.getId().toString())
                .collect(Collectors.toList());
        
        // Execute tests in batch
        Map<String, TestCase> results = executeBatch(testIds);
        
        // Return updated test cases
        return new ArrayList<>(results.values());
    }
    
    /**
     * Prepare the test environment for compilation and execution
     * 
     * @param test The test case to prepare for
     * @return The path to the test file
     * @throws IOException If an I/O error occurs
     */
    private String prepareTestEnvironment(TestCase test) throws IOException {
        // Create test directory if it doesn't exist
        String testDir = baseTestDir + "/" + test.getId();
        Files.createDirectories(Paths.get(testDir));
        
        // Create package directories
        String packagePath = test.getPackageName().replace('.', '/');
        Files.createDirectories(Paths.get(testDir + "/src/main/java/" + packagePath));
        
        // Create test file
        String testFileName = test.getClassName().substring(test.getClassName().lastIndexOf('.') + 1) + ".java";
        String testFilePath = testDir + "/src/main/java/" + packagePath + "/" + testFileName;
        
        // Write test source code to file
        try (FileWriter writer = new FileWriter(testFilePath)) {
            writer.write(test.getSourceCode());
        }
        
        // Create pom.xml with necessary dependencies for test
        createPomXml(testDir, test);
        
        return testDir;
    }
    
    /**
     * Create a Maven pom.xml file for test compilation and execution
     * 
     * @param testDir The test directory
     * @param test The test case
     * @throws IOException If an I/O error occurs
     */
    private void createPomXml(String testDir, TestCase test) throws IOException {
        String pomXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "\n" +
                "    <groupId>com.projectoracle.compliance</groupId>\n" +
                "    <artifactId>compliance-test-execution</artifactId>\n" +
                "    <version>1.0.0</version>\n" +
                "\n" +
                "    <properties>\n" +
                "        <maven.compiler.source>11</maven.compiler.source>\n" +
                "        <maven.compiler.target>11</maven.compiler.target>\n" +
                "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
                "    </properties>\n" +
                "\n" +
                "    <dependencies>\n" +
                "        <!-- JUnit 5 -->\n" +
                "        <dependency>\n" +
                "            <groupId>org.junit.jupiter</groupId>\n" +
                "            <artifactId>junit-jupiter-api</artifactId>\n" +
                "            <version>5.8.2</version>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>org.junit.jupiter</groupId>\n" +
                "            <artifactId>junit-jupiter-engine</artifactId>\n" +
                "            <version>5.8.2</version>\n" +
                "        </dependency>\n" +
                "        \n" +
                "        <!-- Mockito -->\n" +
                "        <dependency>\n" +
                "            <groupId>org.mockito</groupId>\n" +
                "            <artifactId>mockito-core</artifactId>\n" +
                "            <version>4.5.1</version>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>org.mockito</groupId>\n" +
                "            <artifactId>mockito-junit-jupiter</artifactId>\n" +
                "            <version>4.5.1</version>\n" +
                "        </dependency>\n" +
                "        \n" +
                "        <!-- Add application dependencies -->\n" +
                "        <!-- These would ideally be dynamically generated based on project dependencies -->\n" +
                "        <dependency>\n" +
                "            <groupId>org.springframework</groupId>\n" +
                "            <artifactId>spring-core</artifactId>\n" +
                "            <version>5.3.20</version>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>org.springframework</groupId>\n" +
                "            <artifactId>spring-context</artifactId>\n" +
                "            <version>5.3.20</version>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>org.springframework</groupId>\n" +
                "            <artifactId>spring-test</artifactId>\n" +
                "            <version>5.3.20</version>\n" +
                "        </dependency>\n" +
                "    </dependencies>\n" +
                "\n" +
                "    <build>\n" +
                "        <plugins>\n" +
                "            <plugin>\n" +
                "                <groupId>org.apache.maven.plugins</groupId>\n" +
                "                <artifactId>maven-surefire-plugin</artifactId>\n" +
                "                <version>3.0.0-M5</version>\n" +
                "            </plugin>\n" +
                "        </plugins>\n" +
                "    </build>\n" +
                "</project>";
        
        try (FileWriter writer = new FileWriter(testDir + "/pom.xml")) {
            writer.write(pomXml);
        }
    }
    
    /**
     * Compile the test using Maven
     * 
     * @param testDir The test directory
     * @param test The test case
     * @return True if compilation succeeded, false otherwise
     */
    private boolean compileTest(String testDir, TestCase test) {
        try {
            log.info("Compiling compliance test: {}", test.getName());
            
            // Create process to run Maven compile
            ProcessBuilder pb = new ProcessBuilder("mvn", "compile");
            pb.directory(new File(testDir));
            pb.redirectErrorStream(true);
            
            // Start process and capture output
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            
            // Wait for process to complete
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                log.error("Compilation failed for test {}: {}", test.getId(), output);
                test.setErrorMessage("Compilation failed: " + output);
                testCaseRepository.save(test);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error compiling test {}: {}", test.getId(), e.getMessage(), e);
            test.setErrorMessage("Compilation error: " + e.getMessage());
            testCaseRepository.save(test);
            return false;
        }
    }
    
    /**
     * Run the compiled test using Maven Surefire
     * 
     * @param testDir The test directory
     * @param test The test case
     * @return The test result
     */
    private TestResult runTest(String testDir, TestCase test) {
        try {
            log.info("Running compliance test: {}", test.getName());
            
            long startTime = System.currentTimeMillis();
            
            // Create process to run Maven test
            ProcessBuilder pb = new ProcessBuilder("mvn", "test");
            pb.directory(new File(testDir));
            pb.redirectErrorStream(true);
            
            // Start process and capture output
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            
            // Wait for process to complete
            int exitCode = process.waitFor();
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Check for test failures in output
            boolean passed = exitCode == 0 && !output.contains("FAILURES!!!");
            String error = passed ? null : extractTestError(output);
            
            return new TestResult(passed, executionTime, output, error);
            
        } catch (Exception e) {
            log.error("Error running test {}: {}", test.getId(), e.getMessage(), e);
            return new TestResult(false, 0, "", "Test execution error: " + e.getMessage());
        }
    }
    
    /**
     * Extract error message from test output
     * 
     * @param output The test output
     * @return The extracted error message
     */
    private String extractTestError(String output) {
        // Look for common JUnit error patterns
        if (output.contains("Failures:")) {
            int failuresIndex = output.indexOf("Failures:");
            int endIndex = output.indexOf("Tests run:", failuresIndex);
            if (endIndex == -1) {
                endIndex = output.length();
            }
            return output.substring(failuresIndex, endIndex).trim();
        } else if (output.contains("FAILURES!!!")) {
            int failuresIndex = output.indexOf("FAILURES!!!");
            int endIndex = output.indexOf("Tests run:", failuresIndex);
            if (endIndex == -1) {
                endIndex = output.length();
            }
            return output.substring(failuresIndex, endIndex).trim();
        }
        
        return "Unknown test failure";
    }
    
    /**
     * Test result data class
     */
    private static class TestResult {
        private final boolean passed;
        private final long executionTime;
        private final String output;
        private final String error;
        
        public TestResult(boolean passed, long executionTime, String output, String error) {
            this.passed = passed;
            this.executionTime = executionTime;
            this.output = output;
            this.error = error;
        }
        
        public boolean isPassed() {
            return passed;
        }
        
        public long getExecutionTime() {
            return executionTime;
        }
        
        public String getOutput() {
            return output;
        }
        
        public String getError() {
            return error;
        }
    }
}