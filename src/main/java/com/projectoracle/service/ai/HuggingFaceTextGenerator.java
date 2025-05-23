package com.projectoracle.service.ai;

import ai.djl.Application;
import ai.djl.ModelException;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.modality.nlp.generate.SearchConfig;
import ai.djl.modality.nlp.generate.TextGenerator;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Specialized text generator for HuggingFace GPT-2 models.
 * This class provides a more robust approach to loading and using GPT-2 models.
 */
@Slf4j
public class HuggingFaceTextGenerator {
    
    private final ZooModel<NDList, NDList> model;
    private final HuggingFaceTokenizer tokenizer;
    private final TextGenerator generator;
    
    /**
     * Load a GPT-2 model from HuggingFace format
     * 
     * @param modelPath Path to the model directory
     * @param modelName Name of the model
     * @return A HuggingFaceTextGenerator instance
     */
    public static HuggingFaceTextGenerator load(Path modelPath, String modelName) 
            throws ModelException, IOException {
        
        log.info("Loading HuggingFace GPT-2 model from: {}", modelPath);
        
        // Initialize tokenizer
        Path tokenizerPath = modelPath.resolve("tokenizer.json");
        if (!tokenizerPath.toFile().exists()) {
            throw new IOException("Tokenizer file not found: " + tokenizerPath);
        }
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
        
        // Load the model using NDList input/output
        Criteria<NDList, NDList> criteria = Criteria.builder()
                .setTypes(NDList.class, NDList.class)
                .optApplication(Application.NLP.TEXT_GENERATION)
                .optModelPath(modelPath)
                .optEngine("PyTorch")
                .optOption("mapLocation", "true")
                .optProgress(new ProgressBar())
                .build();
        
        ZooModel<NDList, NDList> model = criteria.loadModel();
        
        // Create search config for generation
        SearchConfig config = SearchConfig.builder()
                .setMaxSeqLength(1024)
                .setBatchSize(1)
                .setPadTokenId(tokenizer.getTokenId("<pad>"))
                .setEosTokenId(tokenizer.getTokenId("<|endoftext|>"))
                .build();
        
        // Create text generator
        TextGenerator generator = new TextGenerator(model.getBlock(), config, NDManager.newBaseManager());
        
        return new HuggingFaceTextGenerator(model, tokenizer, generator);
    }
    
    private HuggingFaceTextGenerator(ZooModel<NDList, NDList> model, 
                                    HuggingFaceTokenizer tokenizer,
                                    TextGenerator generator) {
        this.model = model;
        this.tokenizer = tokenizer;
        this.generator = generator;
    }
    
    /**
     * Generate text from a prompt
     * 
     * @param prompt The input prompt
     * @param maxLength Maximum length of generated text
     * @param temperature Temperature for sampling
     * @param topK Top-K sampling parameter
     * @param topP Top-P (nucleus) sampling parameter
     * @return Generated text
     */
    public String generate(String prompt, int maxLength, float temperature, int topK, float topP) 
            throws TranslateException {
        
        // Update search config
        SearchConfig config = SearchConfig.builder()
                .setMaxSeqLength(maxLength)
                .setBatchSize(1)
                .setPadTokenId(tokenizer.getTokenId("<pad>"))
                .setEosTokenId(tokenizer.getTokenId("<|endoftext|>"))
                .setTemperature(temperature)
                .setTopK(topK)
                .setTopP(topP)
                .build();
        
        // Encode input
        long[] inputIds = tokenizer.encode(prompt).getIds();
        
        try (NDManager manager = NDManager.newBaseManager()) {
            NDArray inputArray = manager.create(inputIds).reshape(1, -1);
            NDList input = new NDList(inputArray);
            
            // Generate output
            NDList output = generator.generate(input);
            
            // Decode output
            long[] outputIds = output.get(0).toLongArray();
            return tokenizer.decode(outputIds);
        }
    }
    
    /**
     * Simple generate method with default parameters
     */
    public String generate(String prompt, int maxLength) throws TranslateException {
        return generate(prompt, maxLength, 1.0f, 50, 0.95f);
    }
    
    /**
     * Close the model and free resources
     */
    public void close() {
        if (generator != null) {
            generator.getManager().close();
        }
        if (model != null) {
            model.close();
        }
    }
    
    /**
     * Create a predictor that can be used with the existing AIModelService interface
     */
    public Predictor<String, String> createPredictor(int maxLength) {
        return new Predictor<String, String>() {
            @Override
            public String predict(String input) throws TranslateException {
                return generate(input, maxLength);
            }
            
            @Override
            public void close() {
                // Don't close the underlying model when predictor is closed
            }
        };
    }
}