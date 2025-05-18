package com.projectoracle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;

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
        // Initialize model URL map
        modelUrlMap.put("gpt2-medium", "https://huggingface.co/gpt2-medium/resolve/main/pytorch_model.bin");
        modelUrlMap.put("all-MiniLM-L6-v2", "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/pytorch_model.bin");

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
        Path modelPath = aiConfig.getModelPath(modelName).resolve("pytorch_model.bin");
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
        Path modelFile = modelDir.resolve("pytorch_model.bin");

        if (Files.exists(modelFile)) {
            logger.info("Model {} already exists at {}", modelName, modelFile);
            return modelDir;
        }

        logger.info("Model {} not found, downloading...", modelName);
        Files.createDirectories(modelDir);

        String modelUrl = modelUrlMap.get(modelName);
        if (modelUrl == null) {
            throw new IOException("Unknown model: " + modelName);
        }

        downloadFile(modelUrl, modelFile, modelName);

        // Download config file for the model
        String configUrl = modelUrl.replace("pytorch_model.bin", "config.json");
        downloadFile(configUrl, modelDir.resolve("config.json"), modelName + " config");

        // Download tokenizer files
        String tokenizerUrl = modelUrl.replace("pytorch_model.bin", "tokenizer.json");
        downloadFile(tokenizerUrl, modelDir.resolve("tokenizer.json"), modelName + " tokenizer");

        logger.info("Model {} downloaded successfully to {}", modelName, modelDir);
        return modelDir;
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

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");

        int fileSize = connection.getContentLength();
        int downloadedSize = 0;
        int lastProgressPercent = 0;

        try (InputStream in = connection.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;

            try (var outputStream = Files.newOutputStream(dest)) {
                while ((bytesRead = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    downloadedSize += bytesRead;

                    if (fileSize > 0) {
                        int progressPercent = (int) ((downloadedSize * 100L) / fileSize);
                        if (progressPercent > lastProgressPercent) {
                            logger.info("Download progress for {}: {}%", description, progressPercent);
                            lastProgressPercent = progressPercent;
                        }
                    }
                }
            }
        }

        logger.info("Completed downloading {}", description);
    }
}