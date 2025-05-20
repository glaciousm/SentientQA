package com.projectoracle.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import jakarta.annotation.PostConstruct;

import com.projectoracle.service.crawler.APITestGenerationService.APIEndpoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Repository for storing and retrieving API endpoints.
 * Provides file-based persistence for API discovery data.
 */
@Repository
public class APIEndpointRepository {

    private static final Logger logger = LoggerFactory.getLogger(APIEndpointRepository.class);
    private final ObjectMapper objectMapper;
    private final Map<String, APIEndpoint> endpointsCache = new ConcurrentHashMap<>();

    @Value("${app.directories.output:output}")
    private String outputDir;

    public APIEndpointRepository() {
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

            Path endpointsDir = outputPath.resolve("api-endpoints");
            if (!Files.exists(endpointsDir)) {
                logger.info("Creating API endpoints directory: {}", endpointsDir);
                Files.createDirectories(endpointsDir);
            }

            // Load existing endpoints
            loadExistingEndpoints(endpointsDir);

            logger.info("API Endpoint Repository initialized");
        } catch (IOException e) {
            logger.error("Failed to initialize API endpoint repository", e);
            throw new RuntimeException("Failed to initialize API endpoint repository", e);
        }
    }

    /**
     * Load existing endpoints from disk
     */
    private void loadExistingEndpoints(Path endpointsDir) {
        try {
            if (Files.exists(endpointsDir)) {
                Files.list(endpointsDir)
                     .filter(path -> path.toString().endsWith(".json"))
                     .forEach(path -> {
                         try {
                             APIEndpoint endpoint = objectMapper.readValue(path.toFile(), APIEndpoint.class);
                             String key = generateEndpointKey(endpoint);
                             endpointsCache.put(key, endpoint);
                         } catch (IOException e) {
                             logger.error("Failed to load API endpoint: {}", path, e);
                         }
                     });
                logger.info("Loaded {} existing API endpoints", endpointsCache.size());
            }
        } catch (IOException e) {
            logger.error("Error loading existing API endpoints", e);
        }
    }

    /**
     * Save an API endpoint
     *
     * @param endpoint the endpoint to save
     * @return the saved endpoint
     */
    public APIEndpoint saveEndpoint(APIEndpoint endpoint) {
        if (endpoint == null || endpoint.getUrl() == null) {
            logger.warn("Cannot save null endpoint or endpoint without URL");
            return endpoint;
        }

        String key = generateEndpointKey(endpoint);
        endpointsCache.put(key, endpoint);

        try {
            Path endpointPath = getEndpointPath(key);
            objectMapper.writeValue(endpointPath.toFile(), endpoint);
            logger.debug("Saved API endpoint: {}", key);
        } catch (IOException e) {
            logger.error("Failed to save API endpoint: {}", key, e);
        }

        return endpoint;
    }

    /**
     * Save a list of API endpoints
     *
     * @param endpoints the list of endpoints to save
     */
    public void saveEndpoints(List<APIEndpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return;
        }

        for (APIEndpoint endpoint : endpoints) {
            saveEndpoint(endpoint);
        }

        logger.info("Saved {} API endpoints", endpoints.size());
    }

    /**
     * Find an endpoint by URL and method
     *
     * @param url the URL of the endpoint
     * @param method the HTTP method of the endpoint
     * @return the endpoint, or null if not found
     */
    public APIEndpoint findByUrlAndMethod(String url, String method) {
        String key = generateEndpointKey(url, method);
        return endpointsCache.get(key);
    }

    /**
     * Find all endpoints
     *
     * @return a list of all endpoints
     */
    public List<APIEndpoint> findAll() {
        return new ArrayList<>(endpointsCache.values());
    }

    /**
     * Find endpoints by URL pattern
     *
     * @param urlPattern the URL pattern to search for
     * @return a list of matching endpoints
     */
    public List<APIEndpoint> findByUrlPattern(String urlPattern) {
        return endpointsCache.values().stream()
                           .filter(ep -> ep.getUrl() != null && ep.getUrl().contains(urlPattern))
                           .collect(Collectors.toList());
    }

    /**
     * Find endpoints by HTTP method
     *
     * @param method the HTTP method to search for
     * @return a list of matching endpoints
     */
    public List<APIEndpoint> findByMethod(String method) {
        return endpointsCache.values().stream()
                           .filter(ep -> method.equalsIgnoreCase(ep.getMethod()))
                           .collect(Collectors.toList());
    }

    /**
     * Delete an endpoint
     *
     * @param url the URL of the endpoint
     * @param method the HTTP method of the endpoint
     */
    public void deleteByUrlAndMethod(String url, String method) {
        String key = generateEndpointKey(url, method);
        endpointsCache.remove(key);

        try {
            Path endpointPath = getEndpointPath(key);
            Files.deleteIfExists(endpointPath);
            logger.info("Deleted API endpoint: {}", key);
        } catch (IOException e) {
            logger.error("Failed to delete API endpoint file: {}", key, e);
        }
    }

    /**
     * Generate a key for an endpoint
     */
    private String generateEndpointKey(APIEndpoint endpoint) {
        return generateEndpointKey(endpoint.getUrl(), endpoint.getMethod());
    }

    /**
     * Generate a key for an endpoint based on URL and method
     */
    private String generateEndpointKey(String url, String method) {
        // Normalize the method to uppercase
        String normalizedMethod = method != null ? method.toUpperCase() : "GET";
        
        // Create a key that combines method and URL
        return normalizedMethod + "_" + url.hashCode();
    }

    /**
     * Get the path to an endpoint JSON file
     */
    private Path getEndpointPath(String key) {
        return Paths.get(outputDir, "api-endpoints", key + ".json");
    }

    /**
     * Clear all endpoints
     */
    public void clearAll() {
        endpointsCache.clear();

        try {
            Path endpointsDir = Paths.get(outputDir, "api-endpoints");
            if (Files.exists(endpointsDir)) {
                Files.list(endpointsDir)
                     .filter(path -> path.toString().endsWith(".json"))
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                         } catch (IOException e) {
                             logger.error("Failed to delete API endpoint: {}", path, e);
                         }
                     });
            }
            logger.info("Cleared all API endpoints");
        } catch (IOException e) {
            logger.error("Error clearing API endpoints", e);
        }
    }

    /**
     * Get number of endpoints in the repository
     *
     * @return the number of stored endpoints
     */
    public int count() {
        return endpointsCache.size();
    }
}