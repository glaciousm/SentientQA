package com.projectoracle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.projectoracle.config.AIConfig;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service for loading and validating models during application startup.
 * Ensures models are ready to use immediately and application fails fast if models are invalid.
 */
@Service
public class ModelStartupService {

    private static final Logger logger = LoggerFactory.getLogger(ModelStartupService.class);

    @Autowired
    private AIConfig aiConfig;

    @Autowired
    private ModelDownloadService modelDownloadService;

    // We can't autowire AIModelService directly because it creates a circular dependency
    // Instead, we'll use ApplicationContext to get it when needed
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;
    
    @Autowired
    private ModelStateService modelStateService;

    // Model state is now managed by ModelStateService

    /**
     * Initialize after all beans are created
     */
    @PostConstruct
    public void init() {
        logger.info("Model Startup Service initialized");
    }

    /**
     * Start loading models when the application is ready
     * This ensures all dependencies are properly initialized
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadModelsOnStartup() {
        logger.info("Starting model initialization and validation...");

        try {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            CompletableFuture.runAsync(() -> {
                try {
                    // First check if model directories exist
                    ensureModelDirectories();

                    // Download models if needed
                    downloadRequiredModels();

                    // Validate model files
                    validateModelFiles();

                    // Perform test load of models
                    testLoadModels();

                    // Mark models as ready if all steps succeed
                    modelStateService.setModelsReady(true);
                    logger.info("Model initialization complete - all models validated and ready to use");
                } catch (Exception e) {
                    modelStateService.setInitializationError(e.getMessage());
                    logger.error("Model initialization failed: {}", e.getMessage(), e);
                    // We don't throw the exception to prevent application startup failure
                    // Instead, services will check modelsReady flag
                }
            }, executor).join(); // Wait for completion

            executor.shutdown();
            
            // If models failed to initialize, log a warning that functionality will be limited
            if (!modelStateService.areModelsReady()) {
                logger.warn("APPLICATION RUNNING WITH LIMITED FUNCTIONALITY - MODELS FAILED TO INITIALIZE");
                logger.warn("Error: {}", modelStateService.getInitializationError());
            }
        } catch (Exception e) {
            logger.error("Error during model initialization: {}", e.getMessage(), e);
        }
    }

    /**
     * Ensure model directories exist
     */
    private void ensureModelDirectories() throws IOException {
        logger.info("Ensuring model directories exist");

        // Base model directory
        Path modelDir = Path.of(aiConfig.getBaseModelDir());
        if (!Files.exists(modelDir)) {
            logger.info("Creating base model directory: {}", modelDir);
            Files.createDirectories(modelDir);
        }

        // Language model directory
        Path langModelDir = Path.of(aiConfig.getBaseModelDir(), aiConfig.getLanguageModelName());
        if (!Files.exists(langModelDir)) {
            logger.info("Creating language model directory: {}", langModelDir);
            Files.createDirectories(langModelDir);
        }

        // Cache directory
        Path cacheDir = Path.of(aiConfig.getCacheDir());
        if (!Files.exists(cacheDir)) {
            logger.info("Creating cache directory: {}", cacheDir);
            Files.createDirectories(cacheDir);
        }
    }

    /**
     * Download all required models
     */
    private void downloadRequiredModels() throws IOException {
        logger.info("Downloading required models if needed");

        // Download language model
        String languageModelName = aiConfig.getLanguageModelName();
        if (!modelDownloadService.isModelPresent(languageModelName)) {
            logger.info("Language model {} not found, downloading...", languageModelName);
            Path modelPath = modelDownloadService.downloadModelIfNeeded(languageModelName);
            logger.info("Downloaded language model to: {}", modelPath);
        } else {
            logger.info("Language model {} already present", languageModelName);
        }
    }

    /**
     * Validate model files for integrity
     */
    private void validateModelFiles() throws IOException {
        logger.info("Validating model files");

        // Check language model files
        String languageModelName = aiConfig.getLanguageModelName();
        Path langModelDir = Path.of(aiConfig.getBaseModelDir(), languageModelName);
        Path modelFile = langModelDir.resolve(aiConfig.getModelFormat());
        Path configFile = langModelDir.resolve("config.json");
        Path tokenizerFile = langModelDir.resolve("tokenizer.json");
        Path ptFile = langModelDir.resolve(languageModelName + ".pt");

        // Verify files exist and have reasonable sizes
        validateFile(modelFile, 100000000); // At least 100MB for model file
        validateFile(configFile, 100); // At least 100 bytes for config
        validateFile(tokenizerFile, 1000); // At least 1KB for tokenizer

        // Create PT file if it doesn't exist
        if (!Files.exists(ptFile)) {
            logger.info("Creating .pt model file at {}", ptFile);
            Files.copy(modelFile, ptFile);
        } else {
            logger.info("PT model file already exists at {}", ptFile);
            // Verify file size
            if (Files.size(ptFile) < 100000000) {
                logger.warn("PT file too small ({}), recreating", Files.size(ptFile));
                Files.copy(modelFile, ptFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Validate a file exists and has minimum size
     */
    private void validateFile(Path file, long minSize) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("Required file missing: " + file);
        }

        long fileSize = Files.size(file);
        if (fileSize < minSize) {
            throw new IOException(String.format(
                    "File %s is too small: %d bytes (minimum %d)", file, fileSize, minSize));
        }

        logger.info("Validated file: {} - Size: {} bytes", file, fileSize);
    }

    /**
     * Test load models to verify they can be loaded successfully
     */
    private void testLoadModels() throws ModelNotFoundException, MalformedModelException, IOException {
        logger.info("Test loading models");

        // Try loading the language model
        logger.info("Loading language model: {}", aiConfig.getLanguageModelName());
        
        // Use a direct approach to test model loading without depending on AIModelService
        // This breaks the circular dependency
        testLoadModelDirectly();
    }
    
    /**
     * Test load a model directly without using AIModelService
     */
    private void testLoadModelDirectly() throws IOException, ModelNotFoundException, MalformedModelException {
        String modelName = aiConfig.getLanguageModelName();
        Path modelDir = aiConfig.getModelPath(modelName);
        Path modelFile = modelDir.resolve(aiConfig.getModelFormat());
        Path ptFile = modelDir.resolve(modelName + ".pt");
        
        logger.info("Directly testing model load for {} from {}", modelName, ptFile);
        
        // Verify the files exist
        if (!Files.exists(modelFile)) {
            throw new ModelNotFoundException("Model file not found: " + modelFile);
        }
        
        if (!Files.exists(ptFile)) {
            // Try to create it
            logger.info("PT file does not exist. Creating from model file.");
            Files.copy(modelFile, ptFile);
        }
        
        // Try to load the model using basic DJL APIs without requiring AIModelService
        try {
            ai.djl.Model model = ai.djl.Model.newInstance("PyTorch");
            model.load(ptFile);
            logger.info("Successfully loaded model directly: {}", modelName);
            model.close();
        } catch (Exception e) {
            logger.error("Failed to load model directly: {}", e.getMessage());
            throw new MalformedModelException("Failed to load model directly", e);
        }
    }

    /**
     * Check if models are ready to use
     */
    public boolean areModelsReady() {
        return modelStateService.areModelsReady();
    }

    /**
     * Get initialization error if models failed to initialize
     */
    public String getInitializationError() {
        return modelStateService.getInitializationError();
    }
}