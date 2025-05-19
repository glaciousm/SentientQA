package com.projectoracle.repository;

import com.projectoracle.model.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Repository for storing and retrieving test cases.
 * Provides file-based persistence for generated tests.
 */
@Repository
public class TestCaseRepository {

    private static final Logger logger = LoggerFactory.getLogger(TestCaseRepository.class);
    private final ObjectMapper objectMapper;
    private final Map<UUID, TestCase> testCaseCache = new ConcurrentHashMap<>();

    @Value("${app.directories.output:output}")
    private String outputDir;

    public TestCaseRepository() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        try {
            Path outputPath = Paths.get(outputDir);
            if (!Files.exists(outputPath)) {
                logger.info("Creating output directory: {}", outputPath);
                Files.createDirectories(outputPath);
            }

            Path testCasesDir = outputPath.resolve("testcases");
            if (!Files.exists(testCasesDir)) {
                logger.info("Creating test cases directory: {}", testCasesDir);
                Files.createDirectories(testCasesDir);
            }

            // Load existing test cases
            loadExistingTestCases(testCasesDir);

            logger.info("Test Case Repository initialized");
        } catch (IOException e) {
            logger.error("Failed to initialize test case repository", e);
            throw new RuntimeException("Failed to initialize test case repository", e);
        }
    }

    /**
     * Load existing test cases from disk
     */
    private void loadExistingTestCases(Path testCasesDir) {
        try {
            if (Files.exists(testCasesDir)) {
                Files.list(testCasesDir)
                     .filter(path -> path.toString().endsWith(".json"))
                     .forEach(path -> {
                         try {
                             TestCase testCase = objectMapper.readValue(path.toFile(), TestCase.class);
                             testCaseCache.put(testCase.getId(), testCase);
                         } catch (IOException e) {
                             logger.error("Failed to load test case: {}", path, e);
                         }
                     });
                logger.info("Loaded {} existing test cases", testCaseCache.size());
            }
        } catch (IOException e) {
            logger.error("Error loading existing test cases", e);
        }
    }

    /**
     * Save a test case
     *
     * @param testCase the test case to save
     * @return the saved test case
     */
    public TestCase save(TestCase testCase) {
        if (testCase.getId() == null) {
            testCase.setId(UUID.randomUUID());
        }

        testCase.setModifiedAt(LocalDateTime.now());
        testCaseCache.put(testCase.getId(), testCase);

        try {
            Path testCasePath = getTestCasePath(testCase.getId());
            objectMapper.writeValue(testCasePath.toFile(), testCase);

            // Also save the actual test source code to a Java file
            if (testCase.getSourceCode() != null && !testCase.getSourceCode().isEmpty()) {
                saveTestSourceCode(testCase);
            }

            logger.info("Saved test case: {}", testCase.getId());
        } catch (IOException e) {
            logger.error("Failed to save test case: {}", testCase.getId(), e);
        }

        return testCase;
    }

    /**
     * Save the test source code to a Java file
     */
    private void saveTestSourceCode(TestCase testCase) throws IOException {
        String packagePath = testCase.getPackageName().replace('.', '/');
        Path sourcePath = Paths.get(outputDir, "generated-tests", packagePath);
        Files.createDirectories(sourcePath);

        Path javaFilePath = sourcePath.resolve(testCase.getClassName() + ".java");
        Files.writeString(javaFilePath, testCase.getSourceCode());

        logger.info("Saved test source code to: {}", javaFilePath);
    }

    /**
     * Find a test case by ID
     *
     * @param id the ID of the test case
     * @return the test case, or null if not found
     */
    public TestCase findById(UUID id) {
        return testCaseCache.get(id);
    }

    /**
     * Find all test cases
     *
     * @return a list of all test cases
     */
    public List<TestCase> findAll() {
        return new ArrayList<>(testCaseCache.values());
    }

    /**
     * Find test cases by class name
     *
     * @param className the class name to search for
     * @return a list of matching test cases
     */
    public List<TestCase> findByClassName(String className) {
        return testCaseCache.values().stream()
                            .filter(tc -> tc.getClassName().contains(className))
                            .collect(Collectors.toList());
    }

    /**
     * Find test cases by package name
     *
     * @param packageName the package name to search for
     * @return a list of matching test cases
     */
    public List<TestCase> findByPackage(String packageName) {
        return testCaseCache.values().stream()
                            .filter(tc -> tc.getPackageName().startsWith(packageName))
                            .collect(Collectors.toList());
    }

    /**
     * Find test cases by status
     *
     * @param status the status to search for
     * @return a list of matching test cases
     */
    public List<TestCase> findByStatus(TestCase.TestStatus status) {
        return testCaseCache.values().stream()
                            .filter(tc -> tc.getStatus() == status)
                            .collect(Collectors.toList());
    }

    /**
     * Delete a test case
     *
     * @param id the ID of the test case to delete
     */
    public void deleteById(UUID id) {
        testCaseCache.remove(id);

        try {
            Path testCasePath = getTestCasePath(id);
            Files.deleteIfExists(testCasePath);
            logger.info("Deleted test case: {}", id);
        } catch (IOException e) {
            logger.error("Failed to delete test case file: {}", id, e);
        }
    }

    /**
     * Get the path to a test case JSON file
     */
    private Path getTestCasePath(UUID id) {
        return Paths.get(outputDir, "testcases", id.toString() + ".json");
    }
}