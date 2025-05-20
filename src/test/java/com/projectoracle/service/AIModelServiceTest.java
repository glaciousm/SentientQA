package com.projectoracle.service;

import com.projectoracle.config.AIConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Basic test class for AIModelService
 */
@ExtendWith(MockitoExtension.class)
public class AIModelServiceTest {

    @Mock
    private AIConfig aiConfig;

    @Mock
    private ModelDownloadService modelDownloadService;

    @Mock
    private ModelQuantizationService quantizationService;

    @InjectMocks
    private AIModelService aiModelService;

    @BeforeEach
    void setUp() {
        // Default mock behavior setup
        when(aiConfig.getLanguageModelName()).thenReturn("gpt2-medium");
        when(aiConfig.getEmbeddingsModelName()).thenReturn("all-MiniLM-L6-v2");
        when(aiConfig.getBaseModelDir()).thenReturn("models");
        when(aiConfig.getLanguageModelPath()).thenReturn(Paths.get("models/gpt2-medium"));
        when(aiConfig.getEmbeddingsModelPath()).thenReturn(Paths.get("models/all-MiniLM-L6-v2"));
        when(aiConfig.isQuantizeLanguageModel()).thenReturn(true);
        when(aiConfig.getQuantizationLevel()).thenReturn("FP16");
    }

    @Test
    void testGetModelStatus_whenNotLoaded() {
        // Test
        AIModelService.ModelStatus status = aiModelService.getModelStatus("language-gpt2-medium");

        // Verify
        assertEquals(AIModelService.ModelStatus.NOT_LOADED, status);
    }

    @Test
    void testIsModelLoaded_whenNotLoaded() {
        // Test
        boolean isLoaded = aiModelService.isModelLoaded("language-gpt2-medium");

        // Verify
        assertFalse(isLoaded);
    }

    @Test
    void testIsModelLoading_whenNotLoading() {
        // Test
        boolean isLoading = aiModelService.isModelLoading("language-gpt2-medium");

        // Verify
        assertFalse(isLoading);
    }
}