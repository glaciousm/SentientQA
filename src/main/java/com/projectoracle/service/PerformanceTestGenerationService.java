package com.projectoracle.service;

import com.projectoracle.model.TestCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for generating performance tests.
 * Uses code analysis and AI to create JMH benchmark tests.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PerformanceTestGenerationService {

    private final AIModelService aiModelService;
    private final CodeAnalysisService codeAnalysisService;
    private final TestGenerationService testGenerationService;
    
    /**
     * Generate a performance test for a specific method
     * 
     * @param className The class name
     * @param methodName The method name
     * @param benchmarkType The type of benchmark to generate
     * @return Generated performance test case
     */
    public TestCase generatePerformanceTest(String className, String methodName, BenchmarkType benchmarkType) {
        log.info("Generating {} performance test for {}.{}", benchmarkType, className, methodName);
        
        // Get method info
        MethodInfo methodInfo = codeAnalysisService.getMethodInfo(className, methodName);
        if (methodInfo == null) {
            log.error("Method not found: {}.{}", className, methodName);
            throw new IllegalArgumentException("Method not found: " + className + "." + methodName);
        }
        
        // Build prompt for AI model
        String prompt = buildPerformanceTestPrompt(methodInfo, benchmarkType);
        
        // Generate test code
        String testCode = aiModelService.generateText(prompt, 2000);
        
        // Create test case
        return TestCase.builder()
                .id(UUID.randomUUID())
                .name(methodName + "PerformanceTest")
                .description("Performance test for " + className + "." + methodName)
                .type("Performance")
                .priority(TestCase.TestPriority.MEDIUM)
                .status(TestCase.TestStatus.GENERATED)
                .packageName(methodInfo.getPackageName() + ".performance")
                .className("Performance" + className + "Test")
                .methodName("benchmark" + methodName)
                .sourceCode(testCode)
                .generationPrompt(prompt)
                .assertions(List.of("// Performance assertions via JMH"))
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * Generate multiple performance tests for a class
     * 
     * @param className The class name
     * @param benchmarkType The type of benchmark to generate
     * @return List of generated performance test cases
     */
    public List<TestCase> generatePerformanceTestsForClass(String className, BenchmarkType benchmarkType) {
        log.info("Generating {} performance tests for class {}", benchmarkType, className);
        
        // Get all methods for class
        Map<String, MethodInfo> methodInfos = codeAnalysisService.getMethodsForClass(className);
        List<TestCase> generatedTests = new ArrayList<>();
        
        // Generate performance tests for methods that are suitable
        for (Map.Entry<String, MethodInfo> entry : methodInfos.entrySet()) {
            MethodInfo methodInfo = entry.getValue();
            
            // Skip methods that are not suitable for performance testing
            if (!isMethodSuitableForPerformanceTesting(methodInfo)) {
                log.debug("Skipping unsuitable method for performance testing: {}", methodInfo.getMethodName());
                continue;
            }
            
            try {
                TestCase test = generatePerformanceTest(className, methodInfo.getMethodName(), benchmarkType);
                generatedTests.add(test);
            } catch (Exception e) {
                log.error("Error generating performance test for {}.{}: {}", 
                          className, methodInfo.getMethodName(), e.getMessage());
            }
        }
        
        return generatedTests;
    }
    
    /**
     * Build a prompt for performance test generation
     * 
     * @param methodInfo Method information
     * @param benchmarkType Type of benchmark to generate
     * @return Generated prompt
     */
    private String buildPerformanceTestPrompt(MethodInfo methodInfo, BenchmarkType benchmarkType) {
        StringBuilder promptBuilder = new StringBuilder();
        
        promptBuilder.append("Generate a JMH (Java Microbenchmark Harness) performance test for the following Java method:\n\n");
        
        // Add method signature
        promptBuilder.append("Package: ").append(methodInfo.getPackageName()).append("\n");
        promptBuilder.append("Class: ").append(methodInfo.getClassName()).append("\n");
        promptBuilder.append("Method: ").append(methodInfo.getSignature()).append("\n\n");
        
        // Add method body if available
        if (methodInfo.getBody() != null) {
            promptBuilder.append("Method body:\n").append(methodInfo.getBody()).append("\n\n");
        }
        
        // Add benchmark type-specific instructions
        promptBuilder.append("Create a ").append(benchmarkType.getDescription()).append(" benchmark with the following characteristics:\n");
        
        for (String instruction : benchmarkType.getInstructions()) {
            promptBuilder.append("- ").append(instruction).append("\n");
        }
        
        // Add general requirements
        promptBuilder.append("\nGeneral requirements:\n");
        promptBuilder.append("- Use JMH annotations (@Benchmark, @BenchmarkMode, etc.)\n");
        promptBuilder.append("- Include proper setup/teardown methods (@Setup, @TearDown)\n");
        promptBuilder.append("- Include state management if needed (@State)\n");
        promptBuilder.append("- Provide appropriate parameter generation that covers edge cases\n");
        promptBuilder.append("- Add necessary imports for JMH and the tested class\n");
        promptBuilder.append("- Ensure the test is realistic and exercises the method properly\n");
        promptBuilder.append("- Include comments explaining the benchmark approach\n");
        
        // Add example JMH structure for reference
        promptBuilder.append("\nExample JMH structure (as reference only, create specific test for the method above):\n");
        promptBuilder.append("```java\n");
        promptBuilder.append("package com.example.benchmark;\n\n");
        promptBuilder.append("import org.openjdk.jmh.annotations.*;\n");
        promptBuilder.append("import java.util.concurrent.TimeUnit;\n\n");
        promptBuilder.append("@BenchmarkMode(Mode.AverageTime)\n");
        promptBuilder.append("@OutputTimeUnit(TimeUnit.MICROSECONDS)\n");
        promptBuilder.append("@State(Scope.Thread)\n");
        promptBuilder.append("@Fork(1)\n");
        promptBuilder.append("@Warmup(iterations = 3)\n");
        promptBuilder.append("@Measurement(iterations = 5)\n");
        promptBuilder.append("public class ExampleBenchmark {\n\n");
        promptBuilder.append("    @Param({\"100\", \"10000\"})\n");
        promptBuilder.append("    private int size;\n\n");
        promptBuilder.append("    private SomeClass instance;\n\n");
        promptBuilder.append("    @Setup\n");
        promptBuilder.append("    public void setup() {\n");
        promptBuilder.append("        instance = new SomeClass();\n");
        promptBuilder.append("        // Setup code here\n");
        promptBuilder.append("    }\n\n");
        promptBuilder.append("    @Benchmark\n");
        promptBuilder.append("    public void benchmarkMethod() {\n");
        promptBuilder.append("        // Benchmark code here\n");
        promptBuilder.append("    }\n}\n");
        promptBuilder.append("```\n\n");
        
        promptBuilder.append("Please provide the complete JMH benchmark code for testing the performance of ")
                    .append(methodInfo.getClassName()).append(".").append(methodInfo.getMethodName())
                    .append(" using the specified benchmark type.");
        
        return promptBuilder.toString();
    }
    
    /**
     * Check if a method is suitable for performance testing
     * 
     * @param methodInfo Method information
     * @return True if method is suitable for performance testing
     */
    private boolean isMethodSuitableForPerformanceTesting(MethodInfo methodInfo) {
        // Skip very simple methods (getters/setters)
        if (methodInfo.getMethodName().startsWith("get") || 
            methodInfo.getMethodName().startsWith("set") ||
            methodInfo.getMethodName().startsWith("is")) {
            
            // Check if method body is small (typical for getter/setter)
            if (methodInfo.getBody() == null || methodInfo.getBody().length() < 100) {
                return false;
            }
        }
        
        // Skip constructors
        if (methodInfo.getMethodName().equals(methodInfo.getClassName())) {
            return false;
        }
        
        // Skip methods with complex dependencies that are hard to mock
        if (methodInfo.getParameters() != null) {
            for (ParameterInfo param : methodInfo.getParameters()) {
                if (param.getType().contains("Connection") ||
                    param.getType().contains("Session") ||
                    param.getType().contains("Context")) {
                    return false;
                }
            }
        }
        
        // Skip void methods with no parameters (often side-effect methods)
        if (methodInfo.getReturnType().equals("void") && 
            (methodInfo.getParameters() == null || methodInfo.getParameters().isEmpty())) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Calculate performance impact of code changes
     * 
     * @param oldCode Previous code
     * @param newCode Updated code
     * @return Performance impact assessment
     */
    public PerformanceImpactAssessment assessPerformanceImpact(String oldCode, String newCode) {
        log.info("Assessing performance impact of code changes");
        
        // For now, use AI to analyze the code changes
        // In a real implementation, we would run actual performance benchmarks
        
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Analyze these code changes for potential performance impact.\n\n");
        promptBuilder.append("ORIGINAL CODE:\n").append(oldCode).append("\n\n");
        promptBuilder.append("NEW CODE:\n").append(newCode).append("\n\n");
        promptBuilder.append("Identify any potential performance implications of these changes. ");
        promptBuilder.append("Focus on time complexity, memory usage, resource utilization, ");
        promptBuilder.append("concurrency, and any other performance-critical aspects.");
        
        String analysis = aiModelService.generateText(promptBuilder.toString(), 500);
        
        // Simple heuristic analysis
        double impactScore = calculateImpactScore(oldCode, newCode, analysis);
        ImpactLevel impactLevel = determineImpactLevel(impactScore);
        
        return PerformanceImpactAssessment.builder()
                .oldCodeHash(String.valueOf(oldCode.hashCode()))
                .newCodeHash(String.valueOf(newCode.hashCode()))
                .impactScore(impactScore)
                .impactLevel(impactLevel)
                .analysis(analysis)
                .recommendations(generateRecommendations(impactLevel, analysis))
                .needsBenchmark(impactLevel != ImpactLevel.NONE)
                .build();
    }
    
    /**
     * Calculate impact score based on code changes
     */
    private double calculateImpactScore(String oldCode, String newCode, String analysis) {
        double score = 0.0;
        
        // Check for algorithmic changes (loops, recursion)
        int oldLoops = countOccurrences(oldCode, "for (") + countOccurrences(oldCode, "while (");
        int newLoops = countOccurrences(newCode, "for (") + countOccurrences(newCode, "while (");
        if (newLoops > oldLoops) {
            score += 0.2;
        }
        
        // Check for data structure changes
        if (oldCode.contains("ArrayList") && newCode.contains("LinkedList") ||
            oldCode.contains("HashMap") && newCode.contains("TreeMap")) {
            score += 0.15;
        }
        
        // Check for synchronized blocks
        int oldSyncBlocks = countOccurrences(oldCode, "synchronized");
        int newSyncBlocks = countOccurrences(newCode, "synchronized");
        if (newSyncBlocks > oldSyncBlocks) {
            score += 0.1;
        }
        
        // Check for thread creation
        if ((!oldCode.contains("new Thread") && newCode.contains("new Thread")) ||
            (!oldCode.contains("ExecutorService") && newCode.contains("ExecutorService"))) {
            score += 0.25;
        }
        
        // Check for IO operations
        if ((!oldCode.contains("InputStream") && newCode.contains("InputStream")) ||
            (!oldCode.contains("Reader") && newCode.contains("Reader"))) {
            score += 0.2;
        }
        
        // Check for keywords in AI analysis
        if (analysis.contains("O(n²)") || analysis.contains("quadratic")) {
            score += 0.3;
        }
        if (analysis.contains("memory leak") || analysis.contains("resource leak")) {
            score += 0.4;
        }
        if (analysis.contains("thread safety") || analysis.contains("race condition")) {
            score += 0.35;
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Determine impact level based on score
     */
    private ImpactLevel determineImpactLevel(double impactScore) {
        if (impactScore < 0.2) {
            return ImpactLevel.NONE;
        } else if (impactScore < 0.5) {
            return ImpactLevel.LOW;
        } else if (impactScore < 0.8) {
            return ImpactLevel.MEDIUM;
        } else {
            return ImpactLevel.HIGH;
        }
    }
    
    /**
     * Generate recommendations based on impact level and analysis
     */
    private List<String> generateRecommendations(ImpactLevel impactLevel, String analysis) {
        List<String> recommendations = new ArrayList<>();
        
        switch (impactLevel) {
            case NONE:
                recommendations.add("No performance testing needed");
                break;
                
            case LOW:
                recommendations.add("Run basic performance tests to verify no regression");
                recommendations.add("Focus on edge cases with large inputs");
                break;
                
            case MEDIUM:
                recommendations.add("Run comprehensive performance benchmark suite");
                recommendations.add("Monitor memory usage during test execution");
                recommendations.add("Compare benchmarks with previous version");
                break;
                
            case HIGH:
                recommendations.add("Run extensive performance benchmarks with various workloads");
                recommendations.add("Profile code execution to identify bottlenecks");
                recommendations.add("Test with production-like data volumes");
                recommendations.add("Consider load testing for concurrent operations");
                recommendations.add("Measure memory consumption over time for leak detection");
                break;
        }
        
        // Add specific recommendations based on analysis keywords
        if (analysis.contains("memory") || analysis.contains("heap")) {
            recommendations.add("Monitor heap usage to detect memory issues");
        }
        if (analysis.contains("thread") || analysis.contains("concurrency")) {
            recommendations.add("Test with multiple threads to verify thread safety");
        }
        if (analysis.contains("I/O") || analysis.contains("file") || analysis.contains("network")) {
            recommendations.add("Benchmark with I/O operations mocked to isolate application performance");
        }
        
        return recommendations;
    }
    
    /**
     * Count occurrences of a substring in a string
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        
        return count;
    }
    
    /**
     * Benchmark types for performance testing
     */
    public enum BenchmarkType {
        THROUGHPUT("throughput measurement", List.of(
            "Use @BenchmarkMode(Mode.Throughput) to measure operations/time",
            "Use @OutputTimeUnit(TimeUnit.SECONDS) for throughput measurement",
            "Focus on how many operations can be performed in a time unit",
            "Vary input sizes to analyze scalability",
            "Include multiple parameter sets using @Param"
        )),
        
        AVERAGE_TIME("average execution time", List.of(
            "Use @BenchmarkMode(Mode.AverageTime) to measure average execution time",
            "Use @OutputTimeUnit(TimeUnit.MICROSECONDS) or another appropriate unit",
            "Focus on how long each operation takes on average",
            "Include warm-up iterations to stabilize JIT compilation",
            "Use multiple measurement iterations for statistical significance"
        )),
        
        SAMPLE_TIME("execution time distribution", List.of(
            "Use @BenchmarkMode(Mode.SampleTime) to measure execution time distribution",
            "Use @OutputTimeUnit(TimeUnit.MICROSECONDS) for detailed measurements",
            "Focus on distribution of execution times (min, max, percentiles)",
            "Use larger sample size for statistical significance",
            "Include histogram output for visualizing distribution"
        )),
        
        SINGLE_SHOT("cold start performance", List.of(
            "Use @BenchmarkMode(Mode.SingleShotTime) for cold start measurement",
            "Use @OutputTimeUnit(TimeUnit.MILLISECONDS) for single operations",
            "Focus on first-call performance without JVM warm-up",
            "Disable warm-up iterations with @Warmup(iterations = 0)",
            "Run multiple forks for statistical significance with @Fork(10)"
        )),
        
        MEMORY("memory allocation", List.of(
            "Use the JMH memory profiler to measure allocations",
            "Add \"-prof gc\" to JMH command line arguments",
            "Focus on heap allocations during method execution",
            "Analyze both allocation rate and total memory used",
            "Check for objects that survive into old generation"
        ));
        
        private final String description;
        private final List<String> instructions;
        
        BenchmarkType(String description, List<String> instructions) {
            this.description = description;
            this.instructions = instructions;
        }
        
        public String getDescription() {
            return description;
        }
        
        public List<String> getInstructions() {
            return instructions;
        }
    }
    
    /**
     * Performance impact assessment result
     */
    public static class PerformanceImpactAssessment {
        private String oldCodeHash;
        private String newCodeHash;
        private double impactScore;
        private ImpactLevel impactLevel;
        private String analysis;
        private List<String> recommendations;
        private boolean needsBenchmark;
        
        @lombok.Builder
        public PerformanceImpactAssessment(String oldCodeHash, String newCodeHash, 
                                         double impactScore, ImpactLevel impactLevel,
                                         String analysis, List<String> recommendations,
                                         boolean needsBenchmark) {
            this.oldCodeHash = oldCodeHash;
            this.newCodeHash = newCodeHash;
            this.impactScore = impactScore;
            this.impactLevel = impactLevel;
            this.analysis = analysis;
            this.recommendations = recommendations;
            this.needsBenchmark = needsBenchmark;
        }
        
        public String getOldCodeHash() { return oldCodeHash; }
        public String getNewCodeHash() { return newCodeHash; }
        public double getImpactScore() { return impactScore; }
        public ImpactLevel getImpactLevel() { return impactLevel; }
        public String getAnalysis() { return analysis; }
        public List<String> getRecommendations() { return recommendations; }
        public boolean isNeedsBenchmark() { return needsBenchmark; }
    }
    
    /**
     * Performance impact levels
     */
    public enum ImpactLevel {
        NONE("No significant impact expected"),
        LOW("Low impact, minimal performance changes expected"),
        MEDIUM("Medium impact, noticeable performance changes possible"),
        HIGH("High impact, significant performance changes likely");
        
        private final String description;
        
        ImpactLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}