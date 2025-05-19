package com.projectoracle.rest;

import com.projectoracle.service.ModelQuantizationService;
import com.projectoracle.service.ModelQuantizationService.QuantizationLevel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * REST controller for model optimization and quantization.
 */
@RestController
@RequestMapping("/api/v1/models")
public class ModelController {

    private static final Logger logger = LoggerFactory.getLogger(ModelController.class);

    @Autowired
    private ModelQuantizationService modelQuantizationService;

    /**
     * Get available quantization levels
     */
    @GetMapping("/quantization/levels")
    public ResponseEntity<Map<String, String>> getQuantizationLevels() {
        logger.info("Getting available quantization levels");

        Map<String, String> levels = modelQuantizationService.getAvailableQuantizationLevels();
        return ResponseEntity.ok(levels);
    }

    /**
     * Estimate memory savings for model quantization
     */
    @GetMapping("/quantization/savings")
    public ResponseEntity<Map<String, Long>> estimateMemorySavings(@RequestParam String modelName) {
        logger.info("Estimating memory savings for model: {}", modelName);

        try {
            Map<String, Long> savings = modelQuantizationService.estimateMemorySavings(modelName);
            return ResponseEntity.ok(savings);
        } catch (IOException e) {
            logger.error("Error estimating memory savings", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Quantize a model
     */
    @PostMapping("/quantization/quantize")
    public ResponseEntity<QuantizationResult> quantizeModel(
            @RequestParam String modelName,
            @RequestParam String quantizationLevel) {

        logger.info("Quantizing model {} to {}", modelName, quantizationLevel);

        try {
            // Convert string to QuantizationLevel enum
            QuantizationLevel level = parseQuantizationLevel(quantizationLevel);
            if (level == null) {
                return ResponseEntity.badRequest().build();
            }

            // Perform quantization
            Path quantizedModelPath = modelQuantizationService.quantizeModel(modelName, level);

            // Create result
            QuantizationResult result = new QuantizationResult();
            result.setModelName(modelName);
            result.setQuantizationLevel(quantizationLevel);
            result.setFilePath(quantizedModelPath.toString());

            return ResponseEntity.ok(result);
        } catch (IOException e) {
            logger.error("Error quantizing model", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Parse quantization level from string
     */
    private QuantizationLevel parseQuantizationLevel(String levelString) {
        try {
            return QuantizationLevel.valueOf(levelString.toUpperCase());
        } catch (Exception e) {
            logger.error("Error parsing quantization level: {}", levelString, e);
            return null;
        }
    }

    /**
     * Result of model quantization
     */
    public static class QuantizationResult {
        private String modelName;
        private String quantizationLevel;
        private String filePath;

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getQuantizationLevel() {
            return quantizationLevel;
        }

        public void setQuantizationLevel(String quantizationLevel) {
            this.quantizationLevel = quantizationLevel;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
    }
}