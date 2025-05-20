package com.projectoracle.service.basic;

import com.projectoracle.config.AIConfig;
import com.projectoracle.service.AIModelService;
import com.projectoracle.service.ModelDownloadService;
import com.projectoracle.service.ModelQuantizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Simple test for AIModelService with basic functionality
 */
@ExtendWith(MockitoExtension.class)
public class SimpleAIModelServiceTest {

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
        // Setup basic mock configuration
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
        // Test basic model status methods
        assertEquals(AIModelService.ModelStatus.NOT_LOADED, 
                    aiModelService.getModelStatus("language-gpt2-medium"));
    }

    @Test
    void testIsModelLoaded_whenNotLoaded() {
        // Test if model is loaded (should be false initially)
        assertFalse(aiModelService.isModelLoaded("language-gpt2-medium"));
    }

    @Test
    void testIsModelLoading_whenNotLoading() {
        // Test if model is currently loading (should be false initially)
        assertFalse(aiModelService.isModelLoading("language-gpt2-medium"));
    }
}