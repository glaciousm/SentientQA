package com.projectoracle.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for AI models and resources used by Sentinel
 * This centralizes all AI model paths, parameters, and configurations
 */
@Configuration
@PropertySource("classpath:ai-config.properties")
@Data
public class AIConfig {

    // Base directory for all AI models and resources
    @Value("${ai.models.base-dir:models}")
    private String baseModelDir;

    // Language model configurations
    @Value("${ai.model.language.name:gpt2-medium}")
    private String languageModelName;

    @Value("${ai.model.language.quantize:true}")
    private boolean quantizeLanguageModel;
    
    @Value("${ai.model.language.quantization-level:FP16}")
    private String quantizationLevel;

    @Value("${ai.model.embeddings.name:all-MiniLM-L6-v2}")
    private String embeddingsModelName;

    // System resource configurations
    @Value("${ai.system.memory-limit-mb:4096}")
    private int memoryLimitMb;

    @Value("${ai.system.use-gpu:false}")
    private boolean useGpu;

    @Value("${ai.system.gpu-memory-limit-mb:2048}")
    private int gpuMemoryLimitMb;

    // Feature toggles for resource optimization
    @Value("${ai.features.test-generation.enabled:true}")
    private boolean testGenerationEnabled;

    @Value("${ai.features.self-healing.enabled:true}")
    private boolean selfHealingEnabled;

    @Value("${ai.features.code-analysis.enabled:true}")
    private boolean codeAnalysisEnabled;

    @Value("${ai.performance.batch-size:4}")
    private int batchSize;

    @Value("${ai.performance.cache-embeddings:true}")
    private boolean cacheEmbeddings;

    @Value("${ai.performance.cache-dir:cache}")
    private String cacheDir;
    
    @Value("${ai.system.fallback-to-rule-based:true}")
    private boolean fallbackToRuleBased;
    
    @Value("${ai.system.max-load-retries:3}")
    private int maxLoadRetries;
    
    @Value("${ai.system.load-timeout-ms:120000}")
    private long loadTimeoutMs;
    
    @Value("${ai.system.operation-timeout-ms:60000}")
    private long operationTimeoutMs;

    /**
     * Get the absolute path to a specific model file
     */
    public Path getModelPath(String modelName) {
        return Paths.get(baseModelDir, modelName);
    }

    /**
     * Get the language model path
     */
    public Path getLanguageModelPath() {
        return getModelPath(languageModelName);
    }

    /**
     * Get the embeddings model path
     */
    public Path getEmbeddingsModelPath() {
        return getModelPath(embeddingsModelName);
    }

    /**
     * Get the cache directory path
     */
    public Path getCacheDirPath() {
        return Paths.get(cacheDir);
    }

    // Getters

    public String getBaseModelDir() {
        return baseModelDir;
    }

    public String getLanguageModelName() {
        return languageModelName;
    }

    public boolean isQuantizeLanguageModel() {
        return quantizeLanguageModel;
    }
    
    public String getQuantizationLevel() {
        return quantizationLevel;
    }

    public String getEmbeddingsModelName() {
        return embeddingsModelName;
    }

    public int getMemoryLimitMb() {
        return memoryLimitMb;
    }

    public boolean isUseGpu() {
        return useGpu;
    }

    public int getGpuMemoryLimitMb() {
        return gpuMemoryLimitMb;
    }

    public boolean isTestGenerationEnabled() {
        return testGenerationEnabled;
    }

    public boolean isSelfHealingEnabled() {
        return selfHealingEnabled;
    }

    public boolean isCodeAnalysisEnabled() {
        return codeAnalysisEnabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public boolean isCacheEmbeddings() {
        return cacheEmbeddings;
    }

    public String getCacheDir() {
        return cacheDir;
    }
    
    public boolean isFallbackToRuleBased() {
        return fallbackToRuleBased;
    }
    
    public int getMaxLoadRetries() {
        return maxLoadRetries;
    }
    
    public long getLoadTimeoutMs() {
        return loadTimeoutMs;
    }
    
    public long getOperationTimeoutMs() {
        return operationTimeoutMs;
    }
    
    // Setters for testing and configuration adjustments
    
    public void setQuantizeLanguageModel(boolean quantizeLanguageModel) {
        this.quantizeLanguageModel = quantizeLanguageModel;
    }
    
    public void setQuantizationLevel(String quantizationLevel) {
        this.quantizationLevel = quantizationLevel;
    }
    
    public void setFallbackToRuleBased(boolean fallbackToRuleBased) {
        this.fallbackToRuleBased = fallbackToRuleBased;
    }
    
    public void setMaxLoadRetries(int maxLoadRetries) {
        this.maxLoadRetries = maxLoadRetries;
    }
    
    public void setLoadTimeoutMs(long loadTimeoutMs) {
        this.loadTimeoutMs = loadTimeoutMs;
    }
    
    public void setOperationTimeoutMs(long operationTimeoutMs) {
        this.operationTimeoutMs = operationTimeoutMs;
    }
}