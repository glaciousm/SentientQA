package com.projectoracle.service.ai;

import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import com.projectoracle.service.translator.HuggingFaceGPT2Translator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper class to load HuggingFace models like GPT-2 with proper configuration
 */
public class HuggingFaceModelLoader {
    private static final Logger logger = LoggerFactory.getLogger(HuggingFaceModelLoader.class);

    /**
     * Loads a HuggingFace GPT-2 model from the specified directory
     */
    public static ZooModel<String, String> loadGPT2Model(Path modelDir, String modelName, int maxTokens) throws ModelException, IOException {
        logger.info("Loading GPT-2 model from directory: {}", modelDir);
        
        // Ensure serving.properties exists with proper configuration
        ensureServingProperties(modelDir);
        
        // Create criteria for loading the model
        Criteria<String, String> criteria = Criteria.builder()
                .setTypes(String.class, String.class)
                .optModelPath(modelDir)
                .optEngine("PyTorch")
                .optTranslator(new HuggingFaceGPT2Translator(maxTokens))
                .optProgress(new ProgressBar())
                .build();
        
        logger.info("Loading model with criteria...");
        return ModelZoo.loadModel(criteria);
    }
    
    /**
     * Tests a loaded model with a simple prediction
     */
    public static String testModel(ZooModel<String, String> model, String input) throws ModelException {
        try (Predictor<String, String> predictor = model.newPredictor()) {
            return predictor.predict(input);
        } catch (TranslateException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Ensures serving.properties exists with correct configuration for GPT-2
     */
    private static void ensureServingProperties(Path modelDir) throws IOException {
        Path servingPropsPath = modelDir.resolve("serving.properties");
        if (!Files.exists(servingPropsPath)) {
            logger.info("Creating serving.properties for GPT-2 model");
            String content = """
                # DJL serving configuration for GPT-2
                engine=PyTorch
                option.model_name=gpt2
                option.model_type=gpt2
                option.disable_jit_script=true
                option.dtype=float32
                option.rolling_batch=false
                option.max_memory_per_model=4096
                option.load_on_devices=*
                """;
            Files.writeString(servingPropsPath, content);
        }
    }
}