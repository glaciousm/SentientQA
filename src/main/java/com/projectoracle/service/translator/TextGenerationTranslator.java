package com.projectoracle.service.translator;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

/**
 * Translator for text generation models.
 * Handles the conversion between Java strings and model inputs/outputs.
 */
public class TextGenerationTranslator implements Translator<String, String> {

    private final int maxTokens;
    private final float temperature;
    private final float topP;
    private final int topK;

    /**
     * Creates a new TextGenerationTranslator with specified parameters.
     *
     * @param maxTokens maximum number of tokens to generate
     */
    public TextGenerationTranslator(int maxTokens) {
        this(maxTokens, 0.7f, 0.9f, 40);
    }

    /**
     * Creates a new TextGenerationTranslator with specified parameters.
     *
     * @param maxTokens   maximum number of tokens to generate
     * @param temperature the sampling temperature (higher = more creative/random)
     * @param topP        nucleus sampling parameter (cumulative probability threshold)
     * @param topK        top-k sampling parameter (limits vocabulary to top K tokens)
     */
    public TextGenerationTranslator(int maxTokens, float temperature, float topP, int topK) {
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
    }

    @Override
    public String processInput(TranslatorContext ctx, String input) {
        // Create DJL Input object with the input text
        Input djlInput = new Input();
        djlInput.add("text", input);

        // Add generation parameters
        djlInput.add("max_tokens", String.valueOf(maxTokens));
        djlInput.add("temperature", String.valueOf(temperature));
        djlInput.add("top_p", String.valueOf(topP));
        djlInput.add("top_k", String.valueOf(topK));

        ctx.setInput(djlInput);
        return input;
    }

    @Override
    public String processOutput(TranslatorContext ctx, Output output) {
        // Extract the generated text from the model output
        return output.getData().getAsString("text");
    }
}