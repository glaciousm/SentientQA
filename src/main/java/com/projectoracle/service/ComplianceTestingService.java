package com.projectoracle.service;

import com.projectoracle.model.TestCase;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for evaluating code against security compliance standards.
 * Supports standards like OWASP Top 10, GDPR, PCI DSS, etc.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ComplianceTestingService {

    private final AIModelService aiModelService;
    private final CodeAnalysisService codeAnalysisService;
    private final SecurityTestGenerationService securityTestService;
    
    // Map of compliance standards and their rules
    private final Map<ComplianceStandard, List<ComplianceRule>> complianceRules = initComplianceRules();
    
    /**
     * Run compliance tests for a specific standard against a class
     * 
     * @param className The class to test
     * @param standard The compliance standard to test against
     * @return Compliance report with findings
     */
    public ComplianceReport testComplianceForClass(String className, ComplianceStandard standard) {
        log.info("Testing {} compliance for class: {}", standard, className);
        
        // Get class methods
        List<MethodInfo> methods = codeAnalysisService.findMethodsByClassName(className);
        if (methods.isEmpty()) {
            log.warn("No methods found for class: {}", className);
            return createEmptyReport(className, standard);
        }
        
        // Get applicable rules for this standard
        List<ComplianceRule> rules = complianceRules.getOrDefault(standard, List.of());
        if (rules.isEmpty()) {
            log.warn("No rules defined for standard: {}", standard);
            return createEmptyReport(className, standard);
        }
        
        // Analyze each method against each applicable rule
        List<ComplianceViolation> violations = new ArrayList<>();
        
        for (MethodInfo method : methods) {
            for (ComplianceRule rule : rules) {
                if (isRuleApplicableToMethod(rule, method)) {
                    List<ComplianceViolation> methodViolations = checkComplianceForMethod(method, rule);
                    violations.addAll(methodViolations);
                }
            }
        }
        
        // Calculate compliance score
        int totalRules = rules.size() * methods.size();
        int violatedRules = violations.size();
        double compliancePercentage = totalRules > 0 
            ? 100.0 - ((double) violatedRules / totalRules * 100.0)
            : 100.0;
        
        // Build compliance report
        return ComplianceReport.builder()
                .className(className)
                .standard(standard)
                .compliancePercentage(Math.round(compliancePercentage * 10.0) / 10.0)
                .violations(violations)
                .testedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * Generate test cases to validate compliance with a standard
     * 
     * @param className The class to test
     * @param standard The compliance standard to test against
     * @return List of generated test cases
     */
    public List<TestCase> generateComplianceTests(String className, ComplianceStandard standard) {
        log.info("Generating {} compliance tests for class: {}", standard, className);
        
        // Get class methods
        List<MethodInfo> methods = codeAnalysisService.findMethodsByClassName(className);
        if (methods.isEmpty()) {
            log.warn("No methods found for class: {}", className);
            return List.of();
        }
        
        // Get applicable rules for this standard
        List<ComplianceRule> rules = complianceRules.getOrDefault(standard, List.of());
        if (rules.isEmpty()) {
            log.warn("No rules defined for standard: {}", standard);
            return List.of();
        }
        
        // Generate tests for each method against applicable rules
        List<TestCase> complianceTests = new ArrayList<>();
        
        for (MethodInfo method : methods) {
            // Find applicable rules
            List<ComplianceRule> applicableRules = rules.stream()
                    .filter(rule -> isRuleApplicableToMethod(rule, method))
                    .collect(Collectors.toList());
            
            if (!applicableRules.isEmpty()) {
                TestCase test = generateComplianceTest(method, applicableRules, standard);
                complianceTests.add(test);
            }
        }
        
        return complianceTests;
    }
    
    /**
     * Run a comprehensive compliance audit against multiple standards
     * 
     * @param className The class to audit
     * @param standards The standards to audit against (defaults to all if null)
     * @return Map of standards to compliance reports
     */
    public Map<ComplianceStandard, ComplianceReport> auditCompliance(
            String className, List<ComplianceStandard> standards) {
        log.info("Running compliance audit for class: {}", className);
        
        // Use all standards if none specified
        if (standards == null || standards.isEmpty()) {
            standards = List.of(ComplianceStandard.values());
        }
        
        // Run compliance tests for each standard
        Map<ComplianceStandard, ComplianceReport> reports = new HashMap<>();
        
        for (ComplianceStandard standard : standards) {
            ComplianceReport report = testComplianceForClass(className, standard);
            reports.put(standard, report);
        }
        
        return reports;
    }
    
    /**
     * Run compliance tests for multiple classes against a specific standard
     * 
     * @param classNames List of classes to test
     * @param standard The compliance standard to test against
     * @return Map of class names to compliance reports
     */
    public Map<String, ComplianceReport> bulkComplianceTest(
            List<String> classNames, ComplianceStandard standard) {
        log.info("Running bulk compliance tests for {} classes against {}", 
                classNames.size(), standard);
        
        Map<String, ComplianceReport> reports = new HashMap<>();
        
        for (String className : classNames) {
            try {
                ComplianceReport report = testComplianceForClass(className, standard);
                reports.put(className, report);
            } catch (Exception e) {
                log.error("Error testing compliance for class {}: {}", className, e.getMessage());
                // Create an empty report for failed tests
                reports.put(className, createEmptyReport(className, standard));
            }
        }
        
        return reports;
    }
    
    /**
     * Run comprehensive compliance audits for multiple classes against multiple standards
     * 
     * @param classNames List of classes to audit
     * @param standards The standards to audit against (defaults to all if null)
     * @return Map of class names to maps of standards to compliance reports
     */
    public Map<String, Map<ComplianceStandard, ComplianceReport>> bulkComplianceAudit(
            List<String> classNames, List<ComplianceStandard> standards) {
        log.info("Running bulk compliance audit for {} classes", classNames.size());
        
        // Use all standards if none specified
        if (standards == null || standards.isEmpty()) {
            standards = List.of(ComplianceStandard.values());
        }
        
        Map<String, Map<ComplianceStandard, ComplianceReport>> results = new HashMap<>();
        
        for (String className : classNames) {
            try {
                Map<ComplianceStandard, ComplianceReport> reports = auditCompliance(className, standards);
                results.put(className, reports);
            } catch (Exception e) {
                log.error("Error running compliance audit for class {}: {}", className, e.getMessage());
                // Create empty reports for failed audits
                Map<ComplianceStandard, ComplianceReport> emptyReports = new HashMap<>();
                for (ComplianceStandard standard : standards) {
                    emptyReports.put(standard, createEmptyReport(className, standard));
                }
                results.put(className, emptyReports);
            }
        }
        
        return results;
    }
    
    /**
     * Check if a rule applies to a method
     */
    private boolean isRuleApplicableToMethod(ComplianceRule rule, MethodInfo method) {
        // Check method visibility (some rules only apply to public methods)
        if (rule.isPublicMethodsOnly() && !method.isPublic()) {
            return false;
        }
        
        // Check if method contains relevant patterns
        if (!rule.getRelevancePatterns().isEmpty()) {
            boolean matchesPattern = false;
            String methodBody = method.getBody();
            
            if (methodBody != null) {
                for (String pattern : rule.getRelevancePatterns()) {
                    if (methodBody.matches("(?s).*" + pattern + ".*")) {
                        matchesPattern = true;
                        break;
                    }
                }
                
                if (!matchesPattern) {
                    return false;
                }
            } else {
                // If method body is null and we have relevance patterns, skip
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check a method against a compliance rule
     */
    private List<ComplianceViolation> checkComplianceForMethod(MethodInfo method, ComplianceRule rule) {
        List<ComplianceViolation> violations = new ArrayList<>();
        
        // First apply pattern-based detection
        if (method.getBody() != null) {
            for (String violationPattern : rule.getViolationPatterns()) {
                if (method.getBody().matches("(?s).*" + violationPattern + ".*")) {
                    // Found a violation
                    ComplianceViolation violation = ComplianceViolation.builder()
                            .ruleId(rule.getId())
                            .ruleName(rule.getName())
                            .severity(rule.getSeverity())
                            .className(method.getClassName())
                            .methodName(method.getMethodName())
                            .description(rule.getDescription())
                            .detectionPattern(violationPattern)
                            .remediationSteps(rule.getRemediation())
                            .build();
                    
                    violations.add(violation);
                    break; // One violation per rule is enough
                }
            }
        }
        
        // If we have AI detection enabled and no pattern matches found, use AI
        if (violations.isEmpty() && rule.isUseAiDetection()) {
            ComplianceViolation aiViolation = detectViolationWithAI(method, rule);
            if (aiViolation != null) {
                violations.add(aiViolation);
            }
        }
        
        return violations;
    }
    
    /**
     * Use AI to detect compliance violations
     */
    private ComplianceViolation detectViolationWithAI(MethodInfo method, ComplianceRule rule) {
        if (method.getBody() == null) {
            return null;
        }
        
        // Create prompt for AI analysis
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Analyze this Java method for compliance with the following security rule:\n\n");
        promptBuilder.append("Rule: ").append(rule.getName()).append("\n");
        promptBuilder.append("Description: ").append(rule.getDescription()).append("\n");
        promptBuilder.append("Compliance standard: ").append(rule.getStandard()).append("\n\n");
        
        promptBuilder.append("```java\n");
        promptBuilder.append("// Class: ").append(method.getClassName()).append("\n");
        promptBuilder.append(method.getSignature()).append(" {\n");
        promptBuilder.append(method.getBody()).append("\n");
        promptBuilder.append("}\n```\n\n");
        
        promptBuilder.append("Does this method violate the rule? Respond with 'YES' or 'NO' and provide a brief explanation.\n");
        promptBuilder.append("If YES, provide:\n");
        promptBuilder.append("1. The specific issue detected\n");
        promptBuilder.append("2. The location in the code\n");
        promptBuilder.append("3. Recommendation for fixing\n");
        
        // Get AI response
        String aiResponse = aiModelService.generateText(promptBuilder.toString(), 800);
        
        // Parse AI response
        if (aiResponse.toUpperCase().contains("YES")) {
            // Extract explanation (everything after "YES")
            String explanation = aiResponse.substring(aiResponse.toUpperCase().indexOf("YES") + 3).trim();
            
            // Create violation
            return ComplianceViolation.builder()
                    .ruleId(rule.getId())
                    .ruleName(rule.getName())
                    .severity(rule.getSeverity())
                    .className(method.getClassName())
                    .methodName(method.getMethodName())
                    .description(rule.getDescription())
                    .detectionPattern("AI-assisted detection")
                    .remediationSteps(rule.getRemediation())
                    .aiAnalysis(explanation)
                    .build();
        }
        
        return null;
    }
    
    /**
     * Generate a compliance test for a method
     */
    private TestCase generateComplianceTest(MethodInfo method, List<ComplianceRule> rules, ComplianceStandard standard) {
        // Create prompt for test generation
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Generate a test case to validate compliance with ")
                   .append(standard.name()).append(" for the following Java method:\n\n");
        
        // Add method info
        promptBuilder.append("Package: ").append(method.getPackageName()).append("\n");
        promptBuilder.append("Class: ").append(method.getClassName()).append("\n");
        promptBuilder.append("Method: ").append(method.getSignature()).append("\n\n");
        
        if (method.getBody() != null) {
            promptBuilder.append("Method body:\n").append(method.getBody()).append("\n\n");
        }
        
        // Add relevant compliance rules
        promptBuilder.append("Compliance Rules to Test:\n");
        for (ComplianceRule rule : rules) {
            promptBuilder.append("- Rule: ").append(rule.getName()).append("\n");
            promptBuilder.append("  Description: ").append(rule.getDescription()).append("\n");
            promptBuilder.append("  Standard: ").append(rule.getStandard()).append("\n");
            promptBuilder.append("  Remediation: ").append(rule.getRemediation()).append("\n\n");
        }
        
        // Add test requirements
        promptBuilder.append("Test Requirements:\n");
        promptBuilder.append("1. Create a JUnit 5 test that validates compliance with the specified rules\n");
        promptBuilder.append("2. Include tests for both positive and negative scenarios\n");
        promptBuilder.append("3. For SQL injection, test with malicious input like: \"' OR 1=1--\"\n");
        promptBuilder.append("4. For XSS, test with input like: \"<script>alert('xss')</script>\"\n");
        promptBuilder.append("5. For authorization, test with different user roles\n");
        promptBuilder.append("6. Include detailed comments explaining the compliance checks\n");
        promptBuilder.append("7. Use appropriate mocking for dependencies\n\n");
        
        promptBuilder.append("Please provide a complete JUnit 5 test that verifies this method meets ")
                   .append(standard.name()).append(" compliance standards.");
        
        // Generate test code
        String testCode = aiModelService.generateText(promptBuilder.toString(), 2000);
        
        // Create test case
        String testName = "testCompliance" + method.getMethodName() + "For" + standard.name();
        
        return TestCase.builder()
                .id(UUID.randomUUID())
                .name(testName)
                .description("Compliance test for " + method.getClassName() + "." + method.getMethodName() + 
                             " against " + standard.name() + " standard")
                .type(TestCase.TestType.SECURITY)
                .priority(TestCase.TestPriority.HIGH)
                .status(TestCase.TestStatus.GENERATED)
                .packageName(method.getPackageName() + ".compliance")
                .className("Compliance" + method.getClassName() + "Test")
                .methodName(testName)
                .sourceCode(testCode)
                .generationPrompt(promptBuilder.toString())
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create an empty compliance report
     */
    private ComplianceReport createEmptyReport(String className, ComplianceStandard standard) {
        return ComplianceReport.builder()
                .className(className)
                .standard(standard)
                .compliancePercentage(0.0)
                .violations(List.of())
                .testedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * Initialize compliance rules for different standards
     */
    private static Map<ComplianceStandard, List<ComplianceRule>> initComplianceRules() {
        Map<ComplianceStandard, List<ComplianceRule>> rules = new HashMap<>();
        
        // OWASP Top 10 Rules
        List<ComplianceRule> owaspRules = new ArrayList<>();
        
        // A1:2017 - Injection
        owaspRules.add(ComplianceRule.builder()
            .id("OWASP-A1-001")
            .name("SQL Injection Prevention")
            .description("SQL injection occurs when untrusted data is sent to an interpreter as part of a command or query")
            .standard(ComplianceStandard.OWASP_TOP_10)
            .severity(ComplianceSeverity.CRITICAL)
            .violationPatterns(List.of(
                ".*Statement.*execute.*\\+.*",
                ".*createStatement.*\\+.*",
                ".*prepareStatement.*\\+.*",
                ".*executeQuery.*\\+.*",
                ".*executeUpdate.*\\+.*"
            ))
            .relevancePatterns(List.of(
                ".*sql.*",
                ".*jdbc.*",
                ".*Statement.*",
                ".*Connection.*"
            ))
            .remediation("Use parameterized queries or prepared statements instead of string concatenation")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // A2:2017 - Broken Authentication
        owaspRules.add(ComplianceRule.builder()
            .id("OWASP-A2-001")
            .name("Secure Password Storage")
            .description("Passwords must be properly hashed and salted before storage")
            .standard(ComplianceStandard.OWASP_TOP_10)
            .severity(ComplianceSeverity.CRITICAL)
            .violationPatterns(List.of(
                ".*password.*=.*",
                ".*store.*password.*",
                ".*MD5.*password.*",
                ".*SHA1.*password.*"
            ))
            .relevancePatterns(List.of(
                ".*password.*",
                ".*credential.*",
                ".*auth.*"
            ))
            .remediation("Use strong adaptive hashing algorithms like BCrypt, Argon2, or PBKDF2")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // A3:2017 - Sensitive Data Exposure
        owaspRules.add(ComplianceRule.builder()
            .id("OWASP-A3-001")
            .name("Sensitive Data Encryption")
            .description("Sensitive data must be encrypted in transit and at rest")
            .standard(ComplianceStandard.OWASP_TOP_10)
            .severity(ComplianceSeverity.HIGH)
            .violationPatterns(List.of(
                ".*http[^s].*",
                ".*SSLv3.*",
                ".*TLSv1\\.0.*",
                ".*DES.*",
                ".*RC4.*",
                ".*MD5.*"
            ))
            .relevancePatterns(List.of(
                ".*encrypt.*",
                ".*decrypt.*",
                ".*http.*",
                ".*ssl.*",
                ".*tls.*"
            ))
            .remediation("Use strong encryption algorithms, secure protocols (TLS 1.2+), and implement proper key management")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // A7:2017 - Cross-Site Scripting (XSS)
        owaspRules.add(ComplianceRule.builder()
            .id("OWASP-A7-001")
            .name("Cross-Site Scripting Prevention")
            .description("Applications must properly escape or sanitize user input to prevent XSS attacks")
            .standard(ComplianceStandard.OWASP_TOP_10)
            .severity(ComplianceSeverity.HIGH)
            .violationPatterns(List.of(
                ".*\\.html\\(.*",
                ".*innerHTML.*=.*",
                ".*document.write.*\\(.*",
                ".*\\$\\(.*\\)\\.html\\(.*"
            ))
            .relevancePatterns(List.of(
                ".*html.*",
                ".*jsp.*",
                ".*render.*",
                ".*view.*"
            ))
            .remediation("Use context-aware escaping libraries for output encoding, implement Content Security Policy")
            .publicMethodsOnly(true)
            .useAiDetection(true)
            .build());
        
        // Add more OWASP rules...
        
        // PCI DSS Rules
        List<ComplianceRule> pciRules = new ArrayList<>();
        
        // Requirement 3: Protect stored cardholder data
        pciRules.add(ComplianceRule.builder()
            .id("PCI-DSS-3.4")
            .name("Credit Card Data Protection")
            .description("Credit card numbers must be rendered unreadable using strong cryptography")
            .standard(ComplianceStandard.PCI_DSS)
            .severity(ComplianceSeverity.CRITICAL)
            .violationPatterns(List.of(
                ".*\\d{13,19}.*", // Credit card patterns
                ".*credit.*card.*number.*=.*",
                ".*card.*number.*save.*",
                ".*PAN.*=.*",
                ".*store.*card.*"
            ))
            .relevancePatterns(List.of(
                ".*card.*",
                ".*payment.*",
                ".*credit.*",
                ".*PAN.*"
            ))
            .remediation("Use tokenization, truncation, or strong one-way hash functions with salt")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // Requirement 6: Develop and maintain secure systems and applications
        pciRules.add(ComplianceRule.builder()
            .id("PCI-DSS-6.5.1")
            .name("Injection Flaws Prevention")
            .description("Applications must prevent injection flaws, particularly SQL injection")
            .standard(ComplianceStandard.PCI_DSS)
            .severity(ComplianceSeverity.CRITICAL)
            .violationPatterns(List.of(
                ".*Statement.*execute.*\\+.*",
                ".*createStatement.*\\+.*",
                ".*prepareStatement.*\\+.*",
                ".*executeQuery.*\\+.*",
                ".*executeUpdate.*\\+.*"
            ))
            .relevancePatterns(List.of(
                ".*sql.*",
                ".*jdbc.*",
                ".*Statement.*",
                ".*Connection.*"
            ))
            .remediation("Use parameterized queries, ORM frameworks, or prepared statements")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // Add more PCI DSS rules...
        
        // GDPR Rules
        List<ComplianceRule> gdprRules = new ArrayList<>();
        
        // Article 32 - Security of processing
        gdprRules.add(ComplianceRule.builder()
            .id("GDPR-32-001")
            .name("Personal Data Encryption")
            .description("Personal data must be protected using encryption")
            .standard(ComplianceStandard.GDPR)
            .severity(ComplianceSeverity.HIGH)
            .violationPatterns(List.of(
                ".*personal.*data.*=.*",
                ".*user.*data.*store.*",
                ".*save.*user.*information.*"
            ))
            .relevancePatterns(List.of(
                ".*personal.*",
                ".*user.*",
                ".*data.*",
                ".*profile.*",
                ".*name.*",
                ".*address.*",
                ".*email.*"
            ))
            .remediation("Implement encryption for all personal data fields and secure key management")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // Add more GDPR rules...
        gdprRules.add(ComplianceRule.builder()
            .id("GDPR-25-001")
            .name("Data Protection by Design")
            .description("Systems must implement data protection by design and by default")
            .standard(ComplianceStandard.GDPR)
            .severity(ComplianceSeverity.HIGH)
            .violationPatterns(List.of(
                ".*public.*personal.*data.*",
                ".*global.*user.*data.*"
            ))
            .relevancePatterns(List.of(
                ".*user.*data.*",
                ".*personal.*",
                ".*privacy.*"
            ))
            .remediation("Implement data minimization practices, use privacy-enhancing technologies, and ensure appropriate access controls")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        gdprRules.add(ComplianceRule.builder()
            .id("GDPR-17-001")
            .name("Right to Erasure (Right to be Forgotten)")
            .description("Systems must provide mechanisms to delete all personal data upon request")
            .standard(ComplianceStandard.GDPR)
            .severity(ComplianceSeverity.HIGH)
            .violationPatterns(List.of(
                ".*permanent.*user.*record.*",
                ".*cannot.*delete.*user.*"
            ))
            .relevancePatterns(List.of(
                ".*delete.*user.*",
                ".*remove.*data.*",
                ".*erase.*"
            ))
            .remediation("Implement complete data deletion capabilities, including from backups and archives when technically feasible")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // HIPAA Rules
        List<ComplianceRule> hipaaRules = new ArrayList<>();
        
        // 164.312(a)(1) - Access Control
        hipaaRules.add(ComplianceRule.builder()
            .id("HIPAA-312a1-001")
            .name("Access Control Requirements")
            .description("Technical policies and procedures for electronic information systems must restrict access to authorized users only")
            .standard(ComplianceStandard.HIPAA)
            .severity(ComplianceSeverity.CRITICAL)
            .violationPatterns(List.of(
                ".*public.*static.*patient.*",
                ".*public.*medical.*data.*",
                ".*direct.*access.*health.*record.*"
            ))
            .relevancePatterns(List.of(
                ".*patient.*",
                ".*health.*",
                ".*medical.*",
                ".*PHI.*"
            ))
            .remediation("Implement user authentication, role-based access controls, and audit logging for all PHI access")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // 164.312(b) - Audit Controls
        hipaaRules.add(ComplianceRule.builder()
            .id("HIPAA-312b-001")
            .name("Audit Controls")
            .description("Hardware, software, and procedural mechanisms must record and examine activity in systems containing PHI")
            .standard(ComplianceStandard.HIPAA)
            .severity(ComplianceSeverity.HIGH)
            .violationPatterns(List.of(
                ".*access.*patient.*without.*log.*",
                ".*modify.*record.*without.*audit.*",
                ".*PHI.*access.*no.*trace.*"
            ))
            .relevancePatterns(List.of(
                ".*log.*",
                ".*audit.*",
                ".*trace.*",
                ".*access.*patient.*",
                ".*PHI.*"
            ))
            .remediation("Implement comprehensive audit logging for all PHI access, modification, and deletion events")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // 164.312(c)(1) - Integrity
        hipaaRules.add(ComplianceRule.builder()
            .id("HIPAA-312c1-001")
            .name("Data Integrity Protection")
            .description("Protect electronic protected health information from improper alteration or destruction")
            .standard(ComplianceStandard.HIPAA)
            .severity(ComplianceSeverity.HIGH)
            .violationPatterns(List.of(
                ".*direct.*update.*PHI.*",
                ".*unprotected.*medical.*record.*",
                ".*unrestricted.*write.*health.*data.*"
            ))
            .relevancePatterns(List.of(
                ".*health.*record.*",
                ".*medical.*data.*",
                ".*patient.*update.*",
                ".*PHI.*"
            ))
            .remediation("Implement data validation, checksums, change control procedures, and digital signatures for PHI")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // 164.312(e)(1) - Transmission Security
        hipaaRules.add(ComplianceRule.builder()
            .id("HIPAA-312e1-001")
            .name("Transmission Security")
            .description("Technical security measures must protect against unauthorized access to PHI during transmission")
            .standard(ComplianceStandard.HIPAA)
            .severity(ComplianceSeverity.CRITICAL)
            .violationPatterns(List.of(
                ".*plain.*text.*PHI.*",
                ".*http[^s].*medical.*",
                ".*unencrypted.*patient.*"
            ))
            .relevancePatterns(List.of(
                ".*transmit.*",
                ".*send.*",
                ".*receive.*",
                ".*PHI.*",
                ".*patient.*data.*"
            ))
            .remediation("Use TLS/SSL for all PHI transmission, implement end-to-end encryption, and verify the integrity of transmitted data")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // SOX Rules
        List<ComplianceRule> soxRules = new ArrayList<>();
        
        // Section 302 - Financial Reporting Controls
        soxRules.add(ComplianceRule.builder()
            .id("SOX-302-001")
            .name("Financial Reporting Integrity")
            .description("Financial systems must maintain accurate records and prevent unauthorized modifications to financial data")
            .standard(ComplianceStandard.SOX)
            .severity(ComplianceSeverity.CRITICAL)
            .violationPatterns(List.of(
                ".*direct.*update.*financial.*",
                ".*bypass.*accounting.*control.*",
                ".*modify.*financial.*without.*approval.*"
            ))
            .relevancePatterns(List.of(
                ".*financial.*",
                ".*accounting.*",
                ".*transaction.*",
                ".*ledger.*"
            ))
            .remediation("Implement approval workflows, role-based access controls, and comprehensive audit trails for all financial data modifications")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // Section 404 - Internal Controls Assessment
        soxRules.add(ComplianceRule.builder()
            .id("SOX-404-001")
            .name("Audit Trail Requirements")
            .description("Financial systems must maintain complete audit trails of all financial transactions and data changes")
            .standard(ComplianceStandard.SOX)
            .severity(ComplianceSeverity.HIGH)
            .violationPatterns(List.of(
                ".*financial.*no.*log.*",
                ".*transaction.*without.*audit.*",
                ".*accounting.*no.*trace.*"
            ))
            .relevancePatterns(List.of(
                ".*log.*",
                ".*audit.*",
                ".*trace.*",
                ".*financial.*",
                ".*accounting.*"
            ))
            .remediation("Implement comprehensive audit logging for all financial data modifications with user attribution and timestamps")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // Section 409 - Real-Time Disclosure
        soxRules.add(ComplianceRule.builder()
            .id("SOX-409-001")
            .name("Timely Information Disclosure")
            .description("Systems must be capable of capturing and reporting material events in near real-time")
            .standard(ComplianceStandard.SOX)
            .severity(ComplianceSeverity.MEDIUM)
            .violationPatterns(List.of(
                ".*delay.*financial.*report.*",
                ".*postpone.*disclosure.*",
                ".*slow.*notification.*"
            ))
            .relevancePatterns(List.of(
                ".*report.*",
                ".*notify.*",
                ".*disclose.*",
                ".*financial.*event.*"
            ))
            .remediation("Implement real-time event detection, notification systems, and automated reporting capabilities")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // Section 802 - Record Retention
        soxRules.add(ComplianceRule.builder()
            .id("SOX-802-001")
            .name("Record Retention Requirements")
            .description("Financial records and audit data must be retained for appropriate periods and protected from premature deletion")
            .standard(ComplianceStandard.SOX)
            .severity(ComplianceSeverity.HIGH)
            .violationPatterns(List.of(
                ".*delete.*financial.*record.*",
                ".*purge.*audit.*data.*",
                ".*remove.*accounting.*history.*"
            ))
            .relevancePatterns(List.of(
                ".*record.*",
                ".*retention.*",
                ".*delete.*",
                ".*purge.*",
                ".*financial.*"
            ))
            .remediation("Implement records retention policies, protect audit data from deletion, and ensure immutable storage for financial records")
            .publicMethodsOnly(false)
            .useAiDetection(true)
            .build());
        
        // Store rules by standard
        rules.put(ComplianceStandard.OWASP_TOP_10, owaspRules);
        rules.put(ComplianceStandard.PCI_DSS, pciRules);
        rules.put(ComplianceStandard.GDPR, gdprRules);
        rules.put(ComplianceStandard.HIPAA, hipaaRules);
        rules.put(ComplianceStandard.SOX, soxRules);
        
        return rules;
    }
    
    /**
     * Supported compliance standards
     */
    public enum ComplianceStandard {
        OWASP_TOP_10("OWASP Top 10", "Web application security risks"),
        PCI_DSS("PCI DSS", "Payment Card Industry Data Security Standard"),
        GDPR("GDPR", "General Data Protection Regulation"),
        HIPAA("HIPAA", "Health Insurance Portability and Accountability Act"),
        SOX("SOX", "Sarbanes-Oxley Act");
        
        private final String name;
        private final String description;
        
        ComplianceStandard(String name, String description) {
            this.name = name;
            this.description = description;
        }
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Compliance violation severity levels
     */
    public enum ComplianceSeverity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        INFORMATIONAL
    }
    
    /**
     * Compliance rule definition
     */
    @Data
    @Builder
    public static class ComplianceRule {
        private String id;
        private String name;
        private String description;
        private ComplianceStandard standard;
        private ComplianceSeverity severity;
        private List<String> violationPatterns;
        private List<String> relevancePatterns;
        private String remediation;
        private boolean publicMethodsOnly;
        private boolean useAiDetection;
    }
    
    /**
     * Compliance violation finding
     */
    @Data
    @Builder
    public static class ComplianceViolation {
        private String ruleId;
        private String ruleName;
        private ComplianceSeverity severity;
        private String className;
        private String methodName;
        private String description;
        private String detectionPattern;
        private String remediationSteps;
        private String aiAnalysis;
    }
    
    /**
     * Compliance test report
     */
    @Data
    @Builder
    public static class ComplianceReport {
        private String className;
        private ComplianceStandard standard;
        private double compliancePercentage;
        private List<ComplianceViolation> violations;
        private LocalDateTime testedAt;
    }
}