package com.projectoracle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.projectoracle.config.AIConfig;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

/**
 * Service that provides rule-based fallback functionality when AI models are unavailable.
 * Uses simple pattern matching and templates to generate basic tests and responses.
 */
@Service
public class RuleBasedFallbackService {

    private static final Logger logger = LoggerFactory.getLogger(RuleBasedFallbackService.class);

    @Autowired
    private AIConfig aiConfig;

    // Templates for different methods
    private static final Map<String, String> METHOD_TEMPLATES = new HashMap<>();
    static {
        // Template for String methods
        METHOD_TEMPLATES.put("String", 
                "import org.junit.jupiter.api.Test;\n" +
                "import static org.junit.jupiter.api.Assertions.*;\n\n" +
                "class {className}Test {\n\n" +
                "    @Test\n" +
                "    void test{methodName}() {\n" +
                "        // Arrange\n" +
                "        {className} instance = new {className}();\n" +
                "        String input = \"test\";\n\n" +
                "        // Act\n" +
                "        String result = instance.{methodName}(input);\n\n" +
                "        // Assert\n" +
                "        assertNotNull(result, \"Result should not be null\");\n" +
                "    }\n" +
                "}");
        
        // Template for int methods
        METHOD_TEMPLATES.put("int", 
                "import org.junit.jupiter.api.Test;\n" +
                "import static org.junit.jupiter.api.Assertions.*;\n\n" +
                "class {className}Test {\n\n" +
                "    @Test\n" +
                "    void test{methodName}() {\n" +
                "        // Arrange\n" +
                "        {className} instance = new {className}();\n" +
                "        int input = 42;\n\n" +
                "        // Act\n" +
                "        int result = instance.{methodName}(input);\n\n" +
                "        // Assert\n" +
                "        assertTrue(result >= 0, \"Result should be non-negative\");\n" +
                "    }\n" +
                "}");
        
        // Template for boolean methods
        METHOD_TEMPLATES.put("boolean", 
                "import org.junit.jupiter.api.Test;\n" +
                "import static org.junit.jupiter.api.Assertions.*;\n\n" +
                "class {className}Test {\n\n" +
                "    @Test\n" +
                "    void test{methodName}() {\n" +
                "        // Arrange\n" +
                "        {className} instance = new {className}();\n\n" +
                "        // Act\n" +
                "        boolean result = instance.{methodName}();\n\n" +
                "        // Assert\n" +
                "        // Basic assertion to verify execution\n" +
                "        assertNotNull(result);\n" +
                "    }\n" +
                "}");
        
        // Template for void methods
        METHOD_TEMPLATES.put("void", 
                "import org.junit.jupiter.api.Test;\n" +
                "import static org.junit.jupiter.api.Assertions.*;\n\n" +
                "class {className}Test {\n\n" +
                "    @Test\n" +
                "    void test{methodName}() {\n" +
                "        // Arrange\n" +
                "        {className} instance = new {className}();\n\n" +
                "        // Act & Assert\n" +
                "        // This test just verifies the method doesn't throw an exception\n" +
                "        assertDoesNotThrow(() -> instance.{methodName}());\n" +
                "    }\n" +
                "}");
        
        // Template for generic object methods
        METHOD_TEMPLATES.put("Object", 
                "import org.junit.jupiter.api.Test;\n" +
                "import static org.junit.jupiter.api.Assertions.*;\n\n" +
                "class {className}Test {\n\n" +
                "    @Test\n" +
                "    void test{methodName}() {\n" +
                "        // Arrange\n" +
                "        {className} instance = new {className}();\n\n" +
                "        // Act\n" +
                "        Object result = instance.{methodName}();\n\n" +
                "        // Assert\n" +
                "        assertNotNull(result, \"Result should not be null\");\n" +
                "    }\n" +
                "}");
    }

    /**
     * Generate a basic test for a method based on its signature.
     * Uses simple pattern matching and templates to create the test.
     *
     * @param methodSignature the method signature to generate a test for
     * @param className the class name containing the method
     * @return a generated test for the method
     */
    public String generateTestForMethod(String methodSignature, String className) {
        logger.info("Generating rule-based test for method: {}", methodSignature);
        
        try {
            // Extract method name and return type using regex
            Pattern pattern = Pattern.compile("(?:public|private|protected)?\\s+(\\w+(?:<[\\w\\s,]+>)?)\\s+(\\w+)\\s*\\(.*\\)");
            Matcher matcher = pattern.matcher(methodSignature);
            
            if (matcher.find()) {
                String returnType = matcher.group(1);
                String methodName = matcher.group(2);
                
                logger.info("Extracted return type: {}, method name: {}", returnType, methodName);
                
                // Get the appropriate template based on return type
                String template = null;
                
                if (returnType.startsWith("String") || returnType.contains("String")) {
                    template = METHOD_TEMPLATES.get("String");
                } else if (returnType.equals("int") || returnType.equals("long") || returnType.equals("double") || 
                        returnType.equals("float") || returnType.equals("short") || returnType.equals("byte")) {
                    template = METHOD_TEMPLATES.get("int");
                } else if (returnType.equals("boolean")) {
                    template = METHOD_TEMPLATES.get("boolean");
                } else if (returnType.equals("void")) {
                    template = METHOD_TEMPLATES.get("void");
                } else {
                    // Default to Object template for any other type
                    template = METHOD_TEMPLATES.get("Object");
                }
                
                // Replace placeholders in the template
                String test = template
                        .replace("{className}", className)
                        .replace("{methodName}", methodName);
                
                return test;
            } else {
                logger.warn("Could not parse method signature: {}", methodSignature);
                // Return a generic test if we couldn't parse the method
                return createGenericTest(className);
            }
        } catch (Exception e) {
            logger.error("Error generating rule-based test: {}", e.getMessage(), e);
            // Return a very basic test as a fallback
            return createGenericTest(className);
        }
    }
    
