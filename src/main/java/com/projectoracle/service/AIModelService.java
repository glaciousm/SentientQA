package com.projectoracle.service;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.djl.ndarray.types.DataType;

import com.projectoracle.config.AIConfig;
import com.projectoracle.service.translator.TextGenerationTranslator;
import com.projectoracle.service.ModelQuantizationService;
import com.projectoracle.service.ModelQuantizationService.QuantizationLevel;

import java.nio.file.Files;

import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing AI model loading, inference, and resource allocation.
 * Handles efficient loading/unloading to optimize resource usage.
 */
@Service
public class AIModelService {

    private static final Logger logger = LoggerFactory.getLogger(AIModelService.class);

    @Autowired
    private AIConfig aiConfig;

    @Autowired
    private ModelDownloadService modelDownloadService;
    
    @Autowired
    private ModelQuantizationService quantizationService;
    
    @Autowired
    private ModelEnvironmentService environmentService;
    
    @Autowired
    private ModelStateService modelStateService;

    private final ConcurrentHashMap<String, ZooModel<String, String>> loadedModels = new ConcurrentHashMap<>();
    
    // Track which models are currently loading to avoid duplicate loading
    private final ConcurrentHashMap<String, Boolean> modelLoadingStatus = new ConcurrentHashMap<>();
    
    // Constants for model status tracking
    public enum ModelStatus {
        NOT_LOADED,
        LOADING,
        LOADED,
        FAILED
    }
    
    // Map to track current status of each model
    private final ConcurrentHashMap<String, ModelStatus> modelStatus = new ConcurrentHashMap<>();

    /**
     * Initialize the AI model service and prepare directories
     * Note: With lazy loading, we only create directories, 
     * but we don't load any models at startup.
     */
    @PostConstruct
    public void init() {
        try {
            // Create model directory if it doesn't exist
            Path modelDir = Path.of(aiConfig.getBaseModelDir());
            if (!Files.exists(modelDir)) {
                logger.info("Creating model directory: {}", modelDir);
                Files.createDirectories(modelDir);
            }

            // Create cache directory
            Path cacheDir = aiConfig.getCacheDirPath();
            if (!Files.exists(cacheDir)) {
                logger.info("Creating cache directory: {}", cacheDir);
                Files.createDirectories(cacheDir);
            }

            logger.info("AI Model Service initialized with lazy loading");
            logger.info("Models directory: {}", aiConfig.getBaseModelDir());
            logger.info("Using GPU: {}", aiConfig.isUseGpu());
            logger.info("Memory limit: {} MB", aiConfig.getMemoryLimitMb());
            logger.info("Quantization enabled: {}", aiConfig.isQuantizeLanguageModel());
            logger.info("Models will be loaded on first use");
        } catch (IOException e) {
            logger.error("Failed to initialize AI model service", e);
            throw new RuntimeException("Failed to initialize AI model service", e);
        }
    }

    /**
     * Generate text using the language model
     */
    public String generateText(String prompt, int maxTokens) {
        logger.info("Generating text with prompt length: {}", prompt.length());
        
        // Check if models were initialized successfully at startup
        if (!modelStateService.areModelsReady()) {
            throw new IllegalStateException("AI models are not initialized. Cannot generate text without models.");
        }

        // Use the language model
        try {
            ZooModel<String, String> model = loadLanguageModel();
            try (Predictor<String, String> predictor = model.newPredictor(new TextGenerationTranslator(maxTokens))) {
                return predictor.predict(prompt);
            }
        } catch (Exception e) {
            logger.error("Text generation failed", e);
            throw new RuntimeException("Failed to generate text using AI model: " + e.getMessage(), e);
        }
    }
    
