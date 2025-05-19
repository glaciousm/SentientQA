package com.projectoracle.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import jakarta.annotation.PostConstruct;

import com.projectoracle.model.ElementFingerprint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Repository for storing and retrieving element fingerprints.
 * Provides file-based persistence for element identification data.
 */
@Repository
public class ElementRepository {

    private static final Logger logger = LoggerFactory.getLogger(ElementRepository.class);
    private final ObjectMapper objectMapper;
    private final Map<String, ElementFingerprint> fingerprintCache = new ConcurrentHashMap<>();

    @Value("${app.directories.output:output}")
    private String outputDir;

    public ElementRepository() {
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

            Path fingerprintsDir = outputPath.resolve("fingerprints");
            if (!Files.exists(fingerprintsDir)) {
                logger.info("Creating fingerprints directory: {}", fingerprintsDir);
                Files.createDirectories(fingerprintsDir);
            }

            // Load existing fingerprints
            loadExistingFingerprints(fingerprintsDir);

            logger.info("Element Repository initialized");
        } catch (IOException e) {
            logger.error("Failed to initialize element repository", e);
            throw new RuntimeException("Failed to initialize element repository", e);
        }
    }

    /**
     * Load existing fingerprints from disk
     */
    private void loadExistingFingerprints(Path fingerprintsDir) {
        try {
            if (Files.exists(fingerprintsDir)) {
                Files.list(fingerprintsDir)
                     .filter(path -> path.toString().endsWith(".json"))
                     .forEach(path -> {
                         try {
                             ElementFingerprint fingerprint = objectMapper.readValue(path.toFile(), ElementFingerprint.class);
                             fingerprintCache.put(fingerprint.getId(), fingerprint);
                         } catch (IOException e) {
                             logger.error("Failed to load fingerprint: {}", path, e);
                         }
                     });
                logger.info("Loaded {} existing fingerprints", fingerprintCache.size());
            }
        } catch (IOException e) {
            logger.error("Error loading existing fingerprints", e);
        }
    }

    /**
     * Save a fingerprint
     *
     * @param fingerprint the fingerprint to save
     * @return the saved fingerprint
     */
    public ElementFingerprint saveFingerprint(ElementFingerprint fingerprint) {
        if (fingerprint == null || fingerprint.getId() == null) {
            logger.warn("Cannot save null fingerprint or fingerprint without ID");
            return fingerprint;
        }

        fingerprintCache.put(fingerprint.getId(), fingerprint);

        try {
            Path fingerprintPath = getFingerprintPath(fingerprint.getId());
            objectMapper.writeValue(fingerprintPath.toFile(), fingerprint);
            logger.debug("Saved fingerprint: {}", fingerprint.getId());
        } catch (IOException e) {
            logger.error("Failed to save fingerprint: {}", fingerprint.getId(), e);
        }

        return fingerprint;
    }

    /**
     * Find a fingerprint by ID
     *
     * @param id the ID of the fingerprint
     * @return the fingerprint, or null if not found
     */
    public ElementFingerprint findById(String id) {
        return fingerprintCache.get(id);
    }

    /**
     * Find all fingerprints
     *
     * @return a list of all fingerprints
     */
    public List<ElementFingerprint> findAll() {
        return new ArrayList<>(fingerprintCache.values());
    }

    /**
     * Find fingerprints by element type
     *
     * @param elementType the element type to search for
     * @return a list of matching fingerprints
     */
    public List<ElementFingerprint> findByElementType(String elementType) {
        return fingerprintCache.values().stream()
                               .filter(fp -> elementType.equals(fp.getElementType()))
                               .collect(Collectors.toList());
    }

    /**
     * Find fingerprints by element ID
     *
     * @param elementId the element ID to search for
     * @return a list of matching fingerprints
     */
    public List<ElementFingerprint> findByElementId(String elementId) {
        return fingerprintCache.values().stream()
                               .filter(fp -> elementId.equals(fp.getElementId()))
                               .collect(Collectors.toList());
    }

    /**
     * Find fingerprints by element text (exact match)
     *
     * @param elementText the element text to search for
     * @return a list of matching fingerprints
     */
    public List<ElementFingerprint> findByElementText(String elementText) {
        return fingerprintCache.values().stream()
                               .filter(fp -> elementText.equals(fp.getElementText()))
                               .collect(Collectors.toList());
    }

    /**
     * Find fingerprints by element text (contains)
     *
     * @param textFragment the text fragment to search for
     * @return a list of matching fingerprints
     */
    public List<ElementFingerprint> findByElementTextContaining(String textFragment) {
        return fingerprintCache.values().stream()
                               .filter(fp -> fp.getElementText() != null &&
                                       fp.getElementText().contains(textFragment))
                               .collect(Collectors.toList());
    }

    /**
     * Find the most similar fingerprint to the given one
     *
     * @param fingerprint the fingerprint to compare with
     * @param minConfidence minimum confidence threshold
     * @return the most similar fingerprint, or null if none meet the threshold
     */
    public ElementFingerprint findMostSimilar(ElementFingerprint fingerprint, double minConfidence) {
        if (fingerprint == null) {
            return null;
        }

        ElementFingerprint bestMatch = null;
        double bestConfidence = minConfidence;

        for (ElementFingerprint candidate : fingerprintCache.values()) {
            double confidence = fingerprint.calculateMatchConfidence(candidate);
            if (confidence > bestConfidence) {
                bestMatch = candidate;
                bestConfidence = confidence;
            }
        }

        return bestMatch;
    }

    /**
     * Find similar fingerprints to the given one
     *
     * @param fingerprint the fingerprint to compare with
     * @param minConfidence minimum confidence threshold
     * @return a list of similar fingerprints that meet the threshold
     */
    public List<ElementFingerprint> findSimilar(ElementFingerprint fingerprint, double minConfidence) {
        if (fingerprint == null) {
            return new ArrayList<>();
        }

        return fingerprintCache.values().stream()
                               .filter(candidate -> fingerprint.calculateMatchConfidence(candidate) >= minConfidence)
                               .collect(Collectors.toList());
    }

    /**
     * Delete a fingerprint
     *
     * @param id the ID of the fingerprint to delete
     */
    public void deleteById(String id) {
        fingerprintCache.remove(id);

        try {
            Path fingerprintPath = getFingerprintPath(id);
            Files.deleteIfExists(fingerprintPath);
            logger.info("Deleted fingerprint: {}", id);
        } catch (IOException e) {
            logger.error("Failed to delete fingerprint file: {}", id, e);
        }
    }

    /**
     * Get the path to a fingerprint JSON file
     */
    private Path getFingerprintPath(String id) {
        return Paths.get(outputDir, "fingerprints", id + ".json");
    }

    /**
     * Clear all fingerprints
     */
    public void clearAll() {
        fingerprintCache.clear();

        try {
            Path fingerprintsDir = Paths.get(outputDir, "fingerprints");
            if (Files.exists(fingerprintsDir)) {
                Files.list(fingerprintsDir)
                     .filter(path -> path.toString().endsWith(".json"))
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                         } catch (IOException e) {
                             logger.error("Failed to delete fingerprint: {}", path, e);
                         }
                     });
            }
            logger.info("Cleared all fingerprints");
        } catch (IOException e) {
            logger.error("Error clearing fingerprints", e);
        }
    }

    /**
     * Get number of fingerprints in the repository
     *
     * @return the number of stored fingerprints
     */
    public int count() {
        return fingerprintCache.size();
    }
}