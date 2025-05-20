package com.projectoracle.rest;

import com.projectoracle.model.TestCase;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.service.SecurityTestGenerationService;
import com.projectoracle.service.SecurityTestGenerationService.ApiSecurityFinding;
import com.projectoracle.service.SecurityTestGenerationService.VulnerabilityFinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for security test generation endpoints.
 */
@RestController
@RequestMapping("/api/v1/security-tests")
@Slf4j
@RequiredArgsConstructor
public class SecurityTestController {

    private final SecurityTestGenerationService securityTestService;
    private final TestCaseRepository testCaseRepository;

    /**
     * Generate security tests for a method
     * 
     * @param className The class name
     * @param methodName The method name
     * @return Generated test cases
     */
    @PostMapping("/generate")
    public ResponseEntity<List<TestCase>> generateSecurityTests(
            @RequestParam String className,
            @RequestParam String methodName) {
        
        log.info("Generating security tests for {}.{}", className, methodName);
        
        try {
            List<TestCase> securityTests = securityTestService.generateSecurityTests(className, methodName);
            
            // Save generated tests
            for (TestCase test : securityTests) {
                testCaseRepository.save(test);
            }
            
            return ResponseEntity.ok(securityTests);
        } catch (Exception e) {
            log.error("Error generating security tests", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Scan a method for security vulnerabilities
     * 
     * @param className The class name
     * @param methodName The method name
     * @return Vulnerability findings
     */
    @GetMapping("/scan-method")
    public ResponseEntity<List<VulnerabilityFinding>> scanMethodForVulnerabilities(
            @RequestParam String className,
            @RequestParam String methodName) {
        
        log.info("Scanning method {}.{} for security vulnerabilities", className, methodName);
        
        try {
            // Get method info and scan for vulnerabilities
            List<VulnerabilityFinding> findings = securityTestService.scanForVulnerabilities(
                securityTestService.codeAnalysisService.getMethodInfo(className, methodName));
            
            return ResponseEntity.ok(findings);
        } catch (Exception e) {
            log.error("Error scanning method for vulnerabilities", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Analyze API security for a controller
     * 
     * @param controllerClassName The controller class name
     * @return API security findings
     */
    @GetMapping("/analyze-api")
    public ResponseEntity<List<ApiSecurityFinding>> analyzeApiSecurity(
            @RequestParam String controllerClassName) {
        
        log.info("Analyzing API security for controller: {}", controllerClassName);
        
        try {
            List<ApiSecurityFinding> findings = securityTestService.analyzeApiSecurity(controllerClassName);
            return ResponseEntity.ok(findings);
        } catch (Exception e) {
            log.error("Error analyzing API security", e);
            return ResponseEntity.badRequest().build();
        }
    }
}