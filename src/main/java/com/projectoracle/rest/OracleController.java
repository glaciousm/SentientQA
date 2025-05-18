package com.projectoracle.rest;

import com.projectoracle.model.TestCase;
import com.projectoracle.service.CodeAnalysisService;
import com.projectoracle.service.MethodInfo;
import com.projectoracle.service.TestGenerationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for Project Oracle API endpoints.
 * Provides access to code analysis and test generation services.
 */
@RestController
@RequestMapping("/api/v1")
public class OracleController {

    private static final Logger logger = LoggerFactory.getLogger(OracleController.class);

    @Autowired
    private CodeAnalysisService codeAnalysisService;

    @Autowired
    private TestGenerationService testGenerationService;

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Project Oracle API is running");
    }

    /**
     * Analyze Java code
     */
    @PostMapping("/analyze/code")
    public ResponseEntity<Map<String, MethodInfo>> analyzeCode(@RequestBody String sourceCode) {
        logger.info("Received code analysis request with {} characters", sourceCode.length());
        Map<String, MethodInfo> methodInfos = codeAnalysisService.analyzeJavaSource(sourceCode);
        return ResponseEntity.ok(methodInfos);
    }

    /**
     * Analyze Java file at a path
     */
    @GetMapping("/analyze/file")
    public ResponseEntity<Map<String, MethodInfo>> analyzeFile(@RequestParam String filePath) {
        logger.info("Analyzing file: {}", filePath);
        Map<String, MethodInfo> methodInfos = codeAnalysisService.analyzeJavaFile(Path.of(filePath));
        return ResponseEntity.ok(methodInfos);
    }

    /**
     * Scan a directory for Java files
     */
    @GetMapping("/analyze/directory")
    public ResponseEntity<List<MethodInfo>> scanDirectory(@RequestParam String dirPath) {
        logger.info("Scanning directory: {}", dirPath);
        List<MethodInfo> methodInfos = codeAnalysisService.scanDirectory(Path.of(dirPath));
        return ResponseEntity.ok(methodInfos);
    }

    /**
     * Generate a test for a specific method
     */
    @PostMapping("/generate/test")
    public ResponseEntity<TestCase> generateTest(@RequestBody MethodInfo methodInfo) {
        logger.info("Generating test for method: {}", methodInfo.getSignature());
        TestCase testCase = testGenerationService.generateTestForMethod(methodInfo);
        return ResponseEntity.ok(testCase);
    }

    /**
     * Generate tests for all methods in a file
     */
    @PostMapping("/generate/tests/file")
    public ResponseEntity<List<TestCase>> generateTestsForFile(@RequestParam String filePath) {
        logger.info("Generating tests for file: {}", filePath);
        Map<String, MethodInfo> methodInfos = codeAnalysisService.analyzeJavaFile(Path.of(filePath));

        List<TestCase> testCases = methodInfos.values().stream()
                                              .map(testGenerationService::generateTestForMethod)
                                              .collect(Collectors.toList());

        return ResponseEntity.ok(testCases);
    }
}