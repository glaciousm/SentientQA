package com.projectoracle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.projectoracle.config.AIConfig;

/**
 * Service for managing AI model downloading and management
 */
@Service
public class ModelDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(ModelDownloadService.class);

    @Autowired
    private AIConfig aiConfig;

    private final ConcurrentHashMap<String, String> modelUrlMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Initialize model URL map based on configured format
        String modelFormat = aiConfig.getModelFormat();
        
        // Set URLs based on the configured model format
        modelUrlMap.put("gpt2-medium", "https://huggingface.co/gpt2-medium/resolve/main/" + modelFormat);
        modelUrlMap.put("all-MiniLM-L6-v2", "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/" + modelFormat);

        try {
            // Create model directory if it doesn't exist
            Path modelDir = Path.of(aiConfig.getBaseModelDir());
            if (!Files.exists(modelDir)) {
                logger.info("Creating model directory: {}", modelDir);
                Files.createDirectories(modelDir);
            }

            logger.info("Model Download Service initialized");
        } catch (IOException e) {
            logger.error("Failed to initialize model download service", e);
            throw new RuntimeException("Failed to initialize model download service", e);
        }
    }

    /**
     * Check if a model is present locally
     *
     * @param modelName the name of the model to check
     * @return true if the model exists locally
     */
    public boolean isModelPresent(String modelName) {
        Path modelPath = aiConfig.getModelPath(modelName).resolve(aiConfig.getModelFormat());
        return Files.exists(modelPath);
    }

    /**
     * Download a model from HuggingFace if it's not already present
     *
     * @param modelName the name of the model to download
     * @return the path to the downloaded model
     * @throws IOException if the download fails
     */
    public Path downloadModelIfNeeded(String modelName) throws IOException {
        Path modelDir = aiConfig.getModelPath(modelName);
        Path modelFile = modelDir.resolve(aiConfig.getModelFormat());
        Path tempDir = null;

        if (Files.exists(modelFile) && Files.size(modelFile) > 1000000) { // Must be at least 1MB
            logger.info("Model {} already exists at {} with size {}", modelName, modelFile, Files.size(modelFile));
            return modelDir;
        } else if (Files.exists(modelFile)) {
            logger.warn("Model {} exists at {} but has suspiciously small size: {} bytes", 
                    modelName, modelFile, Files.size(modelFile));
            
            // Backup and delete the suspicious model file
            Path backupFile = modelDir.resolve("pytorch_model.bin.backup");
            Files.move(modelFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Backed up suspicious model file to {}", backupFile);
        }

        logger.info("Model {} not found or invalid, downloading...", modelName);
        
        // Create temp directory for download to ensure atomicity
        tempDir = Files.createTempDirectory("model-download-" + modelName);
        
        try {
            Files.createDirectories(modelDir);

            String modelUrl = modelUrlMap.get(modelName);
            if (modelUrl == null) {
                throw new IOException("Unknown model: " + modelName);
            }

            // Download to temp directory first
            Path tempModelFile = tempDir.resolve(aiConfig.getModelFormat());
            downloadFile(modelUrl, tempModelFile, modelName);

            // Validate downloaded file - check size is reasonable
            long fileSize = Files.size(tempModelFile);
            if (fileSize < 1000000) { // Less than 1MB is suspicious for these models
                throw new IOException(String.format(
                    "Downloaded model file size is suspiciously small: %d bytes. Min expected: 1,000,000 bytes", fileSize));
            }

            // Download config file for the model
            String configUrl = modelUrl.replace("pytorch_model.bin", "config.json");
            Path tempConfigFile = tempDir.resolve("config.json");
            downloadFile(configUrl, tempConfigFile, modelName + " config");

            // Download tokenizer files
            String tokenizerUrl = modelUrl.replace("pytorch_model.bin", "tokenizer.json");
            Path tempTokenizerFile = tempDir.resolve("tokenizer.json");
            downloadFile(tokenizerUrl, tempTokenizerFile, modelName + " tokenizer");

            // Move files from temp to final location
            Files.move(tempModelFile, modelFile, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempConfigFile, modelDir.resolve("config.json"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempTokenizerFile, modelDir.resolve("tokenizer.json"), StandardCopyOption.REPLACE_EXISTING);

            logger.info("Model {} downloaded successfully to {}", modelName, modelDir);
            return modelDir;
        } catch (IOException e) {
            logger.error("Failed to download model {}: {}", modelName, e.getMessage());
            
            // Cleanup any partial downloads in the model directory
            cleanModelDirectory(modelDir);
            
            throw new IOException("Failed to download model " + modelName + ": " + e.getMessage(), e);
        } finally {
            // Always clean up the temp directory
            if (tempDir != null) {
                try {
                    FileSystemUtils.deleteRecursively(tempDir);
                } catch (IOException e) {
                    logger.warn("Failed to delete temporary directory {}: {}", tempDir, e.getMessage());
                }
            }
        }
    }
    
    /**
     * Clean up a model directory in case of failed download
     */
    private void cleanModelDirectory(Path modelDir) {
        try {
            // Delete incomplete files but keep the directory
            Files.list(modelDir).forEach(file -> {
                try {
                    if (!Files.isDirectory(file)) {
                        Files.delete(file);
                        logger.info("Deleted incomplete model file: {}", file);
                    }
                } catch (IOException e) {
                    logger.warn("Failed to delete file {}: {}", file, e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.warn("Failed to clean up model directory {}: {}", modelDir, e.getMessage());
        }
    }

    /**
     * Download a file from a URL with progress reporting
     *
     * @param urlString the URL to download from
     * @param dest the destination path
     * @param description description for the progress
     * @throws IOException if the download fails
     */
    private void downloadFile(String urlString, Path dest, String description) throws IOException {
        logger.info("Downloading {} from {} to {}", description, urlString, dest);
        
        int maxRetries = 3;
        int retryCount = 0;
        int retryDelayMs = 5000; // 5 seconds initial delay
        
        while (retryCount <= maxRetries) {
            try {
                doDownload(urlString, dest, description);
                
                // Verify the download size if it's the main model file
                if (dest.getFileName().toString().equals("pytorch_model.bin")) {
                    long fileSize = Files.size(dest);
                    if (fileSize < 1000000) { // Less than 1MB is suspicious for these models
                        logger.warn("Downloaded model file size is suspiciously small: {} bytes", fileSize);
                        if (retryCount < maxRetries) {
                            logger.info("Will retry download (attempt {} of {})", retryCount + 1, maxRetries);
                            Files.delete(dest);
                            
                            // Exponential backoff for retry delay
                            retryDelayMs *= 2;
                            Thread.sleep(retryDelayMs);
                            retryCount++;
                            continue;
                        } else {
                            throw new IOException(String.format(
                                "Downloaded model file size is too small after %d retries: %d bytes", 
                                maxRetries, fileSize));
                        }
                    }
                }
                
                // If we reach here, download was successful
                logger.info("Completed downloading {}", description);
                return;
                
            } catch (IOException e) {
                logger.warn("Download failed: {}", e.getMessage());
                
                if (retryCount < maxRetries) {
                    logger.info("Will retry download (attempt {} of {})", retryCount + 1, maxRetries);
                    
                    // Exponential backoff for retry delay
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Download interrupted during retry wait", ie);
                    }
                    
                    retryDelayMs *= 2;
                    retryCount++;
                } else {
                    logger.error("Failed to download after {} retries", maxRetries);
                    throw new IOException("Failed to download after " + maxRetries + " retries: " + e.getMessage(), e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Download interrupted during retry wait", e);
            }
        }
    }
    
    /**
     * Perform the actual download with timeout and progress reporting
     */
    private void doDownload(String urlString, Path dest, String description) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setConnectTimeout(30000); // 30 seconds connection timeout
        connection.setReadTimeout(60000);    // 60 seconds read timeout

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to download " + description + ": HTTP error code " + responseCode);
        }

        int fileSize = connection.getContentLength();
        int downloadedSize = 0;
        int lastProgressPercent = 0;
        long startTime = System.currentTimeMillis();
        long lastLogTime = startTime;

        try (InputStream in = connection.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;

            try (var outputStream = Files.newOutputStream(dest)) {
                while ((bytesRead = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    downloadedSize += bytesRead;
                    
                    long currentTime = System.currentTimeMillis();
                    
                    // Only log progress every 5 seconds or when percent changes
                    if (currentTime - lastLogTime > 5000 || 
                        (fileSize > 0 && (int) ((downloadedSize * 100L) / fileSize) > lastProgressPercent)) {
                        
                        if (fileSize > 0) {
                            int progressPercent = (int) ((downloadedSize * 100L) / fileSize);
                            long elapsedSeconds = (currentTime - startTime) / 1000;
                            long bytesPerSecond = elapsedSeconds > 0 ? downloadedSize / elapsedSeconds : 0;
                            
                            logger.info("Download progress for {}: {}% ({} KB/s)", 
                                      description, 
                                      progressPercent,
                                      bytesPerSecond / 1024);
                                      
                            lastProgressPercent = progressPercent;
                        } else {
                            // If file size is unknown, just log bytes downloaded
                            logger.info("Downloaded {} bytes of {} so far", downloadedSize, description);
                        }
                        
                        lastLogTime = currentTime;
                    }
                }
            }
        }
    }
}