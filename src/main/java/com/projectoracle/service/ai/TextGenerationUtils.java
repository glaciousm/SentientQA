package com.projectoracle.service.ai;

import ai.djl.ndarray.NDArray;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Compatible utility class for text generation post-processing
 * Uses only basic operations that work across all DJL versions
 */
@Slf4j
public class TextGenerationUtils {

    /**
     * Post-processing options for generated text
     */
    public static class PostProcessingConfig {
        public boolean removeIncompleteLastSentence = true;
        public boolean trimWhitespace = true;
        public boolean removeDuplicateLines = true;
        public boolean enforceMaxSentences = false;
        public int maxSentences = 5;
        public boolean filterProfanity = false;
        public Set<String> customStopWords = new HashSet<>();

        public PostProcessingConfig removeIncompleteLastSentence(boolean remove) {
            this.removeIncompleteLastSentence = remove;
            return this;
        }

        public PostProcessingConfig maxSentences(int max) {
            this.enforceMaxSentences = true;
            this.maxSentences = max;
            return this;
        }

        public PostProcessingConfig addStopWords(String... words) {
            this.customStopWords.addAll(Arrays.asList(words));
            return this;
        }
    }

    /**
     * Basic sampling techniques using array operations
     */
    public static class SamplingTechniques {

        /**
         * Basic multinomial sampling from probability array
         */
        public static long multinomialSample(float[] probs, Random random) {
            float randomValue = random.nextFloat();
            float cumulativeProb = 0.0f;

            for (int i = 0; i < probs.length; i++) {
                cumulativeProb += probs[i];
                if (randomValue <= cumulativeProb) {
                    return i;
                }
            }

            return probs.length - 1;
        }

        /**
         * Apply softmax to logits array
         */
        public static float[] applySoftmax(float[] logits) {
            // Find max for numerical stability
            float max = Float.NEGATIVE_INFINITY;
            for (float logit : logits) {
                if (logit > max) {
                    max = logit;
                }
            }

            // Compute exp(logit - max) and sum
            float[] probs = new float[logits.length];
            float sum = 0.0f;
            for (int i = 0; i < logits.length; i++) {
                probs[i] = (float) Math.exp(logits[i] - max);
                sum += probs[i];
            }

            // Normalize
            if (sum > 0) {
                for (int i = 0; i < probs.length; i++) {
                    probs[i] /= sum;
                }
            }

            return probs;
        }

        /**
         * Top-k sampling from logits
         */
        public static long topKSample(float[] logits, int k, Random random) {
            float[] probs = applySoftmax(logits);

            if (k <= 0 || k >= probs.length) {
                return multinomialSample(probs, random);
            }

            // Get top-k indices
            List<IndexValuePair> pairs = new ArrayList<>();
            for (int i = 0; i < probs.length; i++) {
                pairs.add(new IndexValuePair(i, probs[i]));
            }

            pairs.sort((a, b) -> Float.compare(b.value, a.value));

            float[] filteredProbs = new float[probs.length];
            for (int i = 0; i < k && i < pairs.size(); i++) {
                IndexValuePair pair = pairs.get(i);
                filteredProbs[pair.index] = pair.value;
            }

            // Renormalize
            float sum = 0.0f;
            for (float prob : filteredProbs) {
                sum += prob;
            }
            if (sum > 0) {
                for (int i = 0; i < filteredProbs.length; i++) {
                    filteredProbs[i] /= sum;
                }
            }

            return multinomialSample(filteredProbs, random);
        }

        /**
         * Top-p (nucleus) sampling from logits
         */
        public static long topPSample(float[] logits, float p, Random random) {
            float[] probs = applySoftmax(logits);

            if (p >= 1.0f) {
                return multinomialSample(probs, random);
            }

            // Get sorted indices
            List<IndexValuePair> pairs = new ArrayList<>();
            for (int i = 0; i < probs.length; i++) {
                pairs.add(new IndexValuePair(i, probs[i]));
            }

            pairs.sort((a, b) -> Float.compare(b.value, a.value));

            // Find cutoff
            float cumulative = 0.0f;
            int cutoff = pairs.size();
            for (int i = 0; i < pairs.size(); i++) {
                cumulative += pairs.get(i).value;
                if (cumulative > p) {
                    cutoff = i + 1;
                    break;
                }
            }

            float[] filteredProbs = new float[probs.length];
            for (int i = 0; i < cutoff; i++) {
                IndexValuePair pair = pairs.get(i);
                filteredProbs[pair.index] = pair.value;
            }

            // Renormalize
            float sum = 0.0f;
            for (float prob : filteredProbs) {
                sum += prob;
            }
            if (sum > 0) {
                for (int i = 0; i < filteredProbs.length; i++) {
                    filteredProbs[i] /= sum;
                }
            }

            return multinomialSample(filteredProbs, random);
        }

