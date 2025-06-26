package com.projectoracle.service;

import com.projectoracle.model.TestCase;
import com.projectoracle.model.UserFlow;
import com.projectoracle.model.Page;
import com.projectoracle.model.UIComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Service for generating test cases using AI models.
 * Orchestrates the test generation process by integrating
 * code analysis with AI-powered test creation.
 */
@Service
public class TestGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(TestGenerationService.class);

    @Autowired
    private AIModelService aiModelService;

    @Autowired
    private CodeAnalysisService codeAnalysisService;
    
    @Autowired
    private RuleBasedFallbackService fallbackService;
    
    @Autowired
    private com.projectoracle.config.AIConfig aiConfig;
    
    @Autowired
    private ModelStateService modelStateService;

    /**
     * Generate a test case for a specific method
     *
     * @param methodInfo information about the method to test
     * @return the generated test case
     */
    public TestCase generateTestForMethod(MethodInfo methodInfo) {
        logger.info("Generating test for method: {}", methodInfo.getSignature());

        // Construct a prompt for the AI model
        String prompt = buildPromptForMethod(methodInfo);

        // Generate test code using AI with fallback to rule-based generation
        String generatedTestCode;
        boolean usingFallback = false;
        String methodPurpose = "";
        String detailedDescription = "";
        
        try {
            // Try using AI first
            if (!modelStateService.areModelsReady()) {
                logger.warn("AI models are not initialized. Using rule-based generation immediately.");
                usingFallback = true;
                generatedTestCode = fallbackService.generateTestForMethod(methodInfo.getSignature(), methodInfo.getClassName());
                
                // Generate a meaningful description since we're using fallback
                methodPurpose = inferMethodPurpose(methodInfo);
            } else {
                generatedTestCode = aiModelService.generateText(prompt, 500);
                
                // Check if we got an error message instead of actual code
                if (generatedTestCode.startsWith("Error") || generatedTestCode.startsWith("AI model")) {
                    logger.warn("AI model returned an error, falling back to rule-based generation");
                    // If AI failed and fallback is enabled, use rule-based generation
                    if (aiConfig.isFallbackToRuleBased()) {
                        usingFallback = true;
                        generatedTestCode = fallbackService.generateTestForMethod(methodInfo.getSignature(), methodInfo.getClassName());
                        // Generate a meaningful description since we're using fallback
                        methodPurpose = inferMethodPurpose(methodInfo);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error using AI model for test generation: {}", e.getMessage());
            
            // If fallback is enabled, use rule-based generation
            if (aiConfig.isFallbackToRuleBased()) {
                logger.info("Falling back to rule-based test generation");
                usingFallback = true;
                generatedTestCode = fallbackService.generateTestForMethod(methodInfo.getSignature(), methodInfo.getClassName());
                // Generate a meaningful description since we're using fallback
                methodPurpose = inferMethodPurpose(methodInfo);
            } else {
                // If fallback is disabled, just return a placeholder
                generatedTestCode = "// Error generating test: " + e.getMessage();
            }
        }
        
        // Calculate confidence score
        double confidenceScore = 0.8;
        double knowledgeEnhancementScore = 0.0;
        
        // If we used fallback generation, confidence is lower
        if (usingFallback || generatedTestCode.contains("// This is a generic test created as a fallback")) {
            logger.info("Using fallback generation with lower confidence score");
            confidenceScore = 0.4; // Lower confidence for rule-based generation
        }
        
        // If additional context was provided, boost the confidence score
        if (methodInfo.getAdditionalContext() != null && !methodInfo.getAdditionalContext().isEmpty()) {
            knowledgeEnhancementScore = 0.15; // 15% improvement from knowledge integration
            confidenceScore += knowledgeEnhancementScore;
            if (confidenceScore > 1.0) {
                confidenceScore = 1.0;
            }
            logger.info("Knowledge integration improved confidence score by {}", knowledgeEnhancementScore);
        }
        
        // Extract knowledge sources from methodInfo
        List<com.projectoracle.model.KnowledgeSource> knowledgeSources = new ArrayList<>();
        if (methodInfo.getAdditionalContext() != null) {
            if (methodInfo.getAdditionalContext().containsKey("apiDoc")) {
                knowledgeSources.add(new com.projectoracle.model.KnowledgeSource(
                    "api", 
                    methodInfo.getAdditionalContext().get("path") != null ? 
                        methodInfo.getAdditionalContext().get("path").toString() : "",
                    "swagger", 
                    true
                ));
            }
            
            if (methodInfo.getAdditionalContext().containsKey("projectDoc")) {
                knowledgeSources.add(new com.projectoracle.model.KnowledgeSource(
                    "docs", 
                    methodInfo.getAdditionalContext().get("path") != null ? 
                        methodInfo.getAdditionalContext().get("path").toString() : "",
                    "markdown", 
                    true
                ));
            }
            
            if (methodInfo.getAdditionalContext().containsKey("testHistory")) {
                knowledgeSources.add(new com.projectoracle.model.KnowledgeSource(
                    "history", 
                    methodInfo.getAdditionalContext().get("path") != null ? 
                        methodInfo.getAdditionalContext().get("path").toString() : "",
                    "junit", 
                    true
                ));
            }
            
            if (methodInfo.getAdditionalContext().containsKey("codeComments")) {
                knowledgeSources.add(new com.projectoracle.model.KnowledgeSource(
                    "source", 
                    methodInfo.getAdditionalContext().get("path") != null ? 
                        methodInfo.getAdditionalContext().get("path").toString() : "",
                    "java", 
                    true
                ));
            }
        }

        // If we're using fallback and don't have methodPurpose yet, generate it
        if ((usingFallback || confidenceScore < 0.5) && methodPurpose.isEmpty()) {
            methodPurpose = inferMethodPurpose(methodInfo);
        }
        
        // Generate a detailed description even when using fallback
        if (detailedDescription.isEmpty()) {
            detailedDescription = generateDetailedDescription(methodInfo, methodPurpose, usingFallback);
        }
        
        // Create a better test name
        String testName = "Test" + methodInfo.getMethodName();
        if (!methodPurpose.isEmpty()) {
            testName = "Test" + methodInfo.getMethodName() + "For" + 
                        methodPurpose.substring(0, 1).toUpperCase() + methodPurpose.substring(1);
        }
        
        // Create and return a test case with improved descriptions
        return TestCase.builder()
                       .id(UUID.randomUUID())
                       .name(testName)
                       .description(detailedDescription)
                       .type(TestCase.TestType.UNIT)
                       .priority(TestCase.TestPriority.MEDIUM)
                       .status(TestCase.TestStatus.GENERATED)
                       .packageName(methodInfo.getPackageName())
                       .className("Test" + methodInfo.getClassName())
                       .methodName("test" + methodInfo.getMethodName())
                       .sourceCode(generatedTestCode)
                       .assertions(extractAssertions(generatedTestCode))
                       .createdAt(LocalDateTime.now())
                       .modifiedAt(LocalDateTime.now())
                       .confidenceScore(confidenceScore)
                       .knowledgeEnhancementScore(knowledgeEnhancementScore)
                       .knowledgeSources(knowledgeSources.isEmpty() ? null : knowledgeSources)
                       .generationPrompt(prompt)
                       .build();
    }

    /**
     * Extract assertions from generated test code
     * This is a simple implementation that looks for assert statements
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
            if (line.contains("assert") && !line.startsWith("//") && !line.startsWith("*")) {
                assertions.add(line);
            }
        }

        return assertions;
    }

    /**
     * Build a prompt for the AI model to generate a test
     *
     * @param methodInfo information about the method to test
     * @return prompt for the AI model
     */
    /**
     * Infer the purpose of a method based on its name, parameters and return type
     * 
     * @param methodInfo the method information to analyze
     * @return an inferred purpose description
     */
    private String inferMethodPurpose(MethodInfo methodInfo) {
        String methodName = methodInfo.getMethodName().toLowerCase();
        
        // Check for common method name patterns
        if (methodName.startsWith("get") || methodName.startsWith("fetch") || methodName.startsWith("retrieve")) {
            return "retrieving" + methodName.substring(3);
        } else if (methodName.startsWith("set") || methodName.startsWith("update")) {
            return "updating" + methodName.substring(3);
        } else if (methodName.startsWith("create") || methodName.startsWith("build") || methodName.startsWith("generate")) {
            return "creating" + methodName.substring(methodName.indexOf("e") + 1);
        } else if (methodName.startsWith("delete") || methodName.startsWith("remove")) {
            return "removing" + methodName.substring(methodName.indexOf("e") + 1);
        } else if (methodName.startsWith("is") || methodName.startsWith("has") || methodName.startsWith("can")) {
            return "checking" + methodName.substring(2);
        } else if (methodName.startsWith("validate") || methodName.startsWith("verify")) {
            return "validating" + methodName.substring(methodName.indexOf("e") + 1);
        } else if (methodName.startsWith("calculate") || methodName.startsWith("compute")) {
            return "calculating" + methodName.substring(methodName.indexOf("e") + 1);
        } else if (methodName.startsWith("convert") || methodName.startsWith("transform")) {
            return "converting" + methodName.substring(methodName.indexOf("t") + 1);
        } else if (methodName.startsWith("find") || methodName.startsWith("search") || methodName.startsWith("query")) {
            return "searching" + methodName.substring(methodName.indexOf("d") + 1);
        } else if (methodName.contains("process")) {
            return "processing" + methodName.substring(methodName.indexOf("process") + 7);
        }
        
        // If no pattern matched, use a generic description based on return type
        String returnType = methodInfo.getReturnType();
        if (returnType.equals("void")) {
            return "performingOperation";
        } else if (returnType.equals("boolean")) {
            return "verifyingCondition";
        } else if (returnType.equals("int") || returnType.equals("long") || returnType.equals("double") || 
                  returnType.equals("float")) {
            return "calculatingValue";
        } else if (returnType.startsWith("List") || returnType.startsWith("Set") || 
                  returnType.startsWith("Map") || returnType.startsWith("Collection")) {
            return "retrievingCollection";
        } else {
            return "manipulatingData";
        }
    }
    
    /**
     * Generate a detailed description for a test case
     * 
     * @param methodInfo the method information
     * @param methodPurpose the inferred purpose of the method
     * @param usingFallback whether we're using fallback generation
     * @return a detailed description for the test
     */
    private String generateDetailedDescription(MethodInfo methodInfo, String methodPurpose, boolean usingFallback) {
        StringBuilder description = new StringBuilder();
        
        // Start with a basic description
        description.append("Test for ").append(methodInfo.getSignature());
        
        // Add information about what the test verifies
        if (!methodPurpose.isEmpty()) {
            description.append("\n\nVerifies the method's functionality for ")
                      .append(methodPurpose.toLowerCase());
        }
        
        // Add information about the method's parameters if available
        if (methodInfo.getParameters() != null && !methodInfo.getParameters().isEmpty()) {
            description.append("\n\nTest inputs include: ");
            for (int i = 0; i < methodInfo.getParameters().size(); i++) {
                ParameterInfo param = methodInfo.getParameters().get(i);
                if (i > 0) {
                    description.append(", ");
                }
                description.append(param.getType()).append(" ").append(param.getName());
            }
        }
        
        // Add information about expected behavior based on return type
        description.append("\n\nExpected behavior: ");
        String returnType = methodInfo.getReturnType();
        if (returnType.equals("void")) {
            description.append("Method should execute without exceptions");
        } else if (returnType.equals("boolean")) {
            description.append("Method should return a boolean value indicating success or failure");
        } else if (returnType.equals("int") || returnType.equals("long") || returnType.equals("double") || 
                  returnType.equals("float")) {
            description.append("Method should return a numeric value within expected range");
        } else if (returnType.startsWith("List") || returnType.startsWith("Set") || 
                  returnType.startsWith("Map") || returnType.startsWith("Collection")) {
            description.append("Method should return a collection object with expected content");
        } else {
            description.append("Method should return a valid " + returnType + " object");
        }
        
        // Add information about exceptions if available
        if (methodInfo.getExceptions() != null && !methodInfo.getExceptions().isEmpty()) {
            description.append("\n\nException handling tests for: ");
            for (int i = 0; i < methodInfo.getExceptions().size(); i++) {
                if (i > 0) {
                    description.append(", ");
                }
                description.append(methodInfo.getExceptions().get(i));
            }
        }
        
        // Add a note about test generation method
        if (usingFallback) {
            description.append("\n\nNote: This test was generated using rule-based fallback methods. " +
                             "Additional test cases may be needed for comprehensive coverage.");
        }
        
        return description.toString();
    }
    
    private String buildPromptForMethod(MethodInfo methodInfo) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("Generate a JUnit 5 test for the following Java method:\n\n");

        // Add method signature and details
        promptBuilder.append("Package: ").append(methodInfo.getPackageName()).append("\n");
        promptBuilder.append("Class: ").append(methodInfo.getClassName()).append("\n");
        promptBuilder.append("Method signature: ").append(methodInfo.getSignature()).append("\n");
        promptBuilder.append("Return type: ").append(methodInfo.getReturnType()).append("\n");

        // Add parameters
        if (methodInfo.getParameters() != null && !methodInfo.getParameters().isEmpty()) {
            promptBuilder.append("Parameters:\n");
            for (ParameterInfo param : methodInfo.getParameters()) {
                promptBuilder.append("- ").append(param.getType()).append(" ").append(param.getName()).append("\n");
            }
        }

        // Add exceptions
        if (methodInfo.getExceptions() != null && !methodInfo.getExceptions().isEmpty()) {
            promptBuilder.append("Throws:\n");
            for (String exception : methodInfo.getExceptions()) {
                promptBuilder.append("- ").append(exception).append("\n");
            }
        }

        // Add Javadoc if available
        if (methodInfo.getJavadoc() != null && !methodInfo.getJavadoc().isEmpty()) {
            promptBuilder.append("\nJavadoc:\n").append(methodInfo.getJavadoc()).append("\n");
        }

        // Add method body if available
        if (methodInfo.getBody() != null && !methodInfo.getBody().isEmpty()) {
            promptBuilder.append("\nMethod body:\n").append(methodInfo.getBody()).append("\n");
        }
        
        // Include additional context from knowledge integration if available
        if (methodInfo.getAdditionalContext() != null && !methodInfo.getAdditionalContext().isEmpty()) {
            promptBuilder.append("\nAdditional Context from Knowledge Integration:\n");
            
            // Include API documentation information
            if (methodInfo.getAdditionalContext().containsKey("apiDoc")) {
                promptBuilder.append("\nAPI Documentation:\n");
                Object apiDoc = methodInfo.getAdditionalContext().get("apiDoc");
                promptBuilder.append(apiDoc.toString()).append("\n");
            }
            
            // Include project documentation information
            if (methodInfo.getAdditionalContext().containsKey("projectDoc")) {
                promptBuilder.append("\nProject Documentation:\n");
                Object projectDoc = methodInfo.getAdditionalContext().get("projectDoc");
                promptBuilder.append(projectDoc.toString()).append("\n");
            }
            
            // Include historical test data
            if (methodInfo.getAdditionalContext().containsKey("testHistory")) {
                promptBuilder.append("\nHistorical Test Patterns:\n");
                Object testHistory = methodInfo.getAdditionalContext().get("testHistory");
                promptBuilder.append(testHistory.toString()).append("\n");
            }
            
            // Include code comments
            if (methodInfo.getAdditionalContext().containsKey("codeComments")) {
                promptBuilder.append("\nCode Comments:\n");
                Object codeComments = methodInfo.getAdditionalContext().get("codeComments");
                promptBuilder.append(codeComments.toString()).append("\n");
            }
        }

        // Add instructions for test generation
        promptBuilder.append("\nCreate a comprehensive JUnit 5 test that:");
        promptBuilder.append("\n1. Tests the main functionality of the method");
        promptBuilder.append("\n2. Includes appropriate assertions");
        promptBuilder.append("\n3. Handles edge cases");
        promptBuilder.append("\n4. Uses mocks where appropriate");
        promptBuilder.append("\n5. Has good test method names following the convention testMethodName_scenario_expectedBehavior");
        
        // Add additional instructions based on knowledge integration
        if (methodInfo.getAdditionalContext() != null && !methodInfo.getAdditionalContext().isEmpty()) {
            promptBuilder.append("\n6. Incorporates insights from the additional context provided");
            promptBuilder.append("\n7. Uses realistic test data based on the documentation");
            promptBuilder.append("\n8. Follows established patterns from historical tests");
            promptBuilder.append("\n9. Addresses all requirements mentioned in the documentation");
        }

        return promptBuilder.toString();
    }
    
    /**
     * Generate a test case from a user flow or sequence of flows
     * 
     * @param flows The user flows to generate a test for
     * @param testPrefix Prefix for test name and class
     * @param journeyName Name of the journey (for description)
     * @param includeSetup Whether to include setup code
     * @return A test case for the flow
     */
    public TestCase generateFlowTest(List<UserFlow> flows, String testPrefix, String journeyName, boolean includeSetup) {
        logger.info("Generating test for flow journey: {}", journeyName);
        
        if (flows == null || flows.isEmpty()) {
            logger.warn("No flows provided for test generation");
            return null;
        }
        
        // Generate test code
        String testCode = generateFlowTestCode(flows, testPrefix, includeSetup);
        
        // Create test case object
        TestCase testCase = new TestCase();
        testCase.setId(UUID.randomUUID());
        testCase.setName(testPrefix + "_" + sanitizeForTestName(journeyName));
        testCase.setDescription("Test for user journey: " + journeyName);
        testCase.setType(TestCase.TestType.UI);
        testCase.setPriority(determinePriority(flows));
        testCase.setStatus(TestCase.TestStatus.GENERATED);
        testCase.setPackageName("com.projectoracle.test.ui.flow");
        testCase.setClassName(testPrefix + "Test");
        testCase.setMethodName("test" + sanitizeForTestName(journeyName));
        testCase.setSourceCode(testCode);
        testCase.setCreatedAt(LocalDateTime.now());
        testCase.setModifiedAt(LocalDateTime.now());
        testCase.setConfidenceScore(calculateConfidenceScore(flows));
        
        return testCase;
    }
    
    /**
     * Generate Selenium test code for a single user flow
     * 
     * @param flow The user flow to generate a test for
     * @param testMethodName The name of the test method
     * @param includeSetup Whether to include setup code
     * @return Generated test code
     */
    public String generateFlowTestCode(UserFlow flow, String testMethodName, boolean includeSetup) {
        List<UserFlow> flows = new ArrayList<>();
        flows.add(flow);
        return generateFlowTestCode(flows, testMethodName, includeSetup);
    }
    
    /**
     * Generate Selenium test code for a sequence of user flows
     * 
     * @param flows The user flows to generate a test for
     * @param testMethodName The name of the test method
     * @param includeSetup Whether to include setup code
     * @return Generated test code
     */
    public String generateFlowTestCode(List<UserFlow> flows, String testMethodName, boolean includeSetup) {
        logger.info("Generating test code for {} flows", flows.size());
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a Selenium WebDriver test in Java for the following user journey:\n\n");
        
        // Describe the user journey
        prompt.append("User Journey Description:\n");
        if (flows.size() == 1) {
            UserFlow flow = flows.get(0);
            prompt.append("- ").append(getFlowDescription(flow)).append("\n");
        } else {
            prompt.append("A multi-step journey that includes:\n");
            for (int i = 0; i < flows.size(); i++) {
                UserFlow flow = flows.get(i);
                prompt.append(i+1).append(". ").append(getFlowDescription(flow)).append("\n");
            }
        }
        
        // Add detailed flow steps
        prompt.append("\nDetailed Flow Steps:\n");
        for (int i = 0; i < flows.size(); i++) {
            UserFlow flow = flows.get(i);
            prompt.append("Step ").append(i+1).append(": ");
            prompt.append(getDetailedFlowStep(flow)).append("\n");
        }
        
        // Add instructions for test generation
        prompt.append("\nGenerate a comprehensive Selenium test that:");
        prompt.append("\n1. Uses WebDriver to automate browser interactions");
        prompt.append("\n2. Includes proper setup and teardown methods");
        prompt.append("\n3. Uses explicit waits for reliability");
        prompt.append("\n4. Includes assertions to verify the flow completed successfully");
        prompt.append("\n5. Uses methods to encapsulate repetitive tasks");
        prompt.append("\n6. Has good error handling");
        
        // Add note about including setup code
        if (includeSetup) {
            prompt.append("\n7. Include WebDriver setup code and all necessary imports");
        } else {
            prompt.append("\n7. Assume WebDriver is already set up in a base class");
        }
        
        // Generate code
        String testCode;
        try {
            testCode = aiModelService.generateText(prompt.toString(), 2000);
            
            // Check if AI returned error
            if (testCode.startsWith("Error") || testCode.isEmpty()) {
                // Fall back to template-based generation
                testCode = generateTemplateBasedFlowTest(flows, testMethodName, includeSetup);
            }
        } catch (Exception e) {
            logger.error("Error generating flow test with AI", e);
            // Fall back to template-based generation
            testCode = generateTemplateBasedFlowTest(flows, testMethodName, includeSetup);
        }
        
        return testCode;
    }
    
    /**
     * Generate test code using templates when AI generation fails
     */
    private String generateTemplateBasedFlowTest(List<UserFlow> flows, String testMethodName, boolean includeSetup) {
        StringBuilder code = new StringBuilder();
        
        // Add imports
        if (includeSetup) {
            code.append("import org.junit.jupiter.api.Test;\n");
            code.append("import org.junit.jupiter.api.BeforeEach;\n");
            code.append("import org.junit.jupiter.api.AfterEach;\n");
            code.append("import static org.junit.jupiter.api.Assertions.*;\n");
            code.append("import org.openqa.selenium.WebDriver;\n");
            code.append("import org.openqa.selenium.chrome.ChromeDriver;\n");
            code.append("import org.openqa.selenium.chrome.ChromeOptions;\n");
            code.append("import org.openqa.selenium.By;\n");
            code.append("import org.openqa.selenium.WebElement;\n");
            code.append("import org.openqa.selenium.support.ui.WebDriverWait;\n");
            code.append("import org.openqa.selenium.support.ui.ExpectedConditions;\n");
            code.append("import java.time.Duration;\n\n");
        }
        
        // Begin class
        code.append("public class FlowTest {\n\n");
        
        // Add WebDriver field
        if (includeSetup) {
            code.append("    private WebDriver driver;\n");
            code.append("    private WebDriverWait wait;\n\n");
            
            // Add setup method
            code.append("    @BeforeEach\n");
            code.append("    public void setUp() {\n");
            code.append("        ChromeOptions options = new ChromeOptions();\n");
            code.append("        options.addArguments(\"--headless=new\");\n");
            code.append("        driver = new ChromeDriver(options);\n");
            code.append("        wait = new WebDriverWait(driver, Duration.ofSeconds(10));\n");
            code.append("    }\n\n");
            
            // Add teardown method
            code.append("    @AfterEach\n");
            code.append("    public void tearDown() {\n");
            code.append("        if (driver != null) {\n");
            code.append("            driver.quit();\n");
            code.append("        }\n");
            code.append("    }\n\n");
        }
        
        // Begin test method
        code.append("    @Test\n");
        code.append("    public void ").append(testMethodName).append("() {\n");
        
        // Add test implementation
        for (UserFlow flow : flows) {
            code.append(generateFlowStep(flow));
        }
        
        // Add assertion
        code.append("        // Verify the flow completed successfully\n");
        code.append("        assertTrue(driver.getTitle().contains(\"Success\") || driver.getCurrentUrl().contains(\"success\"));\n");
        
        // End method and class
        code.append("    }\n}\n");
        
        return code.toString();
    }
    
    /**
     * Generate Selenium code for a specific flow step
     */
    private String generateFlowStep(UserFlow flow) {
        StringBuilder code = new StringBuilder();
        
        // Navigate to URL
        if (flow.getSourcePage() != null) {
            code.append("        // Navigate to ").append(flow.getSourcePage().getTitle()).append("\n");
            code.append("        driver.get(\"").append(flow.getSourcePage().getUrl()).append("\");\n");
        }
        
        // Perform interaction
        code.append("        // ").append(getFlowDescription(flow)).append("\n");
        
        if ("form_submission".equals(flow.getFlowType())) {
            code.append("        // Fill in form fields\n");
            code.append("        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName(\"form\")));\n");

            if (flow.getSourcePage() != null && flow.getSourcePage().getComponents() != null) {
                for (UIComponent comp : flow.getSourcePage().getComponents()) {
                    if (!"input".equals(comp.getType()) && !"textarea".equals(comp.getType()) && !"select".equals(comp.getType())) {
                        continue;
                    }

                    String locator = comp.getElementLocator();
                    if (locator == null || locator.isEmpty()) {
                        if (comp.getId() != null && !comp.getId().isEmpty()) {
                            locator = "#" + comp.getId();
                        } else if (comp.getName() != null && !comp.getName().isEmpty()) {
                            locator = "[name='" + comp.getName() + "']";
                        } else {
                            continue;
                        }
                    }

                    code.append("        driver.findElement(By.cssSelector(\"")
                        .append(locator)
                        .append("\"))");
                    if ("checkbox".equals(comp.getSubtype())) {
                        code.append(".click();\n");
                    } else {
                        code.append(".sendKeys(\"")
                            .append(comp.generateTestValue())
                            .append("\");\n");
                    }
                }
            }

            code.append("        WebElement submitButton = driver.findElement(By.cssSelector(\"button[type='submit']\"));\n");
            code.append("        submitButton.click();\n");
        } else if ("navigation".equals(flow.getFlowType())) {
            // Add navigation code
            code.append("        // Click navigation element\n");
            if (flow.getElementSelector() != null && !flow.getElementSelector().isEmpty()) {
                code.append("        WebElement navElement = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(\"")
                      .append(flow.getElementSelector())
                      .append("\")));\n");
            } else {
                code.append("        WebElement navElement = wait.until(ExpectedConditions.elementToBeClickable(By.linkText(\"Link Text\")));\n");
            }
            code.append("        navElement.click();\n");
        } else if ("state_change".equals(flow.getFlowType())) {
            // Add state change code
            code.append("        // Trigger state change\n");
            if (flow.getElementSelector() != null && !flow.getElementSelector().isEmpty()) {
                code.append("        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(\"")
                      .append(flow.getElementSelector())
                      .append("\")));\n");
            } else {
                code.append("        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.id(\"stateChangeElement\")));\n");
            }
            code.append("        element.click();\n");
        }
        
        // Wait for navigation/action to complete
        if (flow.getTargetPage() != null) {
            code.append("        // Wait for navigation to complete\n");
            code.append("        wait.until(ExpectedConditions.urlContains(\"")
                  .append(getUrlFragment(flow.getTargetPage().getUrl()))
                  .append("\"));\n");
        } else {
            code.append("        // Wait for action to complete\n");
            code.append("        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName(\"body\")));\n");
        }
        
        code.append("\n");
        return code.toString();
    }
    
    /**
     * Get a URL fragment for assertions
     */
    private String getUrlFragment(String url) {
        // Extract a meaningful part of the URL to use in assertions
        if (url == null || url.isEmpty()) {
            return "";
        }
        
        // Remove protocol and domain
        String fragment = url;
        int domainEnd = url.indexOf("/", 8); // Skip past https://
        if (domainEnd > 0) {
            fragment = url.substring(domainEnd);
        }
        
        // If fragment is too long, just return a short part
        if (fragment.length() > 20) {
            fragment = fragment.substring(0, 20);
        }
        
        return fragment;
    }
    
    /**
     * Get a description of a flow for test generation
     */
    private String getFlowDescription(UserFlow flow) {
        if (flow == null) {
            return "Unknown flow";
        }
        
        StringBuilder description = new StringBuilder();
        
        if ("form_submission".equals(flow.getFlowType())) {
            description.append("Fill and submit form");
            if (flow.getSourcePage() != null && flow.getTargetPage() != null) {
                description.append(" from '").append(flow.getSourcePage().getTitle())
                          .append("' to '").append(flow.getTargetPage().getTitle()).append("'");
            }
        } else if ("navigation".equals(flow.getFlowType())) {
            description.append("Navigate");
            if (flow.getSourcePage() != null && flow.getTargetPage() != null) {
                description.append(" from '").append(flow.getSourcePage().getTitle())
                          .append("' to '").append(flow.getTargetPage().getTitle()).append("'");
            }
        } else if ("state_change".equals(flow.getFlowType())) {
            description.append("Change UI state");
            if (flow.getInteractionDescription() != null) {
                description.append(" by ").append(flow.getInteractionDescription());
            }
        } else {
            description.append("Perform interaction");
            if (flow.getInteractionDescription() != null) {
                description.append(": ").append(flow.getInteractionDescription());
            }
        }
        
        return description.toString();
    }
    
    /**
     * Get detailed step information for a flow
     */
    private String getDetailedFlowStep(UserFlow flow) {
        if (flow == null) {
            return "No flow details available";
        }
        
        StringBuilder details = new StringBuilder();
        
        // Source page info
        if (flow.getSourcePage() != null) {
            details.append("On page '").append(flow.getSourcePage().getTitle())
                   .append("' (").append(flow.getSourcePage().getUrl()).append(")");
        } else {
            details.append("On source page");
        }
        
        // Interaction info
        details.append(", ");
        if ("form_submission".equals(flow.getFlowType())) {
            details.append("fill out and submit a form");
            if (flow.getInteractionDescription() != null) {
                details.append(" (").append(flow.getInteractionDescription()).append(")");
            }
        } else if ("navigation".equals(flow.getFlowType())) {
            details.append("click a navigation element");
            if (flow.getInteractionDescription() != null) {
                details.append(" (").append(flow.getInteractionDescription()).append(")");
            }
        } else if ("state_change".equals(flow.getFlowType())) {
            details.append("change UI state by interaction");
            if (flow.getInteractionDescription() != null) {
                details.append(" (").append(flow.getInteractionDescription()).append(")");
            }
        } else {
            details.append("perform interaction");
            if (flow.getInteractionDescription() != null) {
                details.append(" (").append(flow.getInteractionDescription()).append(")");
            }
        }
        
        // Element selector info
        if (flow.getElementSelector() != null && !flow.getElementSelector().isEmpty()) {
            details.append(" on element '").append(flow.getElementSelector()).append("'");
        }
        
        // Target page info
        if (flow.getTargetPage() != null) {
            details.append(", which leads to page '").append(flow.getTargetPage().getTitle())
                   .append("' (").append(flow.getTargetPage().getUrl()).append(")");
        }
        
        return details.toString();
    }
    
    /**
     * Determine test priority based on flows
     */
    private TestCase.TestPriority determinePriority(List<UserFlow> flows) {
        // Find the highest priority flow
        int maxPriority = flows.stream()
                .mapToInt(UserFlow::getPriorityScore)
                .max()
                .orElse(0);
        
        // Convert to TestCase.TestPriority
        if (maxPriority >= 80) {
            return TestCase.TestPriority.CRITICAL;
        } else if (maxPriority >= 60) {
            return TestCase.TestPriority.HIGH;
        } else if (maxPriority >= 40) {
            return TestCase.TestPriority.MEDIUM;
        } else {
            return TestCase.TestPriority.LOW;
        }
    }
    
    /**
     * Calculate confidence score for flow-based test
     */
    private double calculateConfidenceScore(List<UserFlow> flows) {
        // Base confidence on verified status and number of flows
        double baseConfidence = 0.7; // Start with reasonable confidence
        
        // Increase confidence for verified flows
        long verifiedCount = flows.stream().filter(UserFlow::isVerified).count();
        double verificationBonus = (double) verifiedCount / flows.size() * 0.2;
        
        // Decrease confidence for very complex journeys
        double complexityPenalty = Math.min(0.1, (flows.size() - 2) * 0.02);
        
        return Math.min(1.0, baseConfidence + verificationBonus - complexityPenalty);
    }
    
    /**
     * Sanitize a string for use in a test method name
     */
    private String sanitizeForTestName(String name) {
        if (name == null || name.isEmpty()) {
            return "Unknown";
        }
        
        // Replace non-alphanumeric chars with underscores
        String sanitized = name.replaceAll("[^a-zA-Z0-9]", "_");
        
        // Remove consecutive underscores
        sanitized = sanitized.replaceAll("_+", "_");
        
        // Ensure it starts with a letter
        if (!Character.isLetter(sanitized.charAt(0))) {
            sanitized = "Flow_" + sanitized;
        }
        
        // Limit length
        if (sanitized.length() > 30) {
            sanitized = sanitized.substring(0, 30);
        }
        
        return sanitized;
    }
}