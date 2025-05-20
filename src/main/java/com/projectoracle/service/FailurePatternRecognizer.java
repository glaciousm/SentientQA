package com.projectoracle.service;

import com.projectoracle.model.TestCase;
import com.projectoracle.model.TestExecutionHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for recognizing patterns in test failures
 * and generating self-healing suggestions.
 */
@Service
public class FailurePatternRecognizer {

    private static final Logger logger = LoggerFactory.getLogger(FailurePatternRecognizer.class);
    
    // Known failure patterns with regular expressions for matching
    private final Map<String, FailurePatternMatcher> knownPatterns;
    
    /**
     * Constructor initializes known failure patterns
     */
    public FailurePatternRecognizer() {
        this.knownPatterns = initializeKnownPatterns();
    }
    
    /**
     * Analyze a test failure and identify failure patterns
     * 
     * @param testCase The failing test case
     * @param errorMessage The error message
     * @param stackTrace The stack trace
     * @return List of detected failure patterns
     */
    public List<TestExecutionHistory.FailurePattern> analyzeFailure(TestCase testCase, 
                                                                   String errorMessage, 
                                                                   String stackTrace) {
        logger.info("Analyzing failure for test: {}", testCase.getName());
        
        if (errorMessage == null && stackTrace == null) {
            logger.warn("No error message or stack trace provided for analysis");
            return List.of();
        }
        
        List<TestExecutionHistory.FailurePattern> detectedPatterns = new ArrayList<>();
        
        // Check for known patterns
        for (Map.Entry<String, FailurePatternMatcher> entry : knownPatterns.entrySet()) {
            FailurePatternMatcher matcher = entry.getValue();
            
            if (matcher.matches(errorMessage, stackTrace, testCase.getSourceCode())) {
                // Create a failure pattern with a unique ID
                TestExecutionHistory.FailurePattern pattern = TestExecutionHistory.FailurePattern.builder()
                    .patternId(entry.getKey() + "-" + UUID.randomUUID().toString().substring(0, 8))
                    .patternType(entry.getKey())
                    .description(matcher.getDescription())
                    .errorSignature(matcher.getSignature(errorMessage, stackTrace))
                    .occurrences(1)
                    .confidenceScore(matcher.getConfidence(errorMessage, stackTrace))
                    .suggestedFixes(matcher.getSuggestedFixes(testCase, errorMessage, stackTrace))
                    .properties(new HashMap<>())
                    .build();
                
                detectedPatterns.add(pattern);
                
                // Only match one primary pattern per category
                // (to avoid overlapping patterns of the same type)
            }
        }
        
        // If no known patterns were found, create a generic pattern
        if (detectedPatterns.isEmpty() && errorMessage != null) {
            String errorType = extractErrorType(errorMessage, stackTrace);
            String errorSignature = createErrorSignature(errorMessage, stackTrace);
            
            TestExecutionHistory.FailurePattern genericPattern = TestExecutionHistory.FailurePattern.builder()
                .patternId("generic-" + UUID.randomUUID().toString().substring(0, 8))
                .patternType(errorType)
                .description("Generic " + errorType + " error")
                .errorSignature(errorSignature)
                .occurrences(1)
                .confidenceScore(0.5) // Medium confidence for generic patterns
                .suggestedFixes(new ArrayList<>()) // No specific fixes for generic pattern
                .properties(new HashMap<>())
                .build();
            
            detectedPatterns.add(genericPattern);
        }
        
        return detectedPatterns;
    }
    