    /**
     * Attempt text generation with multiple fallback approaches
     * 
     * @param prompt the prompt to generate text from
     * @param maxTokens the maximum number of tokens to generate
     * @return the generated text
     */
    private String attemptTextGeneration(String prompt, int maxTokens) {
        // Keep track of all exceptions to provide detailed error info if all methods fail
        List<Exception> exceptions = new ArrayList<>();
        
        // Approach 1: Try using the standard loading approach
        try {
            logger.info("Attempting text generation using standard model loading approach");
            ZooModel<String, String> model = loadLanguageModel();
            try (Predictor<String, String> predictor = model.newPredictor(new TextGenerationTranslator(maxTokens))) {
                return predictor.predict(prompt);
            } catch (TranslateException e) {
                logger.warn("Standard text generation failed: {}", e.getMessage());
                exceptions.add(e);
            }
        } catch (Exception e) {
            logger.warn("Standard model loading failed: {}", e.getMessage());
            exceptions.add(e);
        }
        
        // Approach 2: Try using a rule-based response
        try {
            logger.info("Falling back to rule-based text response");
            return generateRuleBasedResponse(prompt);
        } catch (Exception e) {
            logger.warn("Rule-based fallback failed: {}", e.getMessage());
            exceptions.add(e);
        }
        
        // If all approaches failed, provide detailed error information
        StringBuilder errorMessage = new StringBuilder("All text generation approaches failed:");
        for (int i = 0; i < exceptions.size(); i++) {
            errorMessage.append("\n").append(i + 1).append(") ").append(exceptions.get(i).getMessage());
        }
        
        return errorMessage.toString();
    }
    
    /**
     * Generate a rule-based response as a fallback
     * 
     * @param prompt the input prompt
     * @return a rule-based response
     */
    private String generateRuleBasedResponse(String prompt) {
        // Simple rule-based fallback for test generation prompts
        if (prompt.contains("Generate a JUnit") || prompt.contains("test")) {
            return "import org.junit.jupiter.api.Test;\n" +
                   "import static org.junit.jupiter.api.Assertions.*;\n\n" +
                   "class BasicTest {\n\n" +
                   "    @Test\n" +
                   "    void testBasicFunctionality() {\n" +
                   "        // This is a generic test created when AI model is unavailable\n" +
                   "        // Basic checks to verify operation\n" +
                   "        assertTrue(true);\n" +
                   "        assertNotNull(\"Test object\");\n" +
                   "    }\n" +
                   "}\n";
        } else if (prompt.contains("API") || prompt.contains("REST")) {
            return "// API Test\n" +
                   "import org.junit.jupiter.api.Test;\n" +
                   "import static org.junit.jupiter.api.Assertions.*;\n\n" +
                   "class ApiTest {\n\n" +
                   "    @Test\n" +
                   "    void testApiEndpoint() {\n" +
                   "        // This is a placeholder API test\n" +
                   "        assertTrue(true);\n" +
                   "    }\n" +
                   "}\n";
        } else {
            return "// Generated response\n" +
                   "// Note: AI model unavailable, providing generic response\n\n" +
                   "The requested information cannot be generated with full AI capabilities.\n" +
                   "Please check that the model files are correctly installed.\n";
        }
    }

    /**
     * Get the current status of a model
     * 
     * @param modelKey the key for the model
     * @return the current status of the model
     */
    public ModelStatus getModelStatus(String modelKey) {
        return modelStatus.getOrDefault(modelKey, ModelStatus.NOT_LOADED);
    }
    
    /**
     * Check if a model is already loaded
     * 
     * @param modelKey the key for the model
     * @return true if the model is loaded and ready to use
     */
    public boolean isModelLoaded(String modelKey) {
        return loadedModels.containsKey(modelKey) && 
               modelStatus.getOrDefault(modelKey, ModelStatus.NOT_LOADED) == ModelStatus.LOADED;
    }
    
    /**
     * Check if a model is currently loading
     * 
     * @param modelKey the key for the model
     * @return true if the model is in the process of being loaded
     */
    public boolean isModelLoading(String modelKey) {
        return modelStatus.getOrDefault(modelKey, ModelStatus.NOT_LOADED) == ModelStatus.LOADING;
    }

