package com.projectoracle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.projectoracle.config.AIConfig;

import jakarta.annotation.PostConstruct;

/**
 * Service for managing model loading environment settings.
 * Handles JVM and native library environment variables to optimize model loading.
 */
@Service
public class ModelEnvironmentService {

    private static final Logger logger = LoggerFactory.getLogger(ModelEnvironmentService.class);

    @Autowired
    private AIConfig aiConfig;

    /**
     * Set up environment variables and system properties for optimal model loading
     */
    @PostConstruct
    public void setupEnvironment() {
        logger.info("Setting up model environment variables");

        try {
            // Set DJL system properties
            String memoryLimitMb = String.valueOf(aiConfig.getMemoryLimitMb());
            System.setProperty("ai.djl.pytorch.memory_limit", memoryLimitMb + "MB");
            logger.info("Set DJL memory limit to {}MB", memoryLimitMb);

            // Set PyTorch environment variables
            setEnvVariable("OMP_NUM_THREADS", "1");
            setEnvVariable("MKL_NUM_THREADS", "1");

            // Set caching directory to native Linux filesystem
            if (aiConfig.getCacheDir().startsWith("/mnt/")) {
                logger.info("Cache directory is on Windows filesystem. Changing to Linux native filesystem");
                String newCacheDir = "/tmp/djl_cache";
                System.setProperty("DJL_CACHE_DIR", newCacheDir);
                logger.info("Set DJL cache directory to {}", newCacheDir);
            } else {
                System.setProperty("DJL_CACHE_DIR", aiConfig.getCacheDir());
            }

            // Disable tensor parallel feature which can cause issues in WSL environment
            System.setProperty("ai.djl.pytorch.disable_tensor_parallel", "true");

            // Set timeout for PyTorch operations
            System.setProperty("ai.djl.pytorch.timeout", String.valueOf(aiConfig.getOperationTimeoutMs()));

            logger.info("Model environment variables set successfully");
        } catch (Exception e) {
            logger.error("Error setting environment variables: {}", e.getMessage());
        }
    }

    /**
     * Helper method to set an environment variable using reflection
     * (since System.setenv is not directly available in Java)
     */
    private void setEnvVariable(String key, String value) {
        try {
            java.lang.reflect.Field field = System.getenv().getClass().getDeclaredField("m");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> map = (java.util.Map<String, String>) field.get(System.getenv());
            map.put(key, value);
            logger.info("Set environment variable {}={}", key, value);
        } catch (Exception e) {
            logger.warn("Could not set environment variable {}: {}", key, e.getMessage());
            // Alternative: use a ProcessBuilder to run commands if this approach doesn't work
        }
    }
}