    /**
     * Initialize known failure patterns
     * 
     * @return Map of pattern name to matcher
     */
    private Map<String, FailurePatternMatcher> initializeKnownPatterns() {
        Map<String, FailurePatternMatcher> patterns = new HashMap<>();
        
        // NullPointerException patterns
        patterns.put("NullPointerException", new FailurePatternMatcher() {
            @Override
            public boolean matches(String errorMessage, String stackTrace, String testCode) {
                return errorMessage != null && 
                       (errorMessage.contains("NullPointerException") || 
                        (stackTrace != null && stackTrace.contains("NullPointerException")));
            }
            
            @Override
            public String getDescription() {
                return "Null reference was accessed";
            }
            
            @Override
            public String getSignature(String errorMessage, String stackTrace) {
                // Extract the line number and class from stack trace
                if (stackTrace != null) {
                    Pattern pattern = Pattern.compile("at ([\\w$.]+)\\.(\\w+)\\(([\\w$.]+\\.java):(\\d+)\\)");
                    Matcher matcher = pattern.matcher(stackTrace);
                    if (matcher.find()) {
                        return "NPE:" + matcher.group(1) + ":" + matcher.group(4);
                    }
                }
                return "NPE:unknown";
            }
            
            @Override
            public double getConfidence(String errorMessage, String stackTrace) {
                return 0.9; // High confidence for NPE
            }
            
            @Override
            public List<String> getSuggestedFixes(TestCase testCase, String errorMessage, String stackTrace) {
                List<String> fixes = new ArrayList<>();
                
                // Extract variable from stack trace if possible
                String variableHint = extractVariableFromNPE(stackTrace, testCase.getSourceCode());
                
                fixes.add("Add null check before accessing the object: if (" + 
                          (variableHint != null ? variableHint : "object") + 
                          " != null) { ... }");
                fixes.add("Initialize the variable before using it");
                fixes.add("Use Optional<> to handle potentially null values");
                fixes.add("Use objects that are guaranteed to be non-null (empty collections instead of null)");
                
                return fixes;
            }
            
            private String extractVariableFromNPE(String stackTrace, String testCode) {
                if (stackTrace == null || testCode == null) {
                    return null;
                }
                
                // Extract line number from stack trace
                Pattern linePattern = Pattern.compile("\\(([\\w$.]+\\.java):(\\d+)\\)");
                Matcher lineMatcher = linePattern.matcher(stackTrace);
                if (lineMatcher.find()) {
                    int lineNumber = Integer.parseInt(lineMatcher.group(2));
                    
                    // Get the line from the test code
                    String[] lines = testCode.split("\n");
                    if (lineNumber > 0 && lineNumber <= lines.length) {
                        String line = lines[lineNumber - 1].trim();
                        
                        // Try to extract variable name (very simplified)
                        Pattern varPattern = Pattern.compile("(\\w+)\\.");
                        Matcher varMatcher = varPattern.matcher(line);
                        if (varMatcher.find()) {
                            return varMatcher.group(1);
                        }
                    }
                }
                
                return null;
            }
        });
        
        // AssertionError patterns
        patterns.put("AssertionError", new FailurePatternMatcher() {
            @Override
            public boolean matches(String errorMessage, String stackTrace, String testCode) {
                return errorMessage != null && 
                       (errorMessage.contains("AssertionError") || 
                        errorMessage.contains("expected") && errorMessage.contains("but was") ||
                        (stackTrace != null && stackTrace.contains("AssertionError")));
            }
            
            @Override
            public String getDescription() {
                return "Test assertion failed";
            }
            
            @Override
            public String getSignature(String errorMessage, String stackTrace) {
                if (errorMessage != null && errorMessage.contains("expected") && errorMessage.contains("but was")) {
                    // Extract expected and actual values
                    Pattern pattern = Pattern.compile("expected:<(.+)> but was:<(.+)>");
                    Matcher matcher = pattern.matcher(errorMessage);
                    if (matcher.find()) {
                        String expected = matcher.group(1);
                        String actual = matcher.group(2);
                        return "ASSERT:expected:" + (expected.length() > 20 ? expected.substring(0, 20) + "..." : expected) +
                               ":actual:" + (actual.length() > 20 ? actual.substring(0, 20) + "..." : actual);
                    }
                }
                return "ASSERT:unknown";
            }
            
            @Override
            public double getConfidence(String errorMessage, String stackTrace) {
                return 0.95; // Very high confidence for assertion errors
            }
            
            @Override
            public List<String> getSuggestedFixes(TestCase testCase, String errorMessage, String stackTrace) {
                List<String> fixes = new ArrayList<>();
                
                // Extract expected and actual values
                Pattern pattern = Pattern.compile("expected:<(.+)> but was:<(.+)>");
                Matcher matcher = pattern.matcher(errorMessage);
                String expected = null;
                String actual = null;
                
                if (matcher.find()) {
                    expected = matcher.group(1);
                    actual = matcher.group(2);
                }
                
                fixes.add("Verify the expected value in the assertion: " + 
                          (expected != null ? "expected: " + expected : ""));
                
                fixes.add("Check the actual value calculation: " + 
                          (actual != null ? "actual: " + actual : ""));
                
                // Handle common specific cases
                if (expected != null && actual != null) {
                    // Whitespace issues
                    if (expected.trim().equals(actual.trim())) {
                        fixes.add("Whitespace difference detected. Use assertEquals() with normalized whitespace, or consider using assertions that ignore whitespace");
                    }
                    
                    // Case sensitivity issues
                    if (expected.equalsIgnoreCase(actual)) {
                        fixes.add("Case difference detected. Consider using case-insensitive comparison or normalize case before comparing");
                    }
                    
                    // Floating point comparison
                    if (isNumeric(expected) && isNumeric(actual)) {
                        fixes.add("For floating point values, use assertEquals with delta parameter: assertEquals(expected, actual, delta)");
                    }
                }
                
                fixes.add("Review test logic and business rules to confirm expected behavior");
                fixes.add("Consider if the expected value should be updated to match actual behavior");
                
                return fixes;
            }
            
            private boolean isNumeric(String str) {
                return str.matches("-?\\d+(\\.\\d+)?");
            }
        });
        
        // IndexOutOfBoundsException patterns
        patterns.put("IndexOutOfBoundsException", new FailurePatternMatcher() {
            @Override
            public boolean matches(String errorMessage, String stackTrace, String testCode) {
                return errorMessage != null && 
                       (errorMessage.contains("IndexOutOfBoundsException") || 
                        errorMessage.contains("ArrayIndexOutOfBoundsException") ||
                        (stackTrace != null && (stackTrace.contains("IndexOutOfBoundsException") || 
                                                stackTrace.contains("ArrayIndexOutOfBoundsException"))));
            }
            
            @Override
            public String getDescription() {
                return "Array or collection index out of bounds";
            }
            
            @Override
            public String getSignature(String errorMessage, String stackTrace) {
                // Extract the index if possible
                Pattern pattern = Pattern.compile("Index\\s+(\\d+)\\s+out of bounds for length\\s+(\\d+)");
                Matcher matcher = pattern.matcher(errorMessage);
                if (matcher.find()) {
                    return "IOOB:index:" + matcher.group(1) + ":length:" + matcher.group(2);
                }
                return "IOOB:unknown";
            }
            
            @Override
            public double getConfidence(String errorMessage, String stackTrace) {
                return 0.9; // High confidence
            }
            
            @Override
            public List<String> getSuggestedFixes(TestCase testCase, String errorMessage, String stackTrace) {
                List<String> fixes = new ArrayList<>();
                
                // Extract index and length if possible
                Pattern pattern = Pattern.compile("Index\\s+(\\d+)\\s+out of bounds for length\\s+(\\d+)");
                Matcher matcher = pattern.matcher(errorMessage);
                if (matcher.find()) {
                    int index = Integer.parseInt(matcher.group(1));
                    int length = Integer.parseInt(matcher.group(2));
                    
                    fixes.add("Index " + index + " is out of bounds for array/collection of length " + length);
                    fixes.add("Check array/collection bounds before accessing: if (index < collection.size()) { ... }");
                    
                    if (index == length) {
                        fixes.add("The index is equal to the length - remember that indices are zero-based (max index is length-1)");
                    } else if (index < 0) {
                        fixes.add("Negative index detected - ensure index is non-negative");
                    } else if (index > length) {
                        fixes.add("Ensure adequate size of array/collection before accessing elements");
                    }
                } else {
                    fixes.add("Check array/collection bounds before accessing elements");
                    fixes.add("Use collection.isEmpty() check before accessing first element");
                    fixes.add("Verify loop conditions to prevent access beyond array/collection bounds");
                }
                
                return fixes;
            }
        });
        
        // ClassCastException patterns
        patterns.put("ClassCastException", new FailurePatternMatcher() {
            @Override
            public boolean matches(String errorMessage, String stackTrace, String testCode) {
                return errorMessage != null && 
                       (errorMessage.contains("ClassCastException") ||
                        (stackTrace != null && stackTrace.contains("ClassCastException")));
            }
            
            @Override
            public String getDescription() {
                return "Invalid type cast operation";
            }
            
            @Override
            public String getSignature(String errorMessage, String stackTrace) {
                // Extract the class names if possible
                Pattern pattern = Pattern.compile("cannot be cast to ([\\w$.]+)");
                Matcher matcher = pattern.matcher(errorMessage);
                if (matcher.find()) {
                    return "CCE:target:" + matcher.group(1);
                }
                return "CCE:unknown";
            }
            
            @Override
            public double getConfidence(String errorMessage, String stackTrace) {
                return 0.9; // High confidence
            }
            
            @Override
            public List<String> getSuggestedFixes(TestCase testCase, String errorMessage, String stackTrace) {
                List<String> fixes = new ArrayList<>();
                
                fixes.add("Use 'instanceof' operator to check type before casting");
                fixes.add("Review object hierarchy to ensure types are compatible");
                fixes.add("Consider using safe cast methods or pattern matching (Java 16+)");
                
                // Extract from and to classes if possible
                Pattern pattern = Pattern.compile("([\\w$.]+) cannot be cast to ([\\w$.]+)");
                Matcher matcher = pattern.matcher(errorMessage);
                if (matcher.find()) {
                    String fromClass = matcher.group(1);
                    String toClass = matcher.group(2);
                    
                    fixes.add("Cannot cast from '" + fromClass + "' to '" + toClass + "' - types are incompatible");
                    fixes.add("Check the actual type of objects before attempting to cast");
                    
                    // Check if it's a primitive/wrapper conversion issue
                    if ((fromClass.contains("Integer") && toClass.contains("int")) ||
                        (fromClass.contains("Boolean") && toClass.contains("boolean")) ||
                        (fromClass.contains("Double") && toClass.contains("double"))) {
                        fixes.add("For primitive/wrapper conversion, use proper unboxing: Integer.intValue(), etc.");
                    }
                }
                
                return fixes;
            }
        });
        
        // NoSuchElementException patterns
        patterns.put("NoSuchElementException", new FailurePatternMatcher() {
            @Override
            public boolean matches(String errorMessage, String stackTrace, String testCode) {
                return errorMessage != null && 
                       (errorMessage.contains("NoSuchElementException") ||
                        (stackTrace != null && stackTrace.contains("NoSuchElementException")));
            }
            
            @Override
            public String getDescription() {
                return "Attempted to access a non-existent element";
            }
            
            @Override
            public String getSignature(String errorMessage, String stackTrace) {
                return "NSE:" + (errorMessage != null ? errorMessage.hashCode() : "unknown");
            }
            
            @Override
            public double getConfidence(String errorMessage, String stackTrace) {
                return 0.85; 
            }
            
            @Override
            public List<String> getSuggestedFixes(TestCase testCase, String errorMessage, String stackTrace) {
                List<String> fixes = new ArrayList<>();
                
                fixes.add("Check if collection/iterator has elements before calling .next()");
                fixes.add("Use Optional.ofNullable() and handle empty cases");
                fixes.add("Add explicit checks for element existence: if (collection.contains(element)) { ... }");
                fixes.add("For Map operations, use containsKey() before get(), or getOrDefault()");
                
                return fixes;
            }
        });
        
        // Add more patterns here
        
        return patterns;
    }
    