        /**
         * Combined top-k and top-p sampling from logits
         */
        public static long combinedSample(float[] logits, int topK, float topP, float temperature, Random random) {
            // Apply temperature
            float[] scaledLogits = new float[logits.length];
            if (temperature != 1.0f && temperature > 0) {
                for (int i = 0; i < logits.length; i++) {
                    scaledLogits[i] = logits[i] / temperature;
                }
            } else {
                scaledLogits = Arrays.copyOf(logits, logits.length);
            }

            float[] probs = applySoftmax(scaledLogits);

            // Apply top-k first
            if (topK > 0 && topK < probs.length) {
                List<IndexValuePair> pairs = new ArrayList<>();
                for (int i = 0; i < probs.length; i++) {
                    pairs.add(new IndexValuePair(i, probs[i]));
                }

                pairs.sort((a, b) -> Float.compare(b.value, a.value));

                float[] filteredProbs = new float[probs.length];
                for (int i = 0; i < topK && i < pairs.size(); i++) {
                    IndexValuePair pair = pairs.get(i);
                    filteredProbs[pair.index] = pair.value;
                }
                probs = filteredProbs;
            }

            // Apply top-p
            if (topP < 1.0f) {
                List<IndexValuePair> pairs = new ArrayList<>();
                for (int i = 0; i < probs.length; i++) {
                    if (probs[i] > 0) {
                        pairs.add(new IndexValuePair(i, probs[i]));
                    }
                }

                pairs.sort((a, b) -> Float.compare(b.value, a.value));

                float cumulative = 0.0f;
                int cutoff = pairs.size();
                for (int i = 0; i < pairs.size(); i++) {
                    cumulative += pairs.get(i).value;
                    if (cumulative > topP) {
                        cutoff = i + 1;
                        break;
                    }
                }

                float[] filteredProbs = new float[probs.length];
                for (int i = 0; i < cutoff; i++) {
                    IndexValuePair pair = pairs.get(i);
                    filteredProbs[pair.index] = pair.value;
                }
                probs = filteredProbs;
            }

            // Renormalize
            float sum = 0.0f;
            for (float prob : probs) {
                sum += prob;
            }

            if (sum > 0) {
                for (int i = 0; i < probs.length; i++) {
                    probs[i] /= sum;
                }
            } else {
                // Fallback to argmax
                int maxIdx = 0;
                float maxVal = Float.NEGATIVE_INFINITY;
                for (int i = 0; i < logits.length; i++) {
                    if (logits[i] > maxVal) {
                        maxVal = logits[i];
                        maxIdx = i;
                    }
                }
                return maxIdx;
            }

            return multinomialSample(probs, random);
        }

        /**
         * Helper class for index-value pairs
         */
        private static class IndexValuePair {
            public final int index;
            public final float value;

            public IndexValuePair(int index, float value) {
                this.index = index;
                this.value = value;
            }
        }
    }

    /**
     * Post-process generated text for better quality
     */
    public static String postProcess(String generatedText, String originalPrompt, PostProcessingConfig config) {
        String result = generatedText;

        // Remove the original prompt if it's at the beginning
        if (result.startsWith(originalPrompt)) {
            result = result.substring(originalPrompt.length());
        }

        if (config.trimWhitespace) {
            result = result.trim();
        }

        if (config.removeDuplicateLines) {
            result = removeDuplicateLines(result);
        }

        if (config.removeIncompleteLastSentence) {
            result = removeIncompleteLastSentence(result);
        }

        if (config.enforceMaxSentences) {
            result = limitSentences(result, config.maxSentences);
        }

        if (!config.customStopWords.isEmpty()) {
            result = removeStopWords(result, config.customStopWords);
        }

        if (config.filterProfanity) {
            result = filterProfanity(result);
        }

        return result.trim();
    }

