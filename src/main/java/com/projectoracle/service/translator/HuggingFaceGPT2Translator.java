package com.projectoracle.service.translator;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * HuggingFace-specific translator for GPT-2 models.
 * Uses the HuggingFace tokenizer for proper text encoding/decoding.
 */
@Slf4j
public class HuggingFaceGPT2Translator implements Translator<String, String> {
    
    private final int maxLength;
    private final float temperature;
    private final int topK;
    private final float topP;
    private HuggingFaceTokenizer tokenizer;
    
    public HuggingFaceGPT2Translator(int maxLength) {
        this(maxLength, 1.0f, 50, 0.95f);
    }
    
    public HuggingFaceGPT2Translator(int maxLength, float temperature, int topK, float topP) {
        this.maxLength = maxLength;
        this.temperature = temperature;
        this.topK = topK;
        this.topP = topP;
    }
    
    @Override
    public void prepare(TranslatorContext ctx) throws Exception {
        // Initialize tokenizer from model directory
        Path modelPath = ctx.getModel().getModelPath();
        Path tokenizerPath = modelPath.resolve("tokenizer.json");
        
        if (!tokenizerPath.toFile().exists()) {
            throw new IllegalStateException("Tokenizer file not found at: " + tokenizerPath);
        }
        
        log.info("Initializing HuggingFace tokenizer from: {}", tokenizerPath);
        this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
    }
    
    @Override
    public NDList processInput(TranslatorContext ctx, String input) throws Exception {
        NDManager manager = ctx.getNDManager();
        
        // Encode the input text
        Encoding encoding = tokenizer.encode(input);
        long[] inputIds = encoding.getIds();
        
        log.debug("Input text: '{}' encoded to {} tokens", input, inputIds.length);
        
        // Convert to NDArray
        NDArray inputArray = manager.create(inputIds);
        
        // For GPT-2, we need to reshape to [batch_size, sequence_length]
        inputArray = inputArray.reshape(1, inputIds.length);
        
        // Create attention mask (all 1s for real tokens)
        NDArray attentionMask = manager.ones(inputArray.getShape());
        
        // Store context for generation parameters
        ctx.setAttachment("input_ids", inputIds);
        ctx.setAttachment("max_length", maxLength);
        ctx.setAttachment("temperature", temperature);
        ctx.setAttachment("top_k", topK);
        ctx.setAttachment("top_p", topP);
        
        // Return input_ids and attention_mask
        return new NDList(inputArray, attentionMask);
    }
    
    @Override
    public String processOutput(TranslatorContext ctx, NDList output) throws Exception {
        if (output.isEmpty()) {
            log.warn("Empty output from model");
            return "";
        }
        
        // Get the output logits/tokens
        NDArray outputArray = output.get(0);
        
        // Handle different output formats
        long[] outputIds;
        if (outputArray.getShape().dimension() == 3) {
            // Output is logits [batch_size, sequence_length, vocab_size]
            // Take argmax to get token ids
            outputArray = outputArray.argMax(-1);
            outputIds = outputArray.toLongArray();
        } else if (outputArray.getShape().dimension() == 2) {
            // Output is already token ids [batch_size, sequence_length]
            outputIds = outputArray.toLongArray();
        } else {
            // Flatten and treat as token ids
            outputIds = outputArray.flatten().toLongArray();
        }
        
        // Decode the output tokens
        String decoded = tokenizer.decode(outputIds);
        
        // Get the original input for context
        long[] inputIds = (long[]) ctx.getAttachment("input_ids");
        String inputText = tokenizer.decode(inputIds);
        
        // Remove the input prompt from the output if it's included
        if (decoded.startsWith(inputText)) {
            decoded = decoded.substring(inputText.length()).trim();
        }
        
        log.debug("Generated text: '{}'", decoded);
        
        return decoded;
    }
    
    @Override
    public ai.djl.translate.Batchifier getBatchifier() {
        // No batching support for now
        return null;
    }
}