    /**
     * Extract the error type from message and stack trace
     * 
     * @param errorMessage The error message
     * @param stackTrace The stack trace
     * @return The error type
     */
    private String extractErrorType(String errorMessage, String stackTrace) {
        if (errorMessage == null && stackTrace == null) {
            return "Unknown";
        }
        
        // First try to extract from error message
        if (errorMessage != null) {
            // Common error patterns
            if (errorMessage.contains("NullPointerException")) {
                return "NullPointerException";
            } else if (errorMessage.contains("IndexOutOfBoundsException") || 
                       errorMessage.contains("ArrayIndexOutOfBoundsException")) {
                return "IndexOutOfBoundsException";
            } else if (errorMessage.contains("ClassCastException")) {
                return "ClassCastException";
            } else if (errorMessage.contains("NoSuchElementException")) {
                return "NoSuchElementException";
            } else if (errorMessage.contains("IllegalArgumentException")) {
                return "IllegalArgumentException";
            } else if (errorMessage.contains("AssertionError")) {
                return "AssertionError";
            }
            
            // Try to extract class name from error message
            Pattern pattern = Pattern.compile("([a-zA-Z0-9]+Exception|[a-zA-Z0-9]+Error)");
            Matcher matcher = pattern.matcher(errorMessage);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        // Next try stack trace
        if (stackTrace != null) {
            String[] lines = stackTrace.split("\n");
            if (lines.length > 0) {
                String firstLine = lines[0];
                
                // Common error patterns
                if (firstLine.contains("NullPointerException")) {
                    return "NullPointerException";
                } else if (firstLine.contains("IndexOutOfBoundsException") || 
                           firstLine.contains("ArrayIndexOutOfBoundsException")) {
                    return "IndexOutOfBoundsException";
                } else if (firstLine.contains("ClassCastException")) {
                    return "ClassCastException";
                } else if (firstLine.contains("NoSuchElementException")) {
                    return "NoSuchElementException";
                } else if (firstLine.contains("IllegalArgumentException")) {
                    return "IllegalArgumentException";
                } else if (firstLine.contains("AssertionError")) {
                    return "AssertionError";
                }
                
                // Try to extract class name from stack trace
                Pattern pattern = Pattern.compile("([a-zA-Z0-9]+Exception|[a-zA-Z0-9]+Error)");
                Matcher matcher = pattern.matcher(firstLine);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        
        return "Unknown";
    }
    
    /**
     * Create a unique signature for an error
     * 
     * @param errorMessage The error message
     * @param stackTrace The stack trace
     * @return A unique signature for deduplication
     */
    private String createErrorSignature(String errorMessage, String stackTrace) {
        StringBuilder signature = new StringBuilder();
        
        // Add error type
        String errorType = extractErrorType(errorMessage, stackTrace);
        signature.append(errorType).append(":");
        
        // Add first line of error message (trimmed)
        if (errorMessage != null) {
            String firstLine = errorMessage.split("\n")[0].trim();
            signature.append(firstLine.length() > 50 ? firstLine.substring(0, 50) : firstLine);
        } else {
            signature.append("unknown");
        }
        
        // Add first line of stack trace that isn't about test framework
        if (stackTrace != null) {
            String[] lines = stackTrace.split("\n");
            for (String line : lines) {
                if (!line.contains("org.junit") && !line.contains("sun.reflect") &&
                    !line.contains("java.lang.reflect") && !line.contains("org.testng")) {
                    signature.append(":").append(line.trim());
                    break;
                }
            }
        }
        
        return signature.toString();
    }
    
    /**
     * Interface for failure pattern matchers
     */
    private interface FailurePatternMatcher {
        /**
         * Check if this pattern matches the error
         * 
         * @param errorMessage The error message
         * @param stackTrace The stack trace
         * @param testCode The test code that failed
         * @return True if pattern matches
         */
        boolean matches(String errorMessage, String stackTrace, String testCode);
        
        /**
         * Get a human-readable description of the pattern
         * 
         * @return Pattern description
         */
        String getDescription();
        
        /**
         * Get a unique signature for this error instance
         * 
         * @param errorMessage The error message
         * @param stackTrace The stack trace
         * @return Unique signature for deduplication
         */
        String getSignature(String errorMessage, String stackTrace);
        
        /**
         * Get confidence score for this pattern match (0.0-1.0)
         * 
         * @param errorMessage The error message
         * @param stackTrace The stack trace
         * @return Confidence score
         */
        double getConfidence(String errorMessage, String stackTrace);
        
        /**
         * Get suggested fixes for this pattern
         * 
         * @param testCase The failing test case
         * @param errorMessage The error message
         * @param stackTrace The stack trace
         * @return List of suggested fixes
         */
        List<String> getSuggestedFixes(TestCase testCase, String errorMessage, String stackTrace);
    }
}