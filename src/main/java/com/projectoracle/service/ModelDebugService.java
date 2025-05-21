package com.projectoracle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.projectoracle.config.AIConfig;

import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Translator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Helper service for diagnosing model loading issues
 */
@Service
public class ModelDebugService {

    private static final Logger logger = LoggerFactory.getLogger(ModelDebugService.class);

    @Autowired
    private AIConfig aiConfig;

    /**
     * Try to load a model with detailed debug information
     */
    public boolean testLoadModel() {
        try {
            logger.info("Testing model loading from: {}", aiConfig.getBaseModelDir());
            
            // Verify model directories
            Path modelDir = Paths.get(aiConfig.getBaseModelDir());
            if (!Files.exists(modelDir)) {
                logger.error("Model directory does not exist: {}", modelDir);
                return false;
            }
            
            Path modelPath = modelDir.resolve(aiConfig.getLanguageModelName());
            if (!Files.exists(modelPath)) {
                logger.error("Specific model directory does not exist: {}", modelPath);
                return false;
            }
            
            // Check for required files
            Path configFile = modelPath.resolve("config.json");
            Path tokenizerFile = modelPath.resolve("tokenizer.json");
            Path modelFile = modelPath.resolve("pytorch_model.bin");
            Path ptFile = modelPath.resolve(aiConfig.getLanguageModelName() + ".pt");
            
            logger.info("Checking model files:");
            checkFile(configFile);
            checkFile(tokenizerFile);
            checkFile(modelFile);
            checkFile(ptFile);
            
            // Try loading the model directly
            try {
                logger.info("Attempting to load model with DJL directly...");
                Criteria<String, String> criteria = Criteria.builder()
                        .setTypes(String.class, String.class)
                        .optModelPath(modelPath)
                        .optModelName(aiConfig.getLanguageModelName() + ".pt")
                        .optEngine("PyTorch")
                        .build();
                
                // Try loading model
                ZooModel<String, String> model = ModelZoo.loadModel(criteria);
                logger.info("Model loaded successfully!");
                model.close();
                return true;
            } catch (ModelNotFoundException | MalformedModelException e) {
                logger.error("Failed to load model: {}", e.getMessage());
                logger.error("Full error:", e);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error during model testing: {}", e.getMessage());
            logger.error("Full error:", e);
            return false;
        }
    }
    
    /**
     * Try to load a model with direct access to the file
     */
    public boolean testLoadModelDirectly() {
        try {
            logger.info("Testing direct model loading");
            
            Path modelPath = Paths.get(aiConfig.getBaseModelDir(), aiConfig.getLanguageModelName());
            Path ptFilePath = modelPath.resolve(aiConfig.getLanguageModelName() + ".pt");
            
            // Check file integrity at a lower level
            try {
                logger.info("Checking PT file at: {}", ptFilePath);
                
                // Verify the file exists and can be opened for reading
                if (!Files.exists(ptFilePath)) {
                    logger.error("PT file does not exist: {}", ptFilePath);
                    return false;
                }
                
                // Check if file is readable and try to read first bytes
                try (java.io.InputStream is = Files.newInputStream(ptFilePath)) {
                    byte[] buffer = new byte[8192]; // 8KB buffer
                    int read = is.read(buffer);
                    logger.info("Successfully read {} bytes from PT file", read);
                    
                    // Check if file appears to be a valid file format
                    if (read >= 4) {
                        // Convert first 4 bytes to a string to check file format
                        String magic = new String(buffer, 0, 4);
                        logger.info("File magic bytes: {}", magic);
                    }
                    
                    return read > 0; // Successfully read some data
                } catch (IOException e) {
                    logger.error("Error reading from PT file: {}", e.getMessage());
                    logger.error("Full error:", e);
                    return false;
                }
            } catch (Exception e) {
                logger.error("Error during file integrity check: {}", e.getMessage());
                logger.error("Full error:", e);
                return false;
            }
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check a model file and log details
     */
    private void checkFile(Path file) {
        if (!Files.exists(file)) {
            logger.error("File missing: {}", file);
            return;
        }
        
        try {
            long size = Files.size(file);
            logger.info("File exists: {} - Size: {} bytes", file, size);
            
            // Check if file is readable
            if (!Files.isReadable(file)) {
                logger.error("File is not readable: {}", file);
            }
            
            // For small files like config, show content
            if (size < 10000 && file.toString().endsWith(".json")) {
                String content = Files.readString(file);
                logger.info("File content preview: {}", content.substring(0, Math.min(200, content.length())));
            }
        } catch (IOException e) {
            logger.error("Error checking file {}: {}", file, e.getMessage());
        }
    }
}