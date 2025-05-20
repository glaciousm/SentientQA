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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for generating security tests.
 * Identifies security vulnerabilities and generates tests to validate them.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityTestGenerationService {

    private final AIModelService aiModelService;
    private final CodeAnalysisService codeAnalysisService;
    
    private static final Map<SecurityVulnerabilityType, VulnerabilityPattern> VULNERABILITY_PATTERNS = initPatterns();
    
    /**
     * Initialize vulnerability detection patterns
     */
    private static Map<SecurityVulnerabilityType, VulnerabilityPattern> initPatterns() {
        Map<SecurityVulnerabilityType, VulnerabilityPattern> patterns = new HashMap<>();
        
        // SQL Injection patterns
        patterns.put(SecurityVulnerabilityType.SQL_INJECTION, new VulnerabilityPattern(
            List.of(
                ".*Statement.*execute.*\\+.*",
                ".*createStatement.*\\+.*",
                ".*prepareStatement.*\\+.*",
                ".*executeQuery.*\\+.*",
                ".*executeUpdate.*\\+.*"
            ),
            "Methods that concatenate user input into SQL queries without parameterization",
            "SQL injection vulnerability found: Unparameterized SQL queries can be exploited",
            List.of(
                "Use PreparedStatement with parameterized queries instead of string concatenation",
                "Apply input validation and sanitization",
                "Use JPA or other ORMs that handle parameterization automatically"
            )
        ));
        
        // XSS patterns
        patterns.put(SecurityVulnerabilityType.XSS, new VulnerabilityPattern(
            List.of(
                ".*setHtml.*\\(.*",
                ".*innerHTML.*=.*",
                ".*document\\.write.*\\(.*",
                ".*\\$\\(.*\\)\\.html\\(.*"
            ),
            "Methods that output unescaped user input to HTML contexts",
            "XSS vulnerability found: Unescaped output could allow script injection",
            List.of(
                "Use HTML encoding/escaping functions before outputting user data",
                "Apply Content Security Policy (CSP) headers",
                "Use template engines that automatically escape content"
            )
        ));
        
        // Path Traversal patterns
        patterns.put(SecurityVulnerabilityType.PATH_TRAVERSAL, new VulnerabilityPattern(
            List.of(
                ".*new File\\(.*\\+.*\\).*",
                ".*Paths\\.get\\(.*\\+.*\\).*",
                ".*FileInputStream\\(.*\\+.*\\).*",
                ".*FileOutputStream\\(.*\\+.*\\).*"
            ),
            "File operations with user-controlled paths",
            "Path traversal vulnerability found: Unsanitized file paths can expose sensitive files",
            List.of(
                "Validate and sanitize file paths",
                "Use canonical paths to resolve and verify file access",
                "Implement whitelist of allowed files/directories"
            )
        ));
        
        // Insecure Deserialization patterns
        patterns.put(SecurityVulnerabilityType.INSECURE_DESERIALIZATION, new VulnerabilityPattern(
            List.of(
                ".*ObjectInputStream.*readObject.*",
                ".*XMLDecoder.*",
                ".*readUnshared.*"
            ),
            "Java deserialization with no validation",
            "Insecure deserialization vulnerability found: Untrusted deserialization can lead to RCE",
            List.of(
                "Implement integrity checks for serialized data",
                "Use serialization filters (available in Java 9+)",
                "Consider safer alternatives like JSON with Jackson"
            )
        ));
        
        // CSRF patterns
        patterns.put(SecurityVulnerabilityType.CSRF, new VulnerabilityPattern(
            List.of(
                ".*@PostMapping.*(?!.*csrf).*",
                ".*@PutMapping.*(?!.*csrf).*",
                ".*@DeleteMapping.*(?!.*csrf).*"
            ),
            "State-changing operations without CSRF protection",
            "CSRF vulnerability found: Missing CSRF protection in state-changing operations",
            List.of(
                "Implement CSRF tokens and validate on state-changing operations",
                "Use SameSite cookie attribute",
                "Verify Origin/Referer headers for cross-origin requests"
            )
        ));
        
        // Weak Cryptography patterns
        patterns.put(SecurityVulnerabilityType.WEAK_CRYPTOGRAPHY, new VulnerabilityPattern(
            List.of(
                ".*DES.*",
                ".*MD5.*",
                ".*SHA-1.*",
                ".*RC4.*",
                ".*\"RSA/ECB/.*"
            ),
            "Usage of weak cryptographic algorithms",
            "Weak cryptography found: Outdated algorithms can be broken",
            List.of(
                "Use strong algorithms (AES-256, SHA-256 or better)",
                "Use standardized libraries and frameworks for cryptography",
                "Keep cryptographic implementations up to date"
            )
        ));
        
        // Logging Sensitive Data patterns
        patterns.put(SecurityVulnerabilityType.LOGGING_SENSITIVE_DATA, new VulnerabilityPattern(
            List.of(
                ".*log\\.(info|debug|warn|error).*password.*",
                ".*log\\.(info|debug|warn|error).*token.*",
                ".*log\\.(info|debug|warn|error).*secret.*",
                ".*log\\.(info|debug|warn|error).*credential.*"
            ),
            "Logging sensitive information",
            "Sensitive data logging found: Credentials or tokens may be exposed in logs",
            List.of(
                "Mask or exclude sensitive data from logs",
                "Implement proper log redaction",
                "Use secure logging frameworks with built-in data protection"
            )
        ));
        
        return patterns;
    }
    
    /**
     * Generate security tests for a method
     * 
     * @param className The class name
     * @param methodName The method name
     * @return Generated security test cases
     */
    public List<TestCase> generateSecurityTests(String className, String methodName) {
        log.info("Generating security tests for {}.{}", className, methodName);
        
        List<TestCase> securityTests = new ArrayList<>();
        
        // Get method info
        MethodInfo methodInfo = codeAnalysisService.getMethodInfo(className, methodName);
        if (methodInfo == null) {
            log.error("Method not found: {}.{}", className, methodName);
            throw new IllegalArgumentException("Method not found: " + className + "." + methodName);
        }
        
        // Scan for vulnerabilities
        List<VulnerabilityFinding> findings = scanForVulnerabilities(methodInfo);
        
        // Generate tests for each vulnerability
        for (VulnerabilityFinding finding : findings) {
            try {
                TestCase securityTest = generateSecurityTest(methodInfo, finding);
                securityTests.add(securityTest);
            } catch (Exception e) {
                log.error("Error generating security test for {} vulnerability in {}.{}: {}", 
                         finding.getVulnerabilityType(), className, methodName, e.getMessage());
            }
        }
        
        return securityTests;
    }
    
    /**
     * Scan a method for security vulnerabilities
     * 
     * @param methodInfo Method information
     * @return List of vulnerability findings
     */
    public List<VulnerabilityFinding> scanForVulnerabilities(MethodInfo methodInfo) {
        List<VulnerabilityFinding> findings = new ArrayList<>();
        
        if (methodInfo.getBody() == null) {
            return findings; // No body to scan
        }
        
        String methodBody = methodInfo.getBody();
        
        // Check each vulnerability pattern
        for (Map.Entry<SecurityVulnerabilityType, VulnerabilityPattern> entry : VULNERABILITY_PATTERNS.entrySet()) {
            SecurityVulnerabilityType vulnType = entry.getKey();
            VulnerabilityPattern pattern = entry.getValue();
            
            // Check if method matches any patterns for this vulnerability
            for (String regexPattern : pattern.getPatterns()) {
                Pattern compiledPattern = Pattern.compile(regexPattern);
                Matcher matcher = compiledPattern.matcher(methodBody);
                
                if (matcher.find()) {
                    // Found a vulnerability, create finding
                    VulnerabilityFinding finding = VulnerabilityFinding.builder()
                        .vulnerabilityType(vulnType)
                        .methodName(methodInfo.getMethodName())
                        .className(methodInfo.getClassName())
                        .description(pattern.getDescription())
                        .explanation(pattern.getExplanation())
                        .recommendations(pattern.getRecommendations())
                        .lineNumber(-1) // Would need more analysis to find exact line
                        .snippet(matcher.group(0))
                        .severity(determineSeverity(vulnType))
                        .build();
                    
                    findings.add(finding);
                    break; // Found one instance of this vulnerability type, move to next type
                }
            }
        }
        
        // Use AI to find additional vulnerabilities
        List<VulnerabilityFinding> aiFindings = scanWithAI(methodInfo);
        findings.addAll(aiFindings);
        
        return findings;
    }
    
    /**
     * Use AI to find additional vulnerabilities
     * 
     * @param methodInfo Method information
     * @return List of vulnerability findings
     */
    private List<VulnerabilityFinding> scanWithAI(MethodInfo methodInfo) {
        List<VulnerabilityFinding> findings = new ArrayList<>();
        
        if (methodInfo.getBody() == null) {
            return findings;
        }
        
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Analyze this Java method for security vulnerabilities:\n\n");
        promptBuilder.append("```java\n");
        promptBuilder.append("package ").append(methodInfo.getPackageName()).append(";\n\n");
        promptBuilder.append("// In class ").append(methodInfo.getClassName()).append("\n");
        promptBuilder.append(methodInfo.getSignature()).append(" {\n");
        promptBuilder.append(methodInfo.getBody()).append("\n");
        promptBuilder.append("}\n");
        promptBuilder.append("```\n\n");
        promptBuilder.append("Identify security vulnerabilities in the code above. ");
        promptBuilder.append("For each vulnerability found, report in this format:\n");
        promptBuilder.append("TYPE: <vulnerability type>\n");
        promptBuilder.append("DESCRIPTION: <brief description>\n");
        promptBuilder.append("SNIPPET: <relevant code snippet>\n");
        promptBuilder.append("EXPLANATION: <explanation of the issue>\n");
        promptBuilder.append("SEVERITY: <HIGH, MEDIUM, or LOW>\n\n");
        promptBuilder.append("Focus on these vulnerability types: SQL Injection, XSS, Path Traversal, ");
        promptBuilder.append("Insecure Deserialization, CSRF, Weak Cryptography, Logging Sensitive Data, ");
        promptBuilder.append("XXE, Open Redirect, Insecure Direct Object References, Security Misconfiguration.");
        
        String aiAnalysis = aiModelService.generateText(promptBuilder.toString(), 1500);
        
        // Parse AI findings
        Pattern findingPattern = Pattern.compile(
            "TYPE:\\s+(.+?)\\s*\n" +
            "DESCRIPTION:\\s+(.+?)\\s*\n" +
            "SNIPPET:\\s+(.+?)\\s*\n" +
            "EXPLANATION:\\s+(.+?)\\s*\n" +
            "SEVERITY:\\s+(HIGH|MEDIUM|LOW)",
            Pattern.DOTALL
        );
        
        Matcher matcher = findingPattern.matcher(aiAnalysis);
        while (matcher.find()) {
            try {
                String typeStr = matcher.group(1).trim();
                SecurityVulnerabilityType type = parseVulnerabilityType(typeStr);
                String description = matcher.group(2).trim();
                String snippet = matcher.group(3).trim();
                String explanation = matcher.group(4).trim();
                VulnerabilitySeverity severity = VulnerabilitySeverity.valueOf(matcher.group(5).trim());
                
                if (type != null) {
                    VulnerabilityFinding finding = VulnerabilityFinding.builder()
                        .vulnerabilityType(type)
                        .methodName(methodInfo.getMethodName())
                        .className(methodInfo.getClassName())
                        .description(description)
                        .explanation(explanation)
                        .lineNumber(-1)
                        .snippet(snippet)
                        .severity(severity)
                        .recommendations(List.of("See AI-generated suggestions in test case"))
                        .build();
                    
                    findings.add(finding);
                }
            } catch (Exception e) {
                log.warn("Error parsing AI finding: {}", e.getMessage());
            }
        }
        
        return findings;
    }
    
    /**
     * Parse vulnerability type from string
     */
    private SecurityVulnerabilityType parseVulnerabilityType(String typeStr) {
        // Try exact match
        try {
            return SecurityVulnerabilityType.valueOf(typeStr.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            // Try partial match
            for (SecurityVulnerabilityType type : SecurityVulnerabilityType.values()) {
                if (typeStr.toUpperCase().contains(type.name())) {
                    return type;
                }
            }
            
            // Special cases
            if (typeStr.toUpperCase().contains("SQL")) {
                return SecurityVulnerabilityType.SQL_INJECTION;
            } else if (typeStr.toUpperCase().contains("CROSS-SITE") || typeStr.toUpperCase().contains("XSS")) {
                return SecurityVulnerabilityType.XSS;
            } else if (typeStr.toUpperCase().contains("PATH") || typeStr.toUpperCase().contains("FILE")) {
                return SecurityVulnerabilityType.PATH_TRAVERSAL;
            } else if (typeStr.toUpperCase().contains("DESERIAL")) {
                return SecurityVulnerabilityType.INSECURE_DESERIALIZATION;
            } else if (typeStr.toUpperCase().contains("CSRF") || typeStr.toUpperCase().contains("FORGERY")) {
                return SecurityVulnerabilityType.CSRF;
            } else if (typeStr.toUpperCase().contains("CRYPTO") || typeStr.toUpperCase().contains("ENCRYPT")) {
                return SecurityVulnerabilityType.WEAK_CRYPTOGRAPHY;
            } else if (typeStr.toUpperCase().contains("LOG") || typeStr.toUpperCase().contains("SENSITIVE")) {
                return SecurityVulnerabilityType.LOGGING_SENSITIVE_DATA;
            }
            
            // Default to other
            return SecurityVulnerabilityType.OTHER;
        }
    }
    
    /**
     * Determine severity of a vulnerability
     * 
     * @param vulnType Vulnerability type
     * @return Severity level
     */
    private VulnerabilitySeverity determineSeverity(SecurityVulnerabilityType vulnType) {
        switch (vulnType) {
            case SQL_INJECTION:
            case INSECURE_DESERIALIZATION:
            case COMMAND_INJECTION:
            case XXE:
                return VulnerabilitySeverity.HIGH;
                
            case XSS:
            case PATH_TRAVERSAL:
            case CSRF:
            case WEAK_CRYPTOGRAPHY:
            case INSECURE_DIRECT_OBJECT_REFERENCES:
                return VulnerabilitySeverity.MEDIUM;
                
            case LOGGING_SENSITIVE_DATA:
            case SECURITY_MISCONFIGURATION:
            case OPEN_REDIRECT:
            default:
                return VulnerabilitySeverity.LOW;
        }
    }
    
    /**
     * Generate a security test for a vulnerability
     * 
     * @param methodInfo Method information
     * @param finding Vulnerability finding
     * @return Generated test case
     */
    private TestCase generateSecurityTest(MethodInfo methodInfo, VulnerabilityFinding finding) {
        // Build prompt for AI model
        String prompt = buildSecurityTestPrompt(methodInfo, finding);
        
        // Generate test code
        String testCode = aiModelService.generateText(prompt, 2000);
        
        // Create test case
        String testName = "test" + methodInfo.getMethodName() + 
                          "Is" + finding.getVulnerabilityType().name().toLowerCase() + "Resistant";
        
        return TestCase.builder()
                .id(UUID.randomUUID())
                .name(testName)
                .description("Security test for " + finding.getVulnerabilityType() + " vulnerability in " + 
                            methodInfo.getClassName() + "." + methodInfo.getMethodName())
                .type("Security")
                .priority(TestCase.TestPriority.HIGH)
                .status(TestCase.TestStatus.GENERATED)
                .packageName(methodInfo.getPackageName() + ".security")
                .className("Security" + methodInfo.getClassName() + "Test")
                .methodName(testName)
                .sourceCode(testCode)
                .generationPrompt(prompt)
                .assertions(extractAssertions(testCode))
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * Build prompt for security test generation
     * 
     * @param methodInfo Method information
     * @param finding Vulnerability finding
     * @return Generated prompt
     */
    private String buildSecurityTestPrompt(MethodInfo methodInfo, VulnerabilityFinding finding) {
        StringBuilder promptBuilder = new StringBuilder();
        
        promptBuilder.append("Generate a security test for a Java method with a ")
                    .append(finding.getVulnerabilityType())
                    .append(" vulnerability:\n\n");
        
        // Add method signature
        promptBuilder.append("Package: ").append(methodInfo.getPackageName()).append("\n");
        promptBuilder.append("Class: ").append(methodInfo.getClassName()).append("\n");
        promptBuilder.append("Method: ").append(methodInfo.getSignature()).append("\n\n");
        
        // Add method body if available
        if (methodInfo.getBody() != null) {
            promptBuilder.append("Method body:\n").append(methodInfo.getBody()).append("\n\n");
        }
        
        // Add vulnerability information
        promptBuilder.append("SECURITY VULNERABILITY DETAILS:\n");
        promptBuilder.append("Type: ").append(finding.getVulnerabilityType()).append("\n");
        promptBuilder.append("Description: ").append(finding.getDescription()).append("\n");
        promptBuilder.append("Explanation: ").append(finding.getExplanation()).append("\n");
        if (finding.getSnippet() != null) {
            promptBuilder.append("Vulnerable code: ").append(finding.getSnippet()).append("\n");
        }
        promptBuilder.append("Severity: ").append(finding.getSeverity()).append("\n\n");
        
        // Add recommendations if available
        if (finding.getRecommendations() != null && !finding.getRecommendations().isEmpty()) {
            promptBuilder.append("RECOMMENDATIONS:\n");
            for (String recommendation : finding.getRecommendations()) {
                promptBuilder.append("- ").append(recommendation).append("\n");
            }
            promptBuilder.append("\n");
        }
        
        // Add test requirements
        promptBuilder.append("TEST REQUIREMENTS:\n");
        promptBuilder.append("1. Create a JUnit 5 test that verifies the method is vulnerable to ")
                   .append(finding.getVulnerabilityType()).append("\n");
        promptBuilder.append("2. Demonstrate how an attacker could exploit this vulnerability\n");
        promptBuilder.append("3. Include specific malicious inputs that would trigger the vulnerability\n");
        promptBuilder.append("4. Show the expected impact or result of the vulnerability\n");
        promptBuilder.append("5. Include proper setup/teardown for the test\n");
        promptBuilder.append("6. Add necessary imports and dependencies\n");
        promptBuilder.append("7. Include detailed comments explaining the vulnerability and test approach\n");
        promptBuilder.append("8. Add assertions that verify if the vulnerability exists\n\n");
        
        // Add security testing strategy
        promptBuilder.append("SECURITY TESTING APPROACH:\n");
        switch (finding.getVulnerabilityType()) {
            case SQL_INJECTION:
                promptBuilder.append("- Use SQL injection payloads (', OR 1=1, etc.)\n");
                promptBuilder.append("- Demonstrate how to bypass authentication or extract data\n");
                promptBuilder.append("- Show both detection of vulnerability and proper fix\n");
                break;
                
            case XSS:
                promptBuilder.append("- Use script injection payloads (<script>, img src, etc.)\n");
                promptBuilder.append("- Demonstrate how user input can execute in HTML context\n");
                promptBuilder.append("- Show proper escaping/encoding techniques\n");
                break;
                
            case PATH_TRAVERSAL:
                promptBuilder.append("- Use path traversal payloads (../../etc/passwd, etc.)\n");
                promptBuilder.append("- Demonstrate how to access files outside intended directory\n");
                promptBuilder.append("- Show proper path normalization and validation\n");
                break;
                
            case INSECURE_DESERIALIZATION:
                promptBuilder.append("- Create malicious serialized object\n");
                promptBuilder.append("- Demonstrate how deserialization can lead to code execution\n");
                promptBuilder.append("- Show proper validation and integrity checking\n");
                break;
                
            case CSRF:
                promptBuilder.append("- Simulate cross-site request\n");
                promptBuilder.append("- Show how state-changing operation can be triggered without user consent\n");
                promptBuilder.append("- Demonstrate proper token validation\n");
                break;
                
            case WEAK_CRYPTOGRAPHY:
                promptBuilder.append("- Demonstrate how weak algorithms can be broken\n");
                promptBuilder.append("- Show cryptographic weaknesses (collision, predictability)\n");
                promptBuilder.append("- Implement stronger alternatives\n");
                break;
                
            case LOGGING_SENSITIVE_DATA:
                promptBuilder.append("- Show how sensitive data appears in logs\n");
                promptBuilder.append("- Demonstrate log capture and analysis\n");
                promptBuilder.append("- Implement proper log redaction\n");
                break;
                
            default:
                promptBuilder.append("- Create specific test data to trigger the vulnerability\n");
                promptBuilder.append("- Show the impact of the vulnerability\n");
                promptBuilder.append("- Demonstrate proper security controls\n");
        }
        
        promptBuilder.append("\nPlease provide the complete JUnit 5 security test class that thoroughly tests for this vulnerability.");
        
        return promptBuilder.toString();
    }
    
    /**
     * Extract assertions from a test
     */
    private List<String> extractAssertions(String testCode) {
        List<String> assertions = new ArrayList<>();
        if (testCode == null || testCode.isEmpty()) {
            return assertions;
        }
        
        // Split the code into lines
        String[] lines = testCode.split("\n");
        for (String line : lines) {
            line = line.trim();
            // Look for assertions (simple implementation)
            if ((line.contains("assert") || line.contains("Assertions.")) && 
                !line.startsWith("//") && !line.startsWith("*")) {
                assertions.add(line);
            }
        }
        
        return assertions;
    }
    
    /**
     * Analyze API security based on controller class
     * 
     * @param className The controller class name
     * @return List of API security findings
     */
    public List<ApiSecurityFinding> analyzeApiSecurity(String className) {
        log.info("Analyzing API security for controller: {}", className);
        
        List<ApiSecurityFinding> findings = new ArrayList<>();
        
        // Get methods for controller
        Map<String, MethodInfo> methodInfos = codeAnalysisService.getMethodsForClass(className);
        
        // Analyze each method
        for (Map.Entry<String, MethodInfo> entry : methodInfos.entrySet()) {
            MethodInfo methodInfo = entry.getValue();
            
            // Skip non-endpoint methods
            if (!isApiEndpoint(methodInfo)) {
                continue;
            }
            
            // Analyze endpoint security
            ApiSecurityFinding finding = analyzeEndpointSecurity(methodInfo);
            if (finding != null) {
                findings.add(finding);
            }
        }
        
        return findings;
    }
    
    /**
     * Check if a method is an API endpoint
     */
    private boolean isApiEndpoint(MethodInfo methodInfo) {
        if (methodInfo.getSignature() == null) {
            return false;
        }
        
        // Check for Spring MVC annotations
        return methodInfo.getSignature().contains("@GetMapping") ||
               methodInfo.getSignature().contains("@PostMapping") ||
               methodInfo.getSignature().contains("@PutMapping") ||
               methodInfo.getSignature().contains("@DeleteMapping") ||
               methodInfo.getSignature().contains("@RequestMapping") ||
               methodInfo.getSignature().contains("@PatchMapping");
    }
    
    /**
     * Analyze endpoint security
     */
    private ApiSecurityFinding analyzeEndpointSecurity(MethodInfo methodInfo) {
        List<ApiSecurityIssue> issues = new ArrayList<>();
        
        // Extract endpoint information
        String httpMethod = extractHttpMethod(methodInfo.getSignature());
        String path = extractEndpointPath(methodInfo.getSignature());
        
        // Check for security annotations
        boolean hasSecurityAnnotation = methodInfo.getSignature().contains("@Secured") ||
                                      methodInfo.getSignature().contains("@PreAuthorize") ||
                                      methodInfo.getSignature().contains("@RolesAllowed");
        
        // Check for validation annotations
        boolean hasValidation = methodInfo.getSignature().contains("@Valid") ||
                               methodInfo.getSignature().contains("@Validated");
        
        // Check for CSRF protection
        boolean csrfNeeded = "POST".equals(httpMethod) || "PUT".equals(httpMethod) || 
                            "DELETE".equals(httpMethod) || "PATCH".equals(httpMethod);
        boolean csrfProtected = methodInfo.getBody() != null && 
                              (methodInfo.getBody().contains("CsrfToken") || 
                               methodInfo.getBody().contains("csrf"));
        
        // Add security issues
        if (csrfNeeded && !csrfProtected) {
            issues.add(new ApiSecurityIssue(
                "Missing CSRF protection",
                "State-changing operations should be protected against CSRF attacks",
                "Implement CSRF token validation for all state-changing operations"
            ));
        }
        
        if (!hasSecurityAnnotation) {
            issues.add(new ApiSecurityIssue(
                "Missing authorization checks",
                "Endpoint does not have explicit authorization annotations",
                "Add @Secured, @PreAuthorize, or @RolesAllowed annotations to restrict access"
            ));
        }
        
        if (!hasValidation && methodInfo.getSignature().contains("@RequestBody")) {
            issues.add(new ApiSecurityIssue(
                "Missing input validation",
                "Request bodies should be validated to prevent injection attacks",
                "Add @Valid annotation to request body parameters and implement validation constraints"
            ));
        }
        
        // Check for rate limiting
        boolean hasRateLimiting = methodInfo.getSignature().contains("@RateLimit") ||
                                methodInfo.getBody() != null && methodInfo.getBody().contains("RateLimiter");
        
        if (!hasRateLimiting) {
            issues.add(new ApiSecurityIssue(
                "Missing rate limiting",
                "Endpoint could be vulnerable to brute force or DoS attacks",
                "Implement rate limiting to prevent abuse"
            ));
        }
        
        // Create finding if issues found
        if (!issues.isEmpty()) {
            return ApiSecurityFinding.builder()
                .className(methodInfo.getClassName())
                .methodName(methodInfo.getMethodName())
                .endpoint(path)
                .httpMethod(httpMethod)
                .issues(issues)
                .securityScore(calculateSecurityScore(issues))
                .build();
        }
        
        return null;
    }
    
    /**
     * Extract HTTP method from method signature
     */
    private String extractHttpMethod(String signature) {
        if (signature.contains("@GetMapping")) {
            return "GET";
        } else if (signature.contains("@PostMapping")) {
            return "POST";
        } else if (signature.contains("@PutMapping")) {
            return "PUT";
        } else if (signature.contains("@DeleteMapping")) {
            return "DELETE";
        } else if (signature.contains("@PatchMapping")) {
            return "PATCH";
        } else if (signature.contains("@RequestMapping")) {
            if (signature.contains("RequestMethod.GET")) {
                return "GET";
            } else if (signature.contains("RequestMethod.POST")) {
                return "POST";
            } else if (signature.contains("RequestMethod.PUT")) {
                return "PUT";
            } else if (signature.contains("RequestMethod.DELETE")) {
                return "DELETE";
            } else if (signature.contains("RequestMethod.PATCH")) {
                return "PATCH";
            }
        }
        
        return "UNKNOWN";
    }
    
    /**
     * Extract endpoint path from method signature
     */
    private String extractEndpointPath(String signature) {
        Pattern pattern = Pattern.compile("\\\"([^\\\"]+)\\\"");
        Matcher matcher = pattern.matcher(signature);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return "/unknown";
    }
    
    /**
     * Calculate security score based on issues
     */
    private int calculateSecurityScore(List<ApiSecurityIssue> issues) {
        // Start with 100 and subtract for each issue
        int score = 100;
        
        for (ApiSecurityIssue issue : issues) {
            if (issue.getTitle().contains("CSRF")) {
                score -= 25;
            } else if (issue.getTitle().contains("authorization")) {
                score -= 35;
            } else if (issue.getTitle().contains("validation")) {
                score -= 20;
            } else if (issue.getTitle().contains("rate limiting")) {
                score -= 15;
            } else {
                score -= 10;
            }
        }
        
        return Math.max(0, score);
    }
    
    /**
     * API Security finding data
     */
    @Data
    @Builder
    public static class ApiSecurityFinding {
        private String className;
        private String methodName;
        private String endpoint;
        private String httpMethod;
        private List<ApiSecurityIssue> issues;
        private int securityScore;
    }
    
    /**
     * API Security issue data
     */
    @Data
    public static class ApiSecurityIssue {
        private String title;
        private String description;
        private String recommendation;
        
        public ApiSecurityIssue(String title, String description, String recommendation) {
            this.title = title;
            this.description = description;
            this.recommendation = recommendation;
        }
    }
    
    /**
     * Vulnerability finding data
     */
    @Data
    @Builder
    public static class VulnerabilityFinding {
        private SecurityVulnerabilityType vulnerabilityType;
        private String className;
        private String methodName;
        private String description;
        private String explanation;
        private List<String> recommendations;
        private int lineNumber;
        private String snippet;
        private VulnerabilitySeverity severity;
    }
    
    /**
     * Vulnerability pattern for matching
     */
    private static class VulnerabilityPattern {
        private final List<String> patterns;
        private final String description;
        private final String explanation;
        private final List<String> recommendations;
        
        public VulnerabilityPattern(List<String> patterns, String description, 
                                  String explanation, List<String> recommendations) {
            this.patterns = patterns;
            this.description = description;
            this.explanation = explanation;
            this.recommendations = recommendations;
        }
        
        public List<String> getPatterns() { return patterns; }
        public String getDescription() { return description; }
        public String getExplanation() { return explanation; }
        public List<String> getRecommendations() { return recommendations; }
    }
    
    /**
     * Security vulnerability types
     */
    public enum SecurityVulnerabilityType {
        SQL_INJECTION,
        XSS,
        PATH_TRAVERSAL,
        INSECURE_DESERIALIZATION,
        CSRF,
        WEAK_CRYPTOGRAPHY,
        LOGGING_SENSITIVE_DATA,
        COMMAND_INJECTION,
        XXE,
        OPEN_REDIRECT,
        INSECURE_DIRECT_OBJECT_REFERENCES,
        SECURITY_MISCONFIGURATION,
        OTHER
    }
    
    /**
     * Vulnerability severity levels
     */
    public enum VulnerabilitySeverity {
        HIGH,
        MEDIUM,
        LOW
    }
}