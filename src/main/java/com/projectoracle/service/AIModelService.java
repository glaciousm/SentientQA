package com.projectoracle.service;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;

import com.projectoracle.config.AIConfig;
import com.projectoracle.service.translator.TextGenerationTranslator;

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

    private final ConcurrentHashMap<String, ZooModel<String, String>> loadedModels = new ConcurrentHashMap<>();

    /**
     * Initialize the AI model service and prepare directories
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

            logger.info("AI Model Service initialized");
            logger.info("Models directory: {}", aiConfig.getBaseModelDir());
            logger.info("Using GPU: {}", aiConfig.isUseGpu());
            logger.info("Memory limit: {} MB", aiConfig.getMemoryLimitMb());
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
     * Load the language model (with lazy loading pattern)
     */
    public ZooModel<String, String> loadLanguageModel() throws ModelNotFoundException, MalformedModelException, IOException {
        String modelKey = "language-" + aiConfig.getLanguageModelName();

        return loadedModels.computeIfAbsent(modelKey, k -> {
            try {
                logger.info("Loading language model: {}", aiConfig.getLanguageModelName());

                // Set criteria for model loading
                Criteria<String, String> criteria = Criteria.builder()
                                                            .setTypes(String.class, String.class)
                                                            .optModelPath(aiConfig.getLanguageModelPath())
                                                            .optEngine("PyTorch") // Using PyTorch engine
                                                            .optProgress(new ProgressBar())
                                                            .optTranslator(new TextGenerationTranslator(1024))
                                                            .build();

                return ModelZoo.loadModel(criteria);
            } catch (ModelNotFoundException | MalformedModelException | IOException e) {
                logger.error("Failed to load language model", e);
                throw new RuntimeException("Failed to load language model", e);
            }
        });
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