    private static String removeDuplicateLines(String text) {
        String[] lines = text.split("\n");
        Set<String> seen = new LinkedHashSet<>();
        StringBuilder result = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && seen.add(trimmed)) {
                if (result.length() > 0) {
                    result.append("\n");
                }
                result.append(line);
            }
        }

        return result.toString();
    }

    private static String removeIncompleteLastSentence(String text) {
        String[] sentences = text.split("[.!?]+");
        if (sentences.length <= 1) {
            return text;
        }

        // Check if the last part ends with proper punctuation
        String lastPart = sentences[sentences.length - 1].trim();
        if (lastPart.isEmpty() || text.trim().matches(".*[.!?]\\s*$")) {
            return text; // Already complete
        }

        // Remove the incomplete last sentence
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < sentences.length - 1; i++) {
            result.append(sentences[i]);
            if (i < sentences.length - 2) {
                result.append(".");
            }
        }

        // Add final punctuation if needed
        String resultStr = result.toString().trim();
        if (!resultStr.isEmpty() && !resultStr.matches(".*[.!?]$")) {
            resultStr += ".";
        }

        return resultStr;
    }

    private static String limitSentences(String text, int maxSentences) {
        String[] sentences = text.split("(?<=[.!?])\\s+");
        if (sentences.length <= maxSentences) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < maxSentences; i++) {
            result.append(sentences[i]);
            if (i < maxSentences - 1) {
                result.append(" ");
            }
        }

        return result.toString();
    }

    private static String removeStopWords(String text, Set<String> stopWords) {
        for (String stopWord : stopWords) {
            // Use word boundaries to avoid partial matches
            text = text.replaceAll("\\b" + Pattern.quote(stopWord) + "\\b", "");
        }

        // Clean up extra whitespace
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String filterProfanity(String text) {
        // Basic profanity filter - in production, use a proper library
        String[] commonProfanity = {
                "damn", "hell", "crap", "shit", "fuck", "ass", "bitch"
        };

        String result = text;
        for (String word : commonProfanity) {
            result = result.replaceAll("(?i)\\b" + Pattern.quote(word) + "\\b", "***");
        }

        return result;
    }

    /**
     * Quality metrics for generated text
     */
    public static class QualityMetrics {
        public final double repetitionScore;
        public final double coherenceScore;
        public final double diversityScore;
        public final int sentenceCount;
        public final double avgSentenceLength;

        public QualityMetrics(double repetitionScore, double coherenceScore,
                double diversityScore, int sentenceCount, double avgSentenceLength) {
            this.repetitionScore = repetitionScore;
            this.coherenceScore = coherenceScore;
            this.diversityScore = diversityScore;
            this.sentenceCount = sentenceCount;
            this.avgSentenceLength = avgSentenceLength;
        }

        @Override
        public String toString() {
            return String.format(
                    "QualityMetrics{repetition=%.3f, coherence=%.3f, diversity=%.3f, sentences=%d, avgLength=%.1f}",
                    repetitionScore, coherenceScore, diversityScore, sentenceCount, avgSentenceLength
            );
        }
    }

    /**
     * Calculate quality metrics for generated text
     */
    public static QualityMetrics calculateQualityMetrics(String text, String prompt) {
        String[] words = text.toLowerCase().split("\\s+");
        String[] sentences = text.split("[.!?]+");

        // Repetition score (lower is better)
        double repetitionScore = calculateRepetitionScore(words);

        // Diversity score (higher is better)
        double diversityScore = calculateDiversityScore(words);

        // Basic coherence score (simplified)
        double coherenceScore = calculateCoherenceScore(text, prompt);

        // Sentence statistics
        int sentenceCount = sentences.length;
        double avgSentenceLength = sentenceCount > 0 ? (double) words.length / sentenceCount : 0;

        return new QualityMetrics(repetitionScore, coherenceScore, diversityScore, sentenceCount, avgSentenceLength);
    }

    private static double calculateRepetitionScore(String[] words) {
        if (words.length <= 1) return 0.0;

        Map<String, Integer> wordCounts = new HashMap<>();
        for (String word : words) {
            wordCounts.put(word, wordCounts.getOrDefault(word, 0) + 1);
        }

        int repeatedWords = 0;
        for (int count : wordCounts.values()) {
            if (count > 1) {
                repeatedWords += count - 1;
            }
        }

        return (double) repeatedWords / words.length;
    }

    private static double calculateDiversityScore(String[] words) {
        if (words.length <= 1) return 1.0;

        Set<String> uniqueWords = new HashSet<>(Arrays.asList(words));
        return (double) uniqueWords.size() / words.length;
    }

    private static double calculateCoherenceScore(String text, String prompt) {
        // Simple coherence metric based on shared vocabulary
        String[] promptWords = prompt.toLowerCase().split("\\s+");
        String[] textWords = text.toLowerCase().split("\\s+");

        Set<String> promptVocab = new HashSet<>(Arrays.asList(promptWords));
        Set<String> textVocab = new HashSet<>(Arrays.asList(textWords));

        Set<String> intersection = new HashSet<>(promptVocab);
        intersection.retainAll(textVocab);

        Set<String> union = new HashSet<>(promptVocab);
        union.addAll(textVocab);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * Apply repetition penalty to logits array
     */
    public static float[] applyRepetitionPenalty(float[] logits, List<Long> previousTokens, float penalty) {
        if (penalty == 1.0f || previousTokens.isEmpty()) {
            return Arrays.copyOf(logits, logits.length);
        }

        float[] penalized = Arrays.copyOf(logits, logits.length);

        // Get recently used tokens
        Set<Long> recentTokens = new HashSet<>();
        int lookback = Math.min(20, previousTokens.size());
        for (int i = previousTokens.size() - lookback; i < previousTokens.size(); i++) {
            if (i >= 0) {
                recentTokens.add(previousTokens.get(i));
            }
        }

        // Apply penalty
        for (Long token : recentTokens) {
            int tokenIdx = token.intValue();
            if (tokenIdx >= 0 && tokenIdx < penalized.length) {
                if (penalized[tokenIdx] > 0) {
                    penalized[tokenIdx] /= penalty;
                } else {
                    penalized[tokenIdx] *= penalty;
                }
            }
        }

        return penalized;
    }
}