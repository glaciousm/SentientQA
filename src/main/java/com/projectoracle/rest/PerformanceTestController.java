package com.projectoracle.rest;

import com.projectoracle.model.TestCase;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.service.PerformanceTestGenerationService;
import com.projectoracle.service.PerformanceTestGenerationService.BenchmarkType;
import com.projectoracle.service.PerformanceTestGenerationService.PerformanceImpactAssessment;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for performance test generation endpoints.
 */
@RestController
@RequestMapping("/api/v1/performance-tests")
@Slf4j
@RequiredArgsConstructor
public class PerformanceTestController {

    private final PerformanceTestGenerationService performanceTestService;
    private final TestCaseRepository testCaseRepository;

    /**
     * Generate a performance test for a specific method
     * 
     * @param request The generation request
     * @return Generated test case
     */
    @PostMapping("/generate")
    public ResponseEntity<TestCase> generatePerformanceTest(@RequestBody PerformanceTestRequest request) {
        log.info("Generating performance test for {}.{} with type {}", 
                request.getClassName(), request.getMethodName(), request.getBenchmarkType());
        
        try {
            BenchmarkType benchmarkType = parseBenchmarkType(request.getBenchmarkType());
            
            TestCase generatedTest = performanceTestService.generatePerformanceTest(
                    request.getClassName(), 
                    request.getMethodName(), 
                    benchmarkType);
            
            // Save the generated test
            testCaseRepository.save(generatedTest);
            
            return ResponseEntity.ok(generatedTest);
        } catch (Exception e) {
            log.error("Error generating performance test", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Generate performance tests for an entire class
     * 
     * @param className The class name
     * @param benchmarkTypeStr The benchmark type
     * @return List of generated test cases
     */
    @PostMapping("/generate-for-class")
    public ResponseEntity<List<TestCase>> generatePerformanceTestsForClass(
            @RequestParam String className,
            @RequestParam(defaultValue = "THROUGHPUT") String benchmarkTypeStr) {
        
        log.info("Generating performance tests for class {} with type {}", className, benchmarkTypeStr);
        
        try {
            BenchmarkType benchmarkType = parseBenchmarkType(benchmarkTypeStr);
            
            List<TestCase> generatedTests = performanceTestService.generatePerformanceTestsForClass(
                    className, benchmarkType);
            
            // Save all generated tests
            for (TestCase test : generatedTests) {
                testCaseRepository.save(test);
            }
            
            return ResponseEntity.ok(generatedTests);
        } catch (Exception e) {
            log.error("Error generating performance tests for class", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Assess performance impact of code changes
     * 
     * @param request The impact assessment request
     * @return Performance impact assessment
     */
    @PostMapping("/assess-impact")
    public ResponseEntity<PerformanceImpactAssessment> assessPerformanceImpact(
            @RequestBody ImpactAssessmentRequest request) {
        
        log.info("Assessing performance impact of code changes");
        
        try {
            PerformanceImpactAssessment assessment = performanceTestService.assessPerformanceImpact(
                    request.getOldCode(), request.getNewCode());
            
            return ResponseEntity.ok(assessment);
        } catch (Exception e) {
            log.error("Error assessing performance impact", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Parse benchmark type from string
     * 
     * @param benchmarkTypeStr Benchmark type string
     * @return BenchmarkType enum value
     */
    private BenchmarkType parseBenchmarkType(String benchmarkTypeStr) {
        try {
            return BenchmarkType.valueOf(benchmarkTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid benchmark type: {}. Using THROUGHPUT as default.", benchmarkTypeStr);
            return BenchmarkType.THROUGHPUT;
        }
    }
    
    /**
     * Performance test generation request
     */
    @Data
    public static class PerformanceTestRequest {
        private String className;
        private String methodName;
        private String benchmarkType;
    }
    
    /**
     * Performance impact assessment request
     */
    @Data
    public static class ImpactAssessmentRequest {
        private String oldCode;
        private String newCode;
    }
}