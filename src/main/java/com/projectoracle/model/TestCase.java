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
 * Core domain object of the Project Oracle system.
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
                .build();
        
        // Copy lists (if they exist)
        if (this.assertions != null) {
            copy.setAssertions(new ArrayList<>(this.assertions));
        }
        
        if (this.dependencies != null) {
            copy.setDependencies(new ArrayList<>(this.dependencies));
        }
        
        // Copy execution result (if it exists)
        if (this.lastExecutionResult != null) {
            copy.setLastExecutionResult(this.lastExecutionResult.deepCopy());
        }
        
        return copy;
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