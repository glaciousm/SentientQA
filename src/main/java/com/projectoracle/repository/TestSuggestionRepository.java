package com.projectoracle.repository;

import com.projectoracle.model.TestSuggestion;
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
import lombok.extern.slf4j.Slf4j;

/**
 * Repository for storing and retrieving test suggestions.
 * Provides file-based persistence for suggestions.
 */
@Repository
@Slf4j
public class TestSuggestionRepository {

    private final ObjectMapper objectMapper;
    private final Map<UUID, TestSuggestion> suggestionCache = new ConcurrentHashMap<>();

    @Value("${app.directories.output:output}")
    private String outputDir;

    public TestSuggestionRepository() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        try {
            Path outputPath = Paths.get(outputDir);
            if (!Files.exists(outputPath)) {
                log.info("Creating output directory: {}", outputPath);
                Files.createDirectories(outputPath);
            }

            Path suggestionsDir = outputPath.resolve("suggestions");
            if (!Files.exists(suggestionsDir)) {
                log.info("Creating suggestions directory: {}", suggestionsDir);
                Files.createDirectories(suggestionsDir);
            }

            // Load existing suggestions
            loadExistingSuggestions(suggestionsDir);

            log.info("Test Suggestion Repository initialized");
        } catch (IOException e) {
            log.error("Failed to initialize test suggestion repository", e);
            throw new RuntimeException("Failed to initialize test suggestion repository", e);
        }
    }

    /**
     * Load existing suggestions from disk
     */
    private void loadExistingSuggestions(Path suggestionsDir) {
        try {
            if (Files.exists(suggestionsDir)) {
                Files.list(suggestionsDir)
                     .filter(path -> path.toString().endsWith(".json"))
                     .forEach(path -> {
                         try {
                             TestSuggestion suggestion = objectMapper.readValue(path.toFile(), TestSuggestion.class);
                             suggestionCache.put(suggestion.getId(), suggestion);
                         } catch (IOException e) {
                             log.error("Failed to load suggestion: {}", path, e);
                         }
                     });
                log.info("Loaded {} existing suggestions", suggestionCache.size());
            }
        } catch (IOException e) {
            log.error("Error loading existing suggestions", e);
        }
    }

    /**
     * Save a suggestion
     *
     * @param suggestion the suggestion to save
     * @return the saved suggestion
     */
    public TestSuggestion save(TestSuggestion suggestion) {
        if (suggestion.getId() == null) {
            suggestion.setId(UUID.randomUUID());
        }

        if (suggestion.getCreatedAt() == null) {
            suggestion.setCreatedAt(LocalDateTime.now());
        }

        suggestionCache.put(suggestion.getId(), suggestion);

        try {
            Path suggestionPath = getSuggestionPath(suggestion.getId());
            objectMapper.writeValue(suggestionPath.toFile(), suggestion);
            log.debug("Saved suggestion: {}", suggestion.getId());
        } catch (IOException e) {
            log.error("Failed to save suggestion: {}", suggestion.getId(), e);
        }

        return suggestion;
    }

    /**
     * Find a suggestion by ID
     *
     * @param id the ID of the suggestion
     * @return the suggestion, or null if not found
     */
    public TestSuggestion findById(UUID id) {
        return suggestionCache.get(id);
    }

    /**
     * Find all suggestions
     *
     * @return a list of all suggestions
     */
    public List<TestSuggestion> findAll() {
        return new ArrayList<>(suggestionCache.values());
    }

    /**
     * Find suggestions by method name
     *
     * @param methodName the method name to search for
     * @return a list of matching suggestions
     */
    public List<TestSuggestion> findByMethodName(String methodName) {
        return suggestionCache.values().stream()
                              .filter(s -> s.getMethodInfo() != null &&
                                      s.getMethodInfo().getMethodName().equals(methodName))
                              .collect(Collectors.toList());
    }

    /**
     * Find suggestions by class name
     *
     * @param className the class name to search for
     * @return a list of matching suggestions
     */
    public List<TestSuggestion> findByClassName(String className) {
        return suggestionCache.values().stream()
                              .filter(s -> s.getMethodInfo() != null &&
                                      s.getMethodInfo().getClassName().equals(className))
                              .collect(Collectors.toList());
    }

    /**
     * Find suggestions by category
     *
     * @param category the category to search for
     * @return a list of matching suggestions
     */
    public List<TestSuggestion> findByCategory(String category) {
        return suggestionCache.values().stream()
                              .filter(s -> category.equals(s.getCategory()))
                              .collect(Collectors.toList());
    }

    /**
     * Find unimplemented suggestions
     *
     * @return a list of unimplemented suggestions
     */
    public List<TestSuggestion> findUnimplemented() {
        return suggestionCache.values().stream()
                              .filter(s -> !s.isImplemented())
                              .collect(Collectors.toList());
    }

    /**
     * Mark a suggestion as implemented
     *
     * @param id the ID of the suggestion
     * @param implementedTestId the ID of the implemented test case
     * @return the updated suggestion
     */
    public TestSuggestion markAsImplemented(UUID id, UUID implementedTestId) {
        TestSuggestion suggestion = findById(id);
        if (suggestion != null) {
            suggestion.setImplemented(true);
            suggestion.setImplementedTestId(implementedTestId);
            save(suggestion);
        }
        return suggestion;
    }

    /**
     * Delete a suggestion
     *
     * @param id the ID of the suggestion to delete
     */
    public void deleteById(UUID id) {
        suggestionCache.remove(id);

        try {
            Path suggestionPath = getSuggestionPath(id);
            Files.deleteIfExists(suggestionPath);
            log.info("Deleted suggestion: {}", id);
        } catch (IOException e) {
            log.error("Failed to delete suggestion file: {}", id, e);
        }
    }

    /**
     * Get the path to a suggestion JSON file
     */
    private Path getSuggestionPath(UUID id) {
        return Paths.get(outputDir, "suggestions", id.toString() + ".json");
    }

    /**
     * Find suggestions with similar test names
     *
     * @param testName the test name to compare with
     * @return a list of suggestions with similar test names
     */
    public List<TestSuggestion> findBySimilarTestName(String testName) {
        // Simple implementation that checks for substring match
        String lowerTestName = testName.toLowerCase();

        return suggestionCache.values().stream()
                              .filter(s -> s.getTestName() != null &&
                                      s.getTestName().toLowerCase().contains(lowerTestName))
                              .collect(Collectors.toList());
    }

    /**
     * Find suggestions for a specific method in a class
     *
     * @param className the class name
     * @param methodName the method name
     * @return a list of matching suggestions
     */
    public List<TestSuggestion> findByClassAndMethod(String className, String methodName) {
        return suggestionCache.values().stream()
                              .filter(s -> s.getMethodInfo() != null &&
                                      s.getMethodInfo().getClassName().equals(className) &&
                                      s.getMethodInfo().getMethodName().equals(methodName))
                              .collect(Collectors.toList());
    }

    /**
     * Count total suggestions
     *
     * @return the number of suggestions
     */
    public int count() {
        return suggestionCache.size();
    }

    /**
     * Count implemented suggestions
     *
     * @return the number of implemented suggestions
     */
    public int countImplemented() {
        return (int) suggestionCache.values().stream()
                                    .filter(TestSuggestion::isImplemented)
                                    .count();
    }

    /**
     * Count unimplemented suggestions
     *
     * @return the number of unimplemented suggestions
     */
    public int countUnimplemented() {
        return (int) suggestionCache.values().stream()
                                    .filter(s -> !s.isImplemented())
                                    .count();
    }

    /**
     * Clear all suggestions
     */
    public void clearAll() {
        suggestionCache.clear();

        try {
            Path suggestionsDir = Paths.get(outputDir, "suggestions");
            if (Files.exists(suggestionsDir)) {
                Files.list(suggestionsDir)
                     .filter(path -> path.toString().endsWith(".json"))
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                         } catch (IOException e) {
                             log.error("Failed to delete suggestion: {}", path, e);
                         }
                     });
            }
            log.info("Cleared all suggestions");
        } catch (IOException e) {
            log.error("Error clearing suggestions", e);
        }
    }
}