package com.projectoracle.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import lombok.Data;

/**
 * Element fingerprint for reliable element identification during test execution.
 * Used for test healing when elements change but maintain similar characteristics.
 */
@Data
public class ElementFingerprint {

    private String id;                          // Unique identifier for this fingerprint
    private String elementId;                   // Element's ID attribute if available
    private String elementName;                 // Element's name attribute if available
    private String elementType;                 // Element's tag name
    private String elementText;                 // Element's text content
    private Map<String, String> attributes;     // Element's attributes
    private String xpath;                       // XPath to the element
    private String cssSelector;                 // CSS selector for the element
    private Map<String, Double> properties;     // Numeric properties (position, size, etc.)
    private String parentFingerprint;           // Simplified fingerprint of parent element
    private String visualSignature;             // Hash of element's visual appearance
    private double confidenceThreshold = 0.8;   // Threshold for match confidence

    /**
     * Default constructor
     */
    public ElementFingerprint() {
        this.id = UUID.randomUUID().toString();
        attributes = new HashMap<>();
        properties = new HashMap<>();
    }

    /**
     * Add an attribute to the fingerprint
     *
     * @param name attribute name
     * @param value attribute value
     */
    public void addAttribute(String name, String value) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put(name, value);
    }

    /**
     * Add a numeric property to the fingerprint
     *
     * @param name property name
     * @param value property value
     */
    public void addProperty(String name, double value) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(name, value);
    }

    /**
     * Calculate match confidence with another fingerprint
     *
     * @param other the fingerprint to compare with
     * @return confidence score between 0.0 and 1.0
     */
    public double calculateMatchConfidence(ElementFingerprint other) {
        if (other == null) {
            return 0.0;
        }

        double score = 0.0;
        int factors = 0;

        // Compare element type (high importance)
        if (Objects.equals(elementType, other.elementType)) {
            score += 0.2;
        }
        factors++;

        // Compare ID and name (high importance)
        if (elementId != null && elementId.equals(other.elementId)) {
            score += 0.2;
        }
        factors++;

        if (elementName != null && elementName.equals(other.elementName)) {
            score += 0.15;
        }
        factors++;

        // Compare text content (medium importance)
        if (elementText != null && other.elementText != null) {
            double textSimilarity = calculateTextSimilarity(elementText, other.elementText);
            score += 0.15 * textSimilarity;
        }
        factors++;

        // Compare attributes (medium importance)
        if (attributes != null && other.attributes != null) {
            double attrSimilarity = calculateAttributeSimilarity(attributes, other.attributes);
            score += 0.15 * attrSimilarity;
        }
        factors++;

        // Compare properties (low importance)
        if (properties != null && other.properties != null) {
            double propSimilarity = calculatePropertySimilarity(properties, other.properties);
            score += 0.1 * propSimilarity;
        }
        factors++;

        // Compare parent (low importance)
        if (parentFingerprint != null && parentFingerprint.equals(other.parentFingerprint)) {
            score += 0.05;
        }
        factors++;

        // Visual signature comparison (if available)
        if (visualSignature != null && visualSignature.equals(other.visualSignature)) {
            score += 0.15;
        } else if (visualSignature != null && other.visualSignature != null) {
            // Calculate visual similarity (simplified)
            double visualSimilarity = calculateVisualSimilarity(visualSignature, other.visualSignature);
            score += 0.15 * visualSimilarity;
        }
        factors++;

        // Normalize score based on factors considered
        return score / factors;
    }

    /**
     * Check if this fingerprint matches another within the confidence threshold
     *
     * @param other the fingerprint to compare with
     * @return true if the match confidence exceeds the threshold
     */
    public boolean matches(ElementFingerprint other) {
        return calculateMatchConfidence(other) >= confidenceThreshold;
    }

    /**
     * Calculate text similarity using Levenshtein distance
     *
     * @param text1 first text
     * @param text2 second text
     * @return similarity score between 0.0 and 1.0
     */
    private double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }

        if (text1.equals(text2)) {
            return 1.0;
        }

        // Trim and normalize whitespace
        text1 = text1.trim().replaceAll("\\s+", " ");
        text2 = text2.trim().replaceAll("\\s+", " ");

        // If one is a substring of the other, it's a good match
        if (text1.contains(text2) || text2.contains(text1)) {
            return 0.8;
        }

        // Calculate Levenshtein distance
        int distance = levenshteinDistance(text1, text2);
        int maxLength = Math.max(text1.length(), text2.length());

        if (maxLength == 0) {
            return 1.0;
        }

        // Convert distance to similarity
        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Calculate Levenshtein distance between two strings
     *
     * @param s1 first string
     * @param s2 second string
     * @return Levenshtein distance
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * Calculate similarity between two attribute maps
     *
     * @param attrs1 first attribute map
     * @param attrs2 second attribute map
     * @return similarity score between 0.0 and 1.0
     */
    private double calculateAttributeSimilarity(Map<String, String> attrs1, Map<String, String> attrs2) {
        if (attrs1.isEmpty() && attrs2.isEmpty()) {
            return 1.0;
        }

        // Count matching attributes
        int matchCount = 0;
        int totalAttrs = 0;

        // Check attributes in the first map
        for (Map.Entry<String, String> entry : attrs1.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (attrs2.containsKey(key)) {
                String otherValue = attrs2.get(key);
                if (Objects.equals(value, otherValue)) {
                    matchCount++;
                }
            }
            totalAttrs++;
        }

        // Add attributes only in the second map
        for (String key : attrs2.keySet()) {
            if (!attrs1.containsKey(key)) {
                totalAttrs++;
            }
        }

        return (double) matchCount / totalAttrs;
    }

    /**
     * Calculate similarity between two property maps
     *
     * @param props1 first property map
     * @param props2 second property map
     * @return similarity score between 0.0 and 1.0
     */
    private double calculatePropertySimilarity(Map<String, Double> props1, Map<String, Double> props2) {
        if (props1.isEmpty() && props2.isEmpty()) {
            return 1.0;
        }

        double similaritySum = 0.0;
        int propertyCount = 0;

        // Check properties in both maps
        for (String key : props1.keySet()) {
            if (props2.containsKey(key)) {
                double value1 = props1.get(key);
                double value2 = props2.get(key);

                // Calculate relative difference
                double maxValue = Math.max(Math.abs(value1), Math.abs(value2));
                if (maxValue < 0.0001) {
                    // Prevent division by zero for very small values
                    similaritySum += 1.0;
                } else {
                    double difference = Math.abs(value1 - value2) / maxValue;
                    // Convert difference to similarity
                    similaritySum += Math.max(0.0, 1.0 - difference);
                }
                propertyCount++;
            }
        }

        if (propertyCount == 0) {
            return 0.0;
        }

        return similaritySum / propertyCount;
    }

    /**
     * Calculate similarity between two visual signatures
     *
     * @param sig1 first visual signature
     * @param sig2 second visual signature
     * @return similarity score between 0.0 and 1.0
     */
    private double calculateVisualSimilarity(String sig1, String sig2) {
        // This is a placeholder implementation
        // In a real system, we would compare image hashes or feature vectors

        if (sig1 == null || sig2 == null) {
            return 0.0;
        }

        if (sig1.equals(sig2)) {
            return 1.0;
        }

        // Simple implementation using string similarity
        return calculateTextSimilarity(sig1, sig2);
    }

    /**
     * Generate a simplified fingerprint string for parent references
     *
     * @return a simplified fingerprint
     */
    public String toSimplifiedFingerprint() {
        StringBuilder sb = new StringBuilder();

        sb.append(elementType != null ? elementType : "unknown");

        if (elementId != null && !elementId.isEmpty()) {
            sb.append("#").append(elementId);
        }

        if (attributes != null && attributes.containsKey("class")) {
            sb.append(".").append(attributes.get("class"));
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElementFingerprint that = (ElementFingerprint) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}