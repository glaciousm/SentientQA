package com.projectoracle.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a test case that can be generated, analyzed, and executed.
 * Core domain object of the Sentinel system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {

    private UUID id;
    private String name;
    private String description;
    private TestType type;
    private TestPriority priority;
    private TestStatus status;
    private String packageName;
    private String className;
    private String methodName;
    private String sourceCode;
    private List<String> assertions;
    private List<String> dependencies;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private LocalDateTime lastExecutedAt;
    private Double confidenceScore;
    private String generationPrompt;
    private TestExecutionResult lastExecutionResult;
    
    // Execution related fields
    private LocalDateTime lastExecuted;
    private Long executionTime;
    private String executionOutput;
    private String errorMessage;
    private Boolean runningInCi;

    // Issue tracker integration
    private String linkedIssueKey;
    
    // Knowledge integration fields
    private List<KnowledgeSource> knowledgeSources;
    private Double knowledgeEnhancementScore; // How much the test was improved by knowledge integration
    
    // UI/API test related fields
    private String targetPage; // UI page or API endpoint target
    private String targetEndpoint; // API endpoint path
    private String baseUrl; // Base URL for test
    private String httpMethod; // HTTP method for API tests
    private List<String> components; // UI components involved
    private List<String> steps; // Steps to perform in the test
    
    // Legacy field for backward compatibility
    private Boolean apiTest; // Kept for backward compatibility with older JSON files

    /**
     * The type of test case
     */
    public enum TestType {
        UNIT,
        INTEGRATION,
        API,
        UI,
        PERFORMANCE,
        SECURITY
    }

    /**
     * The priority of the test case
     */
    public enum TestPriority {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * The current status of the test case
     */
    public enum TestStatus {
        GENERATED,
        REVIEWED,
        APPROVED,
        RUNNING,
        EXECUTING,
        PASSED,
        FAILED,
        BROKEN,
        HEALED
    }

    /**
     * Create a deep copy of this test case
     * 
     * @return a new independent copy of this test case
     */
    public TestCase deepCopy() {
        TestCase copy = TestCase.builder()
                .id(this.id)
                .name(this.name)
                .description(this.description)
                .type(this.type)
                .priority(this.priority)
                .status(this.status)
                .packageName(this.packageName)
                .className(this.className)
                .methodName(this.methodName)
                .sourceCode(this.sourceCode)
                .createdAt(this.createdAt)
                .modifiedAt(this.modifiedAt)
                .lastExecutedAt(this.lastExecutedAt)
                .confidenceScore(this.confidenceScore)
                .generationPrompt(this.generationPrompt)
                .lastExecuted(this.lastExecuted)
                .executionTime(this.executionTime)
                .executionOutput(this.executionOutput)
                .errorMessage(this.errorMessage)
                .runningInCi(this.runningInCi)
                .linkedIssueKey(this.linkedIssueKey)
                .knowledgeEnhancementScore(this.knowledgeEnhancementScore)
                .targetPage(this.targetPage)
                .targetEndpoint(this.targetEndpoint)
                .baseUrl(this.baseUrl)
                .httpMethod(this.httpMethod)
                .apiTest(this.apiTest)
                .build();
        
        // Copy lists (if they exist)
        if (this.assertions != null) {
            copy.setAssertions(new ArrayList<>(this.assertions));
        }
        
        if (this.dependencies != null) {
            copy.setDependencies(new ArrayList<>(this.dependencies));
        }
        
        // Copy knowledge sources (if they exist)
        if (this.knowledgeSources != null) {
            copy.setKnowledgeSources(new ArrayList<>(this.knowledgeSources));
        }
        
        // Copy UI/API test related lists
        if (this.components != null) {
            copy.setComponents(new ArrayList<>(this.components));
        }
        
        if (this.steps != null) {
            copy.setSteps(new ArrayList<>(this.steps));
        }
        
        // Copy execution result (if it exists)
        if (this.lastExecutionResult != null) {
            copy.setLastExecutionResult(this.lastExecutionResult.deepCopy());
        }
        
        return copy;
    }

    /**
     * Check if this is a UI test
     * @return true if this is a UI test
     */
    public boolean isUiTest() {
        return type == TestType.UI;
    }
    
    /**
     * Check if this is an API test
     * @return true if this is an API test
     */
    public boolean isApiTest() {
        // First check the type, but fall back to the legacy field for backward compatibility
        if (type == TestType.API) {
            return true;
        }
        
        // Check the legacy field if present
        return apiTest != null && apiTest;
    }

    /**
     * Result of test execution
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestExecutionResult {
        private boolean success;
        private long executionTimeMs;
        private String errorMessage;
        private String stackTrace;
        private LocalDateTime executedAt;
        
        /**
         * Create a deep copy of this execution result
         * 
         * @return a new independent copy of this execution result
         */
        public TestExecutionResult deepCopy() {
            return TestExecutionResult.builder()
                    .success(this.success)
                    .executionTimeMs(this.executionTimeMs)
                    .errorMessage(this.errorMessage)
                    .stackTrace(this.stackTrace)
                    .executedAt(this.executedAt)
                    .build();
        }
    }
}