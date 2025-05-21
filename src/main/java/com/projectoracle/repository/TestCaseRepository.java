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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

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
     * @throws IOException if there's an error saving to disk
     */
    public TestCase save(TestCase testCase) {
        if (testCase == null) {
            throw new IllegalArgumentException("Test case cannot be null");
        }
        
        // Acquire write lock for thread safety during ID generation and cache update
        lock.writeLock().lock();
        try {
            // Generate ID if not present
            if (testCase.getId() == null) {
                testCase.setId(UUID.randomUUID());
            }

            testCase.setModifiedAt(LocalDateTime.now());
            
            // Create a defensive copy to prevent further modifications from affecting the saved version
            TestCase testCaseCopy = testCase.deepCopy();
            testCaseCache.put(testCaseCopy.getId(), testCaseCopy);

            // Persist to disk
            Path testCasePath = getTestCasePath(testCaseCopy.getId());
            objectMapper.writeValue(testCasePath.toFile(), testCaseCopy);

            // Also save the actual test source code to a Java file
            if (testCaseCopy.getSourceCode() != null && !testCaseCopy.getSourceCode().isEmpty()) {
                saveTestSourceCode(testCaseCopy);
            }

            logger.info("Saved test case: {}", testCaseCopy.getId());
            return testCaseCopy;
        } catch (IOException e) {
            logger.error("Failed to save test case: {}", testCase.getId(), e);
            throw new RuntimeException("Failed to save test case: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
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
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        
        lock.readLock().lock();
        try {
            TestCase testCase = testCaseCache.get(id);
            
            // Create a defensive copy to prevent modifications to the cached version
            return testCase != null ? testCase.deepCopy() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Find all test cases
     *
     * @return a list of all test cases
     */
    public List<TestCase> findAll() {
        lock.readLock().lock();
        try {
            // Create defensive copies of all test cases
            return testCaseCache.values().stream()
                                .map(TestCase::deepCopy)
                                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
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
     * Find test cases by class name and description containing
     *
     * @param className the class name to search for
     * @param descriptionPart the part of description to search for
     * @return a list of matching test cases
     */
    public List<TestCase> findByClassNameAndDescriptionContaining(String className, String descriptionPart) {
        return testCaseCache.values().stream()
                            .filter(tc -> tc.getClassName().contains(className) && 
                                   tc.getDescription() != null && 
                                   tc.getDescription().contains(descriptionPart))
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
     * @throws IOException if there's an error deleting the file
     */
    public void deleteById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        
        lock.writeLock().lock();
        try {
            testCaseCache.remove(id);

            Path testCasePath = getTestCasePath(id);
            boolean deleted = Files.deleteIfExists(testCasePath);
            
            if (deleted) {
                logger.info("Deleted test case file: {}", id);
            } else {
                logger.warn("Test case file not found for deletion: {}", id);
            }
            
            // Also try to delete the Java source file if it exists
            TestCase testCase = testCaseCache.get(id);
            if (testCase != null && testCase.getClassName() != null && testCase.getPackageName() != null) {
                String packagePath = testCase.getPackageName().replace('.', '/');
                Path sourcePath = Paths.get(outputDir, "generated-tests", packagePath);
                Path javaFilePath = sourcePath.resolve(testCase.getClassName() + ".java");
                
                Files.deleteIfExists(javaFilePath);
            }
            
            logger.info("Deleted test case: {}", id);
        } catch (IOException e) {
            logger.error("Failed to delete test case file: {}", id, e);
            throw new RuntimeException("Failed to delete test case: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get the path to a test case JSON file
     */
    private Path getTestCasePath(UUID id) {
        return Paths.get(outputDir, "testcases", id.toString() + ".json");
    }
}