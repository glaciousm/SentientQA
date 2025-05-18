package com.projectoracle.model;

import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Represents a test case that can be generated, analyzed, and executed.
 * Core domain object of the Project Oracle system.
 */
@Data
@Builder
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
     * Result of test execution
     */
    @Data
    @Builder
    public static class TestExecutionResult {
        private boolean success;
        private long executionTimeMs;
        private String errorMessage;
        private String stackTrace;
        private LocalDateTime executedAt;
    }
}