    /**
     * Create a generic test for a class if more specific parsing fails
     *
     * @param className the name of the class to test
     * @return a generic test for the class
     */
    private String createGenericTest(String className) {
        return "import org.junit.jupiter.api.Test;\n" +
               "import static org.junit.jupiter.api.Assertions.*;\n\n" +
               "class " + className + "Test {\n\n" +
               "    @Test\n" +
               "    void testBasicFunctionality() {\n" +
               "        // This is a generic test created as a fallback when AI model is unavailable\n" +
               "        " + className + " instance = new " + className + "();\n" +
               "        assertNotNull(instance);\n" +
               "    }\n" +
               "}";
    }
    
    /**
     * Generate a simple UI test for a web page
     * 
     * @param url the URL to test
     * @return a generated UI test
     */
    public String generateUITest(String url) {
        logger.info("Generating rule-based UI test for URL: {}", url);
        
        String baseTestName = urlToTestName(url);
        
        return "import org.junit.jupiter.api.Test;\n" +
               "import org.openqa.selenium.WebDriver;\n" +
               "import org.openqa.selenium.chrome.ChromeDriver;\n" +
               "import static org.junit.jupiter.api.Assertions.*;\n\n" +
               "class " + baseTestName + "Test {\n\n" +
               "    @Test\n" +
               "    void testPageLoads() {\n" +
               "        // Basic test to verify page loads\n" +
               "        WebDriver driver = new ChromeDriver();\n" +
               "        try {\n" +
               "            driver.get(\"" + url + "\");\n" +
               "            String title = driver.getTitle();\n" +
               "            assertNotNull(title, \"Page title should not be null\");\n" +
               "        } finally {\n" +
               "            driver.quit();\n" +
               "        }\n" +
               "    }\n" +
               "}";
    }
    
    /**
     * Convert a URL to a valid test name
     */
    private String urlToTestName(String url) {
        // Remove protocol
        String noProtocol = url.replaceAll("https?://", "");
        // Remove special characters and convert remaining to CamelCase
        String[] parts = noProtocol.split("[\\W_]+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)))
                      .append(part.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }
    
    /**
     * Generate a simple API test for an endpoint
     * 
     * @param endpoint the API endpoint to test
     * @return a generated API test
     */
    public String generateAPITest(String endpoint) {
        logger.info("Generating rule-based API test for endpoint: {}", endpoint);
        
        String baseTestName = endpointToTestName(endpoint);
        
        return "import org.junit.jupiter.api.Test;\n" +
               "import java.net.HttpURLConnection;\n" +
               "import java.net.URL;\n" +
               "import static org.junit.jupiter.api.Assertions.*;\n\n" +
               "class " + baseTestName + "Test {\n\n" +
               "    @Test\n" +
               "    void testEndpointConnectivity() throws Exception {\n" +
               "        // Basic test to verify endpoint connectivity\n" +
               "        URL url = new URL(\"" + endpoint + "\");\n" +
               "        HttpURLConnection connection = (HttpURLConnection) url.openConnection();\n" +
               "        connection.setRequestMethod(\"GET\");\n" +
               "        int responseCode = connection.getResponseCode();\n" +
               "        assertTrue(responseCode >= 200 && responseCode < 300, \n" +
               "                 \"Expected successful response code, got: \" + responseCode);\n" +
               "    }\n" +
               "}";
    }
    
    /**
     * Convert an API endpoint to a valid test name
     */
    private String endpointToTestName(String endpoint) {
        // Extract the last part of the path
        String[] pathParts = endpoint.split("/");
        String lastPart = pathParts.length > 0 ? pathParts[pathParts.length - 1] : "Api";
        
        // Clean up and convert to CamelCase
        String cleaned = lastPart.replaceAll("[\\W_]+", "");
        if (cleaned.isEmpty()) {
            cleaned = "Api"; // Fallback
        }
        
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1) + "Endpoint";
    }
}