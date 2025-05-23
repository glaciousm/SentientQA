package com.projectoracle.service;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import com.projectoracle.config.AIConfig;
import com.projectoracle.service.ai.HuggingFaceTextGenerator;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Fixed service for loading and validating HuggingFace models during application startup.
 * Uses our custom HuggingFaceTextGenerator instead of generic ModelZoo loading.
 */
@Service
public class ModelStartupService {

    private static final Logger logger = LoggerFactory.getLogger(ModelStartupService.class);

    @Autowired
    private AIConfig aiConfig;

    @Autowired
    private ModelDownloadService modelDownloadService;

    @Autowired
    private ModelStateService modelStateService;

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
        logger.info("Starting HuggingFace model initialization and validation...");

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

                    // Perform test load of models using our HuggingFace generator
                    testLoadHuggingFaceModels();

                    // Mark models as ready if all steps succeed
                    modelStateService.setModelsReady(true);
                    logger.info("HuggingFace model initialization complete - all models validated and ready to use");
                } catch (Exception e) {
                    modelStateService.setInitializationError(e.getMessage());
                    logger.error("HuggingFace model initialization failed: {}", e.getMessage(), e);
                    // In development mode, continue with fallback
                    logger.warn("Continuing in development mode with fallback text generation");
                    modelStateService.setModelsReady(false); // Allow fallback mode
                }
            }, executor).join(); // Wait for completion

            executor.shutdown();

            // Log model status
            if (modelStateService.getInitializationError() != null) {
                logger.info("Application running with fallback generation enabled due to model initialization issues");
                logger.debug("Model initialization details: {}", modelStateService.getInitializationError());
            } else {
                logger.info("HuggingFace models initialized successfully");
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
        logger.info("Validating HuggingFace model files");

        // Check language model files
        String languageModelName = aiConfig.getLanguageModelName();
        Path langModelDir = Path.of(aiConfig.getBaseModelDir(), languageModelName);

        // Essential HuggingFace model files
        Path configFile = langModelDir.resolve("config.json");
        Path tokenizerFile = langModelDir.resolve("tokenizer.json");

        // Model weight files (try different possible names)
        Path modelBinFile = langModelDir.resolve("pytorch_model.bin");
        Path modelSafetensorFile = langModelDir.resolve("model.safetensors");
        Path modelPtFile = langModelDir.resolve("pytorch_model.pt");

        // Verify essential files exist
        validateFile(configFile, 100, "HuggingFace config file");
        validateFile(tokenizerFile, 1000, "HuggingFace tokenizer file");

        // At least one model file must exist
        boolean hasModelFile = false;
        if (Files.exists(modelBinFile)) {
            validateFile(modelBinFile, 100000000, "PyTorch model.bin file"); // At least 100MB
            hasModelFile = true;
            logger.info("Found pytorch_model.bin: {} bytes", Files.size(modelBinFile));
        }
        if (Files.exists(modelSafetensorFile)) {
            validateFile(modelSafetensorFile, 100000000, "SafeTensor model file"); // At least 100MB
            hasModelFile = true;
            logger.info("Found model.safetensors: {} bytes", Files.size(modelSafetensorFile));
        }
        if (Files.exists(modelPtFile)) {
            validateFile(modelPtFile, 100000000, "PyTorch .pt model file"); // At least 100MB
            hasModelFile = true;
            logger.info("Found pytorch_model.pt: {} bytes", Files.size(modelPtFile));
        }

        if (!hasModelFile) {
            throw new IOException("No valid model weight file found in " + langModelDir +
                    ". Expected one of: pytorch_model.bin, model.safetensors, or pytorch_model.pt");
        }

        logger.info("HuggingFace model files validated successfully");
    }

    /**
     * Validate a file exists and has minimum size
     */
    private void validateFile(Path file, long minSize, String description) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException(description + " missing: " + file);
        }

        long fileSize = Files.size(file);
        if (fileSize < minSize) {
            throw new IOException(String.format(
                    "%s %s is too small: %d bytes (minimum %d)", description, file, fileSize, minSize));
        }

        logger.info("Validated {}: {} - Size: {} bytes", description, file, fileSize);
    }

    /**
     * Test load HuggingFace models using our custom generator
     */
    private void testLoadHuggingFaceModels() {
        logger.info("Test loading HuggingFace models with custom generator");

        // Try loading the language model
        String modelName = aiConfig.getLanguageModelName();
        logger.info("Loading HuggingFace model: {}", modelName);

        // First check for existence of all required files
        boolean allFilesValid = checkAllRequiredHuggingFaceFiles();

        if (!allFilesValid) {
            throw new RuntimeException("Not all required HuggingFace model files are valid");
        }

        // Now test loading with our HuggingFaceTextGenerator
        try {
            testLoadHuggingFaceModelDirectly();
            logger.info("HuggingFace model loaded successfully with custom generator");
        } catch (Exception e) {
            logger.error("Failed to load HuggingFace model: {}", e.getMessage(), e);
            throw new RuntimeException("HuggingFace model loading failed", e);
        }
    }

    /**
     * Check all required HuggingFace model files exist and have appropriate sizes
     */
    private boolean checkAllRequiredHuggingFaceFiles() {
        String modelName = aiConfig.getLanguageModelName();
        Path modelDir = Path.of(aiConfig.getBaseModelDir(), modelName);

        logger.info("Checking required HuggingFace model files in {}", modelDir);

        try {
            // Check config file
            Path configFile = modelDir.resolve("config.json");
            if (!Files.exists(configFile)) {
                logger.error("HuggingFace config file not found: {}", configFile);
                return false;
            }

            long configFileSize = Files.size(configFile);
            if (configFileSize < 100) { // 100 bytes
                logger.error("Config file too small: {} bytes", configFileSize);
                return false;
            }
            logger.info("Config file valid: {} - Size: {} bytes", configFile, configFileSize);

            // Check tokenizer file
            Path tokenizerFile = modelDir.resolve("tokenizer.json");
            if (!Files.exists(tokenizerFile)) {
                logger.error("HuggingFace tokenizer file not found: {}", tokenizerFile);
                return false;
            }

            long tokenizerFileSize = Files.size(tokenizerFile);
            if (tokenizerFileSize < 1000) { // 1KB
                logger.error("Tokenizer file too small: {} bytes", tokenizerFileSize);
                return false;
            }
            logger.info("Tokenizer file valid: {} - Size: {} bytes", tokenizerFile, tokenizerFileSize);

            // Check for at least one model weight file
            Path modelBinFile = modelDir.resolve("pytorch_model.bin");
            Path modelSafetensorFile = modelDir.resolve("model.safetensors");
            Path modelPtFile = modelDir.resolve("pytorch_model.pt");

            boolean hasValidModelFile = false;

            if (Files.exists(modelBinFile)) {
                long modelFileSize = Files.size(modelBinFile);
                if (modelFileSize >= 100000000) { // 100MB
                    logger.info("Valid pytorch_model.bin file: {} - Size: {} bytes", modelBinFile, modelFileSize);
                    hasValidModelFile = true;
                } else {
                    logger.warn("pytorch_model.bin file too small: {} bytes", modelFileSize);
                }
            }

            if (Files.exists(modelSafetensorFile)) {
                long modelFileSize = Files.size(modelSafetensorFile);
                if (modelFileSize >= 100000000) { // 100MB
                    logger.info("Valid model.safetensors file: {} - Size: {} bytes", modelSafetensorFile, modelFileSize);
                    hasValidModelFile = true;
                } else {
                    logger.warn("model.safetensors file too small: {} bytes", modelFileSize);
                }
            }

            if (Files.exists(modelPtFile)) {
                long modelFileSize = Files.size(modelPtFile);
                if (modelFileSize >= 100000000) { // 100MB
                    logger.info("Valid pytorch_model.pt file: {} - Size: {} bytes", modelPtFile, modelFileSize);
                    hasValidModelFile = true;
                } else {
                    logger.warn("pytorch_model.pt file too small: {} bytes", modelFileSize);
                }
            }

            if (!hasValidModelFile) {
                logger.error("No valid model weight files found. Checked: pytorch_model.bin, model.safetensors, pytorch_model.pt");
                return false;
            }

            logger.info("All required HuggingFace model files present and valid");
            return true;
        } catch (Exception e) {
            logger.error("Error checking HuggingFace model files: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Test load HuggingFace model using our custom generator
     */
    private void testLoadHuggingFaceModelDirectly() throws Exception {
        String modelName = aiConfig.getLanguageModelName();
        Path modelDir = Path.of(aiConfig.getBaseModelDir(), modelName);

        logger.info("Testing HuggingFace model load for {} from directory: {}", modelName, modelDir);

        // Verify the essential files exist
        Path configFile = modelDir.resolve("config.json");
        Path tokenizerFile = modelDir.resolve("tokenizer.json");

        if (!Files.exists(configFile)) {
            throw new ModelNotFoundException("HuggingFace config file not found: " + configFile);
        }

        if (!Files.exists(tokenizerFile)) {
            throw new ModelNotFoundException("HuggingFace tokenizer file not found: " + tokenizerFile);
        }

        // Use our working HuggingFaceTextGenerator
        HuggingFaceTextGenerator generator = null;
        try {
            logger.info("Loading HuggingFace model using working generator...");
            generator = HuggingFaceTextGenerator.load(modelDir, modelName);

            logger.info("Successfully loaded HuggingFace model: {}", modelName);

            // Test a simple generation to ensure the generator works
            logger.info("Testing model generation...");
            String testPrompt = "Hello, this is a test";
            String result = generator.generate(testPrompt, 20); // Short generation for testing

            if (result != null && !result.trim().isEmpty()) {
                logger.info("Model generation test successful. Generated: '{}'", result.trim());
            } else {
                logger.warn("Model generation test returned empty result");
            }

        } catch (Exception e) {
            logger.error("Failed to load or test HuggingFace model: {}", e.getMessage(), e);
            throw e;
        } finally {
            if (generator != null) {
                try {
                    generator.close();
                    logger.info("Generator closed successfully");
                } catch (Exception e) {
                    logger.warn("Error closing generator: {}", e.getMessage());
                }
            }
        }
    }
}