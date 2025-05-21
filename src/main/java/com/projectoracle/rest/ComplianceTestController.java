package com.projectoracle.rest;

import com.projectoracle.model.TestCase;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.service.ComplianceTestExecutionService;
import com.projectoracle.service.ComplianceTestingService;
import com.projectoracle.service.ComplianceTestingService.ComplianceReport;
import com.projectoracle.service.ComplianceTestingService.ComplianceStandard;
import com.projectoracle.service.reporting.ComplianceReportGenerator;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for compliance testing endpoints.
 */
@RestController
@RequestMapping("/api/v1/compliance-tests")
@Slf4j
@RequiredArgsConstructor
public class ComplianceTestController {

    private final ComplianceTestingService complianceTestingService;
    private final ComplianceTestExecutionService complianceTestExecutionService;
    private final ComplianceReportGenerator complianceReportGenerator;
    private final TestCaseRepository testCaseRepository;

    /**
     * Test a class for compliance against a specific standard
     * 
     * @param className The class name to test
     * @param standard The compliance standard to test against
     * @return Compliance report with findings
     */
    @GetMapping("/test")
    public ResponseEntity<ComplianceReport> testCompliance(
            @RequestParam String className,
            @RequestParam ComplianceStandard standard) {
        
        log.info("Testing {} compliance for class: {}", standard, className);
        
        try {
            ComplianceReport report = complianceTestingService.testComplianceForClass(className, standard);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Error testing compliance: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Run a comprehensive compliance audit against multiple standards
     * 
     * @param className The class to audit
     * @param standards List of standards to test against (defaults to all if not specified)
     * @return Map of standards to compliance reports
     */
    @GetMapping("/audit")
    public ResponseEntity<Map<ComplianceStandard, ComplianceReport>> auditCompliance(
            @RequestParam String className,
            @RequestParam(required = false) List<ComplianceStandard> standards) {
        
        log.info("Running compliance audit for class: {}", className);
        
        try {
            Map<ComplianceStandard, ComplianceReport> reports = 
                complianceTestingService.auditCompliance(className, standards);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            log.error("Error running compliance audit: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Generate compliance test cases for a specific standard
     * 
     * @param className The class to generate tests for
     * @param standard The compliance standard
     * @return Generated test cases
     */
    @PostMapping("/generate")
    public ResponseEntity<List<TestCase>> generateComplianceTests(
            @RequestParam String className,
            @RequestParam ComplianceStandard standard) {
        
        log.info("Generating {} compliance tests for class: {}", standard, className);
        
        try {
            List<TestCase> tests = complianceTestingService.generateComplianceTests(className, standard);
            
            // Save generated tests
            for (TestCase test : tests) {
                testCaseRepository.save(test);
            }
            
            return ResponseEntity.ok(tests);
        } catch (Exception e) {
            log.error("Error generating compliance tests: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get a list of available compliance standards
     * 
     * @return List of compliance standards with descriptions
     */
    @GetMapping("/standards")
    public ResponseEntity<List<StandardInfo>> getComplianceStandards() {
        List<StandardInfo> standards = List.of(
            new StandardInfo(ComplianceStandard.OWASP_TOP_10),
            new StandardInfo(ComplianceStandard.PCI_DSS),
            new StandardInfo(ComplianceStandard.GDPR),
            new StandardInfo(ComplianceStandard.HIPAA),
            new StandardInfo(ComplianceStandard.SOX)
        );
        
        return ResponseEntity.ok(standards);
    }
    
    /**
     * Standard information class
     */
    @lombok.Data
    public static class StandardInfo {
        private String id;
        private String name;
        private String description;
        
        public StandardInfo(ComplianceStandard standard) {
            this.id = standard.name();
            this.name = standard.getName();
            this.description = standard.getDescription();
        }
    }
    
    /**
     * Execute a single compliance test
     * 
     * @param testId The ID of the test to execute
     * @return The executed test case
     */
    @PostMapping("/execute/{testId}")
    public ResponseEntity<TestCase> executeComplianceTest(@PathVariable String testId) {
        log.info("Executing compliance test with ID: {}", testId);
        
        try {
            TestCase result = complianceTestExecutionService.executeTest(testId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error executing compliance test: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Execute multiple compliance tests in batch
     * 
     * @param testIds List of test IDs to execute
     * @return Map of test IDs to executed test cases
     */
    @PostMapping("/execute-batch")
    public ResponseEntity<Map<String, TestCase>> executeBatch(@RequestBody List<String> testIds) {
        log.info("Executing batch of {} compliance tests", testIds.size());
        
        try {
            Map<String, TestCase> results = complianceTestExecutionService.executeBatch(testIds);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error executing compliance test batch: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Execute all compliance tests for a specific standard
     * 
     * @param className The class to test
     * @param standard The compliance standard to test against
     * @return List of executed test cases
     */
    @PostMapping("/execute-standard")
    public ResponseEntity<List<TestCase>> executeStandardTests(
            @RequestParam String className,
            @RequestParam String standard) {
        
        log.info("Executing all compliance tests for {}.{}", className, standard);
        
        try {
            List<TestCase> results = complianceTestExecutionService.executeComplianceTestsForStandard(className, standard);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error executing standard compliance tests: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Generate a PDF report for a compliance test
     * 
     * @param className The class name
     * @param standard The compliance standard
     * @return The PDF report as a downloadable resource
     */
    @GetMapping("/report")
    public ResponseEntity<Resource> generateComplianceReport(
            @RequestParam String className,
            @RequestParam ComplianceStandard standard) {
        
        log.info("Generating PDF report for {} compliance test of {}", standard, className);
        
        try {
            // Run compliance test to get the latest results
            ComplianceReport report = complianceTestingService.testComplianceForClass(className, standard);
            
            // Convert and generate PDF report
            String reportPath = complianceReportGenerator.generateComplianceReport(
                convertToGeneratorReport(report));
            
            // Return PDF as downloadable resource
            Resource resource = new FileSystemResource(reportPath);
            
            // Create appropriate headers
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=compliance_report.pdf");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
            
        } catch (Exception e) {
            log.error("Error generating compliance report: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Generate a PDF report for a compliance audit
     * 
     * @param className The class name
     * @param standards List of standards to include in the audit (defaults to all if not specified)
     * @return The PDF report as a downloadable resource
     */
    @GetMapping("/audit-report")
    public ResponseEntity<Resource> generateAuditReport(
            @RequestParam String className,
            @RequestParam(required = false) List<ComplianceStandard> standards) {
        
        log.info("Generating PDF report for compliance audit of {}", className);
        
        try {
            // Run compliance audit to get the latest results
            Map<ComplianceStandard, ComplianceReport> reports = 
                complianceTestingService.auditCompliance(className, standards);
            
            // Convert and generate PDF report
            String reportPath = complianceReportGenerator.generateComplianceAuditReport(
                convertToGeneratorReportMap(reports), className);
            
            // Return PDF as downloadable resource
            Resource resource = new FileSystemResource(reportPath);
            
            // Create appropriate headers
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=compliance_audit_report.pdf");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
            
        } catch (Exception e) {
            log.error("Error generating audit report: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Run compliance tests for multiple classes against a specific standard
     * 
     * @param classNames List of classes to test
     * @param standard The compliance standard to test against
     * @return Map of class names to compliance reports
     */
    @PostMapping("/bulk-test")
    public ResponseEntity<Map<String, ComplianceReport>> bulkComplianceTest(
            @RequestBody List<String> classNames,
            @RequestParam ComplianceStandard standard) {
        
        log.info("Running bulk compliance tests for {} classes against {}", classNames.size(), standard);
        
        if (classNames == null || classNames.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            Map<String, ComplianceReport> results = complianceTestingService.bulkComplianceTest(classNames, standard);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error running bulk compliance tests: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Run comprehensive compliance audits for multiple classes
     * 
     * @param classNames List of classes to audit
     * @param standards List of standards to include in the audit (defaults to all if not specified)
     * @return Map of class names to maps of standards to compliance reports
     */
    @PostMapping("/bulk-audit")
    public ResponseEntity<Map<String, Map<ComplianceStandard, ComplianceReport>>> bulkComplianceAudit(
            @RequestBody List<String> classNames,
            @RequestParam(required = false) List<ComplianceStandard> standards) {
        
        log.info("Running bulk compliance audit for {} classes", classNames.size());
        
        if (classNames == null || classNames.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            Map<String, Map<ComplianceStandard, ComplianceReport>> results = 
                complianceTestingService.bulkComplianceAudit(classNames, standards);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error running bulk compliance audit: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Generate bulk compliance tests for multiple classes against a specific standard
     * 
     * @param classNames List of classes to generate tests for
     * @param standard The compliance standard to test against
     * @return Map of class names to lists of generated test cases
     */
    @PostMapping("/bulk-generate")
    public ResponseEntity<Map<String, List<TestCase>>> bulkGenerateComplianceTests(
            @RequestBody List<String> classNames,
            @RequestParam ComplianceStandard standard) {
        
        log.info("Generating bulk compliance tests for {} classes against {}", classNames.size(), standard);
        
        if (classNames == null || classNames.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            Map<String, List<TestCase>> results = new HashMap<>();
            
            for (String className : classNames) {
                try {
                    List<TestCase> tests = complianceTestingService.generateComplianceTests(className, standard);
                    
                    // Save generated tests
                    for (TestCase test : tests) {
                        testCaseRepository.save(test);
                    }
                    
                    results.put(className, tests);
                } catch (Exception e) {
                    log.error("Error generating tests for class {}: {}", className, e.getMessage());
                    results.put(className, List.of());
                }
            }
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error generating bulk compliance tests: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Convert from ComplianceTestingService.ComplianceReport to ComplianceReportGenerator.ComplianceReport
     *
     * @param source The source ComplianceReport from ComplianceTestingService
     * @return The converted ComplianceReport for ComplianceReportGenerator
     */
    private ComplianceReportGenerator.ComplianceReport convertToGeneratorReport(ComplianceReport source) {
        ComplianceReportGenerator.ComplianceReport target = new ComplianceReportGenerator.ComplianceReport();
        
        // Set fields that exist in both classes
        target.setClassName(source.getClassName());
        target.setStandard(source.getStandard().name());
        target.setCompliancePercentage(source.getCompliancePercentage());
        target.setTestedAt(source.getTestedAt());
        
        // Convert violations if they exist
        if (source.getViolations() != null) {
            List<ComplianceReportGenerator.ComplianceViolation> violations = source.getViolations().stream()
                .map(this::convertToGeneratorViolation)
                .collect(Collectors.toList());
            target.setViolations(violations);
        }
        
        return target;
    }
    
    /**
     * Convert from ComplianceTestingService.ComplianceViolation to ComplianceReportGenerator.ComplianceViolation
     *
     * @param source The source ComplianceViolation from ComplianceTestingService
     * @return The converted ComplianceViolation for ComplianceReportGenerator
     */
    private ComplianceReportGenerator.ComplianceViolation convertToGeneratorViolation(
            ComplianceTestingService.ComplianceViolation source) {
        
        ComplianceReportGenerator.ComplianceViolation target = new ComplianceReportGenerator.ComplianceViolation();
        
        // Set fields that exist in both classes
        target.setRuleId(source.getRuleId());
        target.setRuleName(source.getRuleName());
        target.setSeverity(ComplianceReportGenerator.ComplianceSeverity.valueOf(source.getSeverity().name()));
        target.setClassName(source.getClassName());
        target.setMethodName(source.getMethodName());
        target.setDescription(source.getDescription());
        target.setDetectionPattern(source.getDetectionPattern());
        target.setRemediationSteps(source.getRemediationSteps());
        target.setAiAnalysis(source.getAiAnalysis());
        
        return target;
    }
    
    /**
     * Convert a map of ComplianceTestingService.ComplianceStandard to ComplianceTestingService.ComplianceReport
     * into a Map<String, ComplianceReportGenerator.ComplianceReport>
     *
     * @param sourceMap The source map from ComplianceTestingService
     * @return The converted map for ComplianceReportGenerator
     */
    private Map<String, ComplianceReportGenerator.ComplianceReport> convertToGeneratorReportMap(
            Map<ComplianceStandard, ComplianceReport> sourceMap) {
        
        Map<String, ComplianceReportGenerator.ComplianceReport> targetMap = new HashMap<>();
        
        sourceMap.forEach((standard, report) -> {
            targetMap.put(standard.name(), convertToGeneratorReport(report));
        });
        
        return targetMap;
    }
}