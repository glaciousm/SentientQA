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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        try {
            ZooModel<String, String> model = loadLanguageModel();
            try (Predictor<String, String> predictor = model.newPredictor(new TextGenerationTranslator(maxTokens))) {
                return predictor.predict(prompt);
            } catch (TranslateException e) {
                logger.error("Failed to generate text", e);
                return "Error generating text: " + e.getMessage();
            }
        } catch (Exception e) {
            logger.error("Failed to generate text due to model issues", e);
            return "Error loading language model: " + e.getMessage();
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
                modelDownloadService.downloadModelIfNeeded(modelName);
            }

            // Determine whether to use quantization
            Criteria.Builder criteriaBuilder;
            
            if (aiConfig.isQuantizeLanguageModel()) {
                // Use quantized model (FP16 or INT8 based on config)
                QuantizationLevel level = aiConfig.getQuantizationLevel().equals("INT8") ? 
                            QuantizationLevel.INT8 : QuantizationLevel.FP16;
                
                logger.info("Loading quantized model with level: {}", level);
                criteriaBuilder = quantizationService.getQuantizedModelCriteria(modelName, level);
                criteriaBuilder.optTranslator(new TextGenerationTranslator(1024));
            } else {
                // Use regular model (FP32)
                criteriaBuilder = Criteria.builder()
                        .setTypes(String.class, String.class)
                        .optModelPath(aiConfig.getLanguageModelPath())
                        .optEngine("PyTorch")
                        .optTranslator(new TextGenerationTranslator(1024));
            }
            
            // Build criteria and load model
            ZooModel<String, String> model = ModelZoo.loadModel(criteriaBuilder.build());
            
            // Store the loaded model
            loadedModels.put(modelKey, model);
            
            // Update status to loaded
            modelStatus.put(modelKey, ModelStatus.LOADED);
            
            logger.info("Successfully loaded language model: {}", modelName);
            return model;
        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            // Update status to failed
            modelStatus.put(modelKey, ModelStatus.FAILED);
            logger.error("Failed to load language model", e);
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
                modelDownloadService.downloadModelIfNeeded(modelName);
            }

            // Set criteria for model loading
            Criteria<String, String> criteria = Criteria.builder()
                                                        .setTypes(String.class, String.class)
                                                        .optModelPath(aiConfig.getEmbeddingsModelPath())
                                                        .optEngine("PyTorch")
                                                        .build();

            // Load the model
            ZooModel<String, String> model = ModelZoo.loadModel(criteria);
            
            // Store the loaded model
            loadedModels.put(modelKey, model);
            
            // Update status to loaded
            modelStatus.put(modelKey, ModelStatus.LOADED);
            
            logger.info("Successfully loaded embeddings model: {}", modelName);
            return model;
        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            // Update status to failed
            modelStatus.put(modelKey, ModelStatus.FAILED);
            logger.error("Failed to load embeddings model", e);
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
}