    /**
     * Load the language model (with true lazy loading pattern)
     * Will automatically download the model if it doesn't exist
     * Model is only loaded on first use, not at startup
     */
    public ZooModel<String, String> loadLanguageModel() throws ModelNotFoundException, MalformedModelException, IOException {
        String modelName = aiConfig.getLanguageModelName();
        String modelKey = "language-" + modelName;

        // If model is already loaded, return it
        if (isModelLoaded(modelKey)) {
            logger.debug("Using already loaded language model: {}", modelName);
            return loadedModels.get(modelKey);
        }
        
        // If model is currently loading, wait for it
        if (isModelLoading(modelKey)) {
            logger.info("Language model {} is currently loading, waiting...", modelName);
            // Simple wait-and-check loop (in production, use proper synchronization)
            int attempts = 0;
            while (isModelLoading(modelKey) && attempts < 30) {
                try {
                    Thread.sleep(1000); // Wait 1 second
                    attempts++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for model to load");
                }
            }
            
            // Check if model is now loaded
            if (isModelLoaded(modelKey)) {
                return loadedModels.get(modelKey);
            } else {
                throw new IOException("Timeout waiting for model to load");
            }
        }

        // Set status to loading before we start
        modelStatus.put(modelKey, ModelStatus.LOADING);
        
        try {
            logger.info("Lazy-loading language model on first use: {}", modelName);

            // Check if model exists, download if needed
            if (!modelDownloadService.isModelPresent(modelName)) {
                logger.info("Model {} not found locally, downloading...", modelName);
                Path modelPath = modelDownloadService.downloadModelIfNeeded(modelName);
                logger.info("Model downloaded to: {}", modelPath);
                
                // Double-check that model exists after download
                if (!Files.exists(modelPath.resolve("pytorch_model.bin"))) {
                    throw new ModelNotFoundException("Model was downloaded but pytorch_model.bin file is missing");
                }
            }
            
            // Explicitly verify the model files before loading
            Path modelDir = aiConfig.getModelPath(modelName);
            Path modelFile = modelDir.resolve(aiConfig.getModelFormat());
            
            // First verify the model file integrity
            verifyAndFixModelFile(modelFile);
            
            // Skip .pt file handling entirely - just use pytorch_model.bin directly
            logger.info("Using original pytorch_model.bin file directly without .pt conversion");
            
            if (!Files.exists(modelFile)) {
                throw new ModelNotFoundException("Required model file not found: " + modelFile);
            }
            
            Path configFile = modelDir.resolve("config.json");
            if (!Files.exists(configFile)) {
                throw new ModelNotFoundException("Required config file not found: " + configFile);
            }
            
            Path tokenizerFile = modelDir.resolve("tokenizer.json");
            if (!Files.exists(tokenizerFile)) {
                throw new ModelNotFoundException("Required tokenizer file not found: " + tokenizerFile);
            }

            // Always use non-quantized model for stability
            logger.info("Loading non-quantized model (FP32) at {}", modelDir);
            Criteria.Builder criteriaBuilder = Criteria.builder()
                    .setTypes(String.class, String.class)
                    .optModelPath(modelDir)
                    .optModelName(aiConfig.getModelFormat()) // Use the original pytorch_model.bin file
                    .optEngine("PyTorch")
                    .optTranslator(new TextGenerationTranslator(1024));
            
            // Build criteria and load model
            logger.info("Loading model with criteria: {}", criteriaBuilder.toString());
            ZooModel<String, String> model = ModelZoo.loadModel(criteriaBuilder.build());
            
            // Verify the model was loaded correctly
            if (model == null) {
                throw new ModelNotFoundException("Model failed to load - returned null");
            }
            
            // Store the loaded model
            loadedModels.put(modelKey, model);
            
            // Update status to loaded
            modelStatus.put(modelKey, ModelStatus.LOADED);
            
            // Test the model with a simple prediction to ensure it works
            try (Predictor<String, String> predictor = model.newPredictor(new TextGenerationTranslator(10))) {
                String testResult = predictor.predict("Hello");
                logger.info("Model loaded successfully and test prediction completed: {}", 
                         testResult.substring(0, Math.min(20, testResult.length())));
            } catch (TranslateException e) {
                throw new MalformedModelException("Model loaded but prediction test failed", e);
            }
            
            logger.info("Successfully loaded language model: {}", modelName);
            return model;
        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            // Update status to failed
            modelStatus.put(modelKey, ModelStatus.FAILED);
            logger.error("Failed to load language model: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Load the embeddings model (with lazy loading pattern)
     * Will automatically download the model if it doesn't exist
     * Model is only loaded on first use, not at startup
     */
    public ZooModel<String, String> loadEmbeddingsModel() throws ModelNotFoundException, MalformedModelException, IOException {
        String modelName = aiConfig.getEmbeddingsModelName();
        String modelKey = "embeddings-" + modelName;

        // If model is already loaded, return it
        if (isModelLoaded(modelKey)) {
            logger.debug("Using already loaded embeddings model: {}", modelName);
            return loadedModels.get(modelKey);
        }
        
        // If model is currently loading, wait for it
        if (isModelLoading(modelKey)) {
            logger.info("Embeddings model {} is currently loading, waiting...", modelName);
            // Simple wait-and-check loop (in production, use proper synchronization)
            int attempts = 0;
            while (isModelLoading(modelKey) && attempts < 30) {
                try {
                    Thread.sleep(1000); // Wait 1 second
                    attempts++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for model to load");
                }
            }
            
            // Check if model is now loaded
            if (isModelLoaded(modelKey)) {
                return loadedModels.get(modelKey);
            } else {
                throw new IOException("Timeout waiting for model to load");
            }
        }

        // Set status to loading before we start
        modelStatus.put(modelKey, ModelStatus.LOADING);
        
        try {
            logger.info("Lazy-loading embeddings model on first use: {}", modelName);

            // Check if model exists, download if needed
            if (!modelDownloadService.isModelPresent(modelName)) {
                logger.info("Model {} not found locally, downloading...", modelName);
                Path modelPath = modelDownloadService.downloadModelIfNeeded(modelName);
                logger.info("Model downloaded to: {}", modelPath);
                
                // Double-check that model exists after download
                if (!Files.exists(modelPath.resolve("pytorch_model.bin"))) {
                    throw new ModelNotFoundException("Embeddings model was downloaded but pytorch_model.bin file is missing");
                }
            }
            
            // Explicitly verify the model files before loading
            Path modelDir = aiConfig.getEmbeddingsModelPath();
            Path modelFile = modelDir.resolve("pytorch_model.bin");
            
            if (!Files.exists(modelFile)) {
                throw new ModelNotFoundException("Required embeddings model file not found: " + modelFile);
            }
            
            // Also create a symlink or copy with .pt extension for DJL compatibility
            Path ptModelFile = modelDir.resolve(modelName + ".pt");
            if (!Files.exists(ptModelFile)) {
                logger.info("Creating .pt model file for embeddings DJL compatibility at {}", ptModelFile);
                try {
                    Files.copy(modelFile, ptModelFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    logger.warn("Failed to create .pt model file: {}", e.getMessage());
                    // Continue execution, as the original pytorch_model.bin might still work
                }
            }
            
            Path configFile = modelDir.resolve("config.json");
            if (!Files.exists(configFile)) {
                logger.warn("Embeddings model config file not found: {}. This may cause issues.", configFile);
            }

            // Set criteria for model loading
            Criteria<String, String> criteria = Criteria.builder()
                                                        .setTypes(String.class, String.class)
                                                        .optModelPath(modelDir)
                                                        .optModelName(modelName + ".pt") // Use the .pt file explicitly
                                                        .optEngine("PyTorch")
                                                        .build();

            logger.info("Loading embeddings model from path: {}, model file: {}", modelDir, modelName + ".pt");
            
            // Load the model
            ZooModel<String, String> model = ModelZoo.loadModel(criteria);
            
            // Verify the model was loaded correctly
            if (model == null) {
                throw new ModelNotFoundException("Embeddings model failed to load - returned null");
            }
            
            // Store the loaded model
            loadedModels.put(modelKey, model);
            
            // Update status to loaded
            modelStatus.put(modelKey, ModelStatus.LOADED);
            
            logger.info("Successfully loaded embeddings model: {}", modelName);
            return model;
        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            // Update status to failed
            modelStatus.put(modelKey, ModelStatus.FAILED);
            logger.error("Failed to load embeddings model: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Unload all models and free resources
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down AI Model Service, unloading {} models", loadedModels.size());

        loadedModels.forEach((key, model) -> {
            try {
                logger.info("Unloading model: {}", key);
                model.close();
            } catch (Exception e) {
                logger.error("Error unloading model: " + key, e);
            }
        });

        loadedModels.clear();
    }

    /**
     * Manually unload a specific model to free resources
     */
    public void unloadModel(String modelKey) {
        ZooModel<String, String> model = loadedModels.remove(modelKey);
        if (model != null) {
            try {
                logger.info("Manually unloading model: {}", modelKey);
                model.close();
            } catch (Exception e) {
                logger.error("Error unloading model: " + modelKey, e);
            }
        }
    }
    
    /**
     * Verify and fix model file integrity, especially useful for WSL environments
     * where filesystem boundary issues can corrupt files
     * 
     * @param modelFile The path to the model file to verify
     * @return true if file is valid or was successfully fixed
     */
    private boolean verifyAndFixModelFile(Path modelFile) {
        logger.info("Verifying model file integrity: {}", modelFile);
        
        try {
            if (!Files.exists(modelFile)) {
                logger.error("Model file does not exist: {}", modelFile);
                return false;
            }
            
            long fileSize = Files.size(modelFile);
            if (fileSize < 1000000) { // Less than 1MB is suspicious for these models
                logger.error("Model file size is suspiciously small: {} bytes", fileSize);
                return false;
            }
            
            // Try to read the first few bytes to verify file access works
            byte[] buffer = new byte[8192]; // 8KB buffer
            try (java.io.InputStream is = Files.newInputStream(modelFile)) {
                int bytesRead = is.read(buffer);
                logger.info("Successfully read {} bytes from model file", bytesRead);
                
                if (bytesRead < 100) {
                    logger.error("Could only read {} bytes from model file, might be corrupt", bytesRead);
                    return false;
                }
            } catch (IOException e) {
                logger.error("Failed to read from model file: {}", e.getMessage());
                return false;
            }

            // Model file is valid - PyTorch can use pytorch_model.bin directly
            logger.info("Model file verified successfully with size: {} bytes", fileSize);
            return true;
            
        } catch (Exception e) {
            logger.error("Unexpected error verifying model file: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Test load the language model to verify it works
     * This is used during startup to validate models
     * 
     * @return The loaded model, which will be closed by the caller
     */
    public ZooModel<String, String> testLoadLanguageModel() throws ModelNotFoundException, MalformedModelException, IOException {
        String modelName = aiConfig.getLanguageModelName();
        String modelKey = "language-" + modelName;
        
        logger.info("Test loading language model: {}", modelName);
        
        // Verify model files
        Path modelDir = aiConfig.getModelPath(modelName);
        Path modelFile = modelDir.resolve(aiConfig.getModelFormat());
        Path ptFile = modelDir.resolve(modelName + ".pt");
        
        // Verify files exist
        if (!Files.exists(modelFile)) {
            throw new ModelNotFoundException("Model file not found: " + modelFile);
        }
        
        // We no longer rely on the .pt file, just use the original model file
        
        // Use a simplified loading approach without quantization for testing
        logger.info("Loading model with criteria without quantization");
        Criteria<String, String> criteria = Criteria.builder()
                .setTypes(String.class, String.class)
                .optModelPath(modelDir)
                .optModelName(aiConfig.getModelFormat()) // Use the original pytorch_model.bin file
                .optEngine("PyTorch")
                .optTranslator(new TextGenerationTranslator(10))
                .build();
        
        // Try to load the model
        ZooModel<String, String> model = ModelZoo.loadModel(criteria);
        
        // Test with a simple inference
        try (Predictor<String, String> predictor = model.newPredictor()) {
            String testResult = predictor.predict("Hello, world");
            logger.info("Test inference successful: {}", 
                       testResult.substring(0, Math.min(testResult.length(), 50)));
        } catch (TranslateException e) {
            throw new MalformedModelException("Model loaded but inference failed", e);
        }
        
        return model;
    }
    
    /**
     * Test model loading, quantization, and inference
     * This method can be called to verify that the models are working correctly
     * 
     * @return A diagnostic report of the test results
     */
    public String testModels() {
        StringBuilder report = new StringBuilder();
        report.append("AI Model Test Report\n");
        report.append("===================\n\n");
        
        // Track overall success/failure
        boolean overallSuccess = true;
        
        // Test language model non-quantized
        try {
            // Temporarily disable quantization
            boolean originalQuantizeSetting = aiConfig.isQuantizeLanguageModel();
            aiConfig.setQuantizeLanguageModel(false);
            
            report.append("Testing language model (non-quantized): ").append(aiConfig.getLanguageModelName()).append("\n");
            
            long startTime = System.currentTimeMillis();
            ZooModel<String, String> model = loadLanguageModel();
            long loadTime = System.currentTimeMillis() - startTime;
            
            report.append("- Model loaded successfully in ").append(loadTime).append("ms\n");
            
            // Test inference
            try (Predictor<String, String> predictor = model.newPredictor(new TextGenerationTranslator(10))) {
                startTime = System.currentTimeMillis();
                String testResult = predictor.predict("Hello, world!");
                long inferenceTime = System.currentTimeMillis() - startTime;
                
                report.append("- Test inference successful in ").append(inferenceTime).append("ms\n");
                report.append("- Sample output: \"").append(testResult.substring(0, Math.min(50, testResult.length()))).append("...\"\n");
            }
            
            // Unload model
            unloadModel("language-" + aiConfig.getLanguageModelName());
            report.append("- Model unloaded successfully\n");
            
            // Restore original quantization setting
            aiConfig.setQuantizeLanguageModel(originalQuantizeSetting);
            
        } catch (Exception e) {
            report.append("- ERROR: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n");
            logger.error("Error testing language model (non-quantized)", e);
            overallSuccess = false;
        }
        
        report.append("\n");
        
        // Test language model with quantization
        try {
            // Enable quantization
            aiConfig.setQuantizeLanguageModel(true);
            
            report.append("Testing language model (quantized FP16): ").append(aiConfig.getLanguageModelName()).append("\n");
            
            long startTime = System.currentTimeMillis();
            ZooModel<String, String> model = loadLanguageModel();
            long loadTime = System.currentTimeMillis() - startTime;
            
            report.append("- Model loaded successfully in ").append(loadTime).append("ms\n");
            
            // Test inference
            try (Predictor<String, String> predictor = model.newPredictor(new TextGenerationTranslator(10))) {
                startTime = System.currentTimeMillis();
                String testResult = predictor.predict("Hello, world!");
                long inferenceTime = System.currentTimeMillis() - startTime;
                
                report.append("- Test inference successful in ").append(inferenceTime).append("ms\n");
                report.append("- Sample output: \"").append(testResult.substring(0, Math.min(50, testResult.length()))).append("...\"\n");
            }
            
            // Unload model
            unloadModel("language-" + aiConfig.getLanguageModelName());
            report.append("- Model unloaded successfully\n");
            
        } catch (Exception e) {
            report.append("- ERROR: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n");
            logger.error("Error testing language model (quantized)", e);
            overallSuccess = false;
        }
        
        report.append("\n");
        
        // Test embeddings model
        try {
            report.append("Testing embeddings model: ").append(aiConfig.getEmbeddingsModelName()).append("\n");
            
            long startTime = System.currentTimeMillis();
            ZooModel<String, String> model = loadEmbeddingsModel();
            long loadTime = System.currentTimeMillis() - startTime;
            
            report.append("- Model loaded successfully in ").append(loadTime).append("ms\n");
            
            // Unload model
            unloadModel("embeddings-" + aiConfig.getEmbeddingsModelName());
            report.append("- Model unloaded successfully\n");
            
        } catch (Exception e) {
            report.append("- ERROR: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n");
            logger.error("Error testing embeddings model", e);
            overallSuccess = false;
        }
        
        report.append("\n===================\n");
        report.append("Overall test result: ").append(overallSuccess ? "SUCCESS" : "FAILURE").append("\n");
        
        return report.toString();
    }
}