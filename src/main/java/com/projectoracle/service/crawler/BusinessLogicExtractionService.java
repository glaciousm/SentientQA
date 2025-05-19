package com.projectoracle.service.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.projectoracle.model.Page;
import com.projectoracle.model.UIComponent;
import com.projectoracle.repository.ElementRepository;
import com.projectoracle.config.CrawlerConfig;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for extracting business logic and validation rules from applications.
 * Analyzes client-side code to discover business rules.
 * Part of the Data Flow Analysis system described in the roadmap.
 */
@Service
public class BusinessLogicExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(BusinessLogicExtractionService.class);

    @Autowired
    private WebDriverSessionManager webDriverSessionManager;

    @Autowired
    private ElementRepository elementRepository;

    @Autowired
    private CrawlerConfig crawlerConfig;

    /**
     * Represents a business rule discovered in the application
     */
    public static class BusinessRule {
        private String name;
        private String description;
        private List<String> affectedFields = new ArrayList<>();
        private String ruleType;  // validation, calculation, transformation, etc.
        private String implementation;
        private String sourceLocation;  // page URL, script file, etc.
        private double confidenceScore;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<String> getAffectedFields() {
            return affectedFields;
        }

        public void addAffectedField(String field) {
            this.affectedFields.add(field);
        }

        public String getRuleType() {
            return ruleType;
        }

        public void setRuleType(String ruleType) {
            this.ruleType = ruleType;
        }

        public String getImplementation() {
            return implementation;
        }

        public void setImplementation(String implementation) {
            this.implementation = implementation;
        }

        public String getSourceLocation() {
            return sourceLocation;
        }

        public void setSourceLocation(String sourceLocation) {
            this.sourceLocation = sourceLocation;
        }

        public double getConfidenceScore() {
            return confidenceScore;
        }

        public void setConfidenceScore(double confidenceScore) {
            this.confidenceScore = confidenceScore;
        }
    }

    /**
     * Represents a validation rule
     */
    public static class ValidationRule {
        private String fieldName;
        private String condition;
        private String errorMessage;
        private String ruleType;  // required, format, range, comparison, custom
        private String implementation;

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String condition) {
            this.condition = condition;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getRuleType() {
            return ruleType;
        }

        public void setRuleType(String ruleType) {
            this.ruleType = ruleType;
        }

        public String getImplementation() {
            return implementation;
        }

        public void setImplementation(String implementation) {
            this.implementation = implementation;
        }
    }

    /**
     * Represents a calculation rule
     */
    public static class CalculationRule {
        private String name;
        private List<String> inputFields = new ArrayList<>();
        private String outputField;
        private String formula;
        private String implementation;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getInputFields() {
            return inputFields;
        }

        public void addInputField(String field) {
            this.inputFields.add(field);
        }

        public String getOutputField() {
            return outputField;
        }

        public void setOutputField(String outputField) {
            this.outputField = outputField;
        }

        public String getFormula() {
            return formula;
        }

        public void setFormula(String formula) {
            this.formula = formula;
        }

        public String getImplementation() {
            return implementation;
        }

        public void setImplementation(String implementation) {
            this.implementation = implementation;
        }
    }

    /**
     * Results of business logic extraction
     */
    public static class BusinessLogicResult {
        private List<BusinessRule> businessRules = new ArrayList<>();
        private List<ValidationRule> validationRules = new ArrayList<>();
        private List<CalculationRule> calculationRules = new ArrayList<>();
        private Map<String, List<String>> fieldDependencies = new HashMap<>();
        private String dominantValidationFramework;

        public List<BusinessRule> getBusinessRules() {
            return businessRules;
        }

        public void addBusinessRule(BusinessRule rule) {
            this.businessRules.add(rule);
        }

        public List<ValidationRule> getValidationRules() {
            return validationRules;
        }

        public void addValidationRule(ValidationRule rule) {
            this.validationRules.add(rule);
        }

        public List<CalculationRule> getCalculationRules() {
            return calculationRules;
        }

        public void addCalculationRule(CalculationRule rule) {
            this.calculationRules.add(rule);
        }

        public Map<String, List<String>> getFieldDependencies() {
            return fieldDependencies;
        }

        public void addFieldDependency(String field, String dependsOn) {
            if (!fieldDependencies.containsKey(field)) {
                fieldDependencies.put(field, new ArrayList<>());
            }
            fieldDependencies.get(field).add(dependsOn);
        }

        public String getDominantValidationFramework() {
            return dominantValidationFramework;
        }

        public void setDominantValidationFramework(String dominantValidationFramework) {
            this.dominantValidationFramework = dominantValidationFramework;
        }
    }

    /**
     * Extract business logic from a web application
     *
     * @param pages the collection of pages from the crawl
     * @return the analysis result
     */
    public BusinessLogicResult extractBusinessLogic(List<Page> pages) {
        logger.info("Extracting business logic from {} pages", pages.size());

        BusinessLogicResult result = new BusinessLogicResult();

        // Create WebDriver session
        String sessionId = webDriverSessionManager.createSession(
                WebDriverSessionManager.BrowserType.CHROME, true);
        WebDriver driver = webDriverSessionManager.getDriver(sessionId);

        try {
            // Extract validation frameworks
            String framework = detectValidationFramework(driver, pages);
            result.setDominantValidationFramework(framework);

            // Extract rules from HTML attributes
            extractRulesFromAttributes(driver, pages, result);

            // Extract rules from JavaScript
            extractRulesFromJavaScript(driver, pages, result);

            // Extract calculation rules
            extractCalculationRules(driver, pages, result);

            // Extract field dependencies
            extractFieldDependencies(driver, pages, result);

            logger.info("Business logic extraction complete. Found {} business rules, {} validation rules, {} calculation rules.",
                    result.getBusinessRules().size(),
                    result.getValidationRules().size(),
                    result.getCalculationRules().size());

            return result;
        } catch (Exception e) {
            logger.error("Error extracting business logic", e);
            return result;
        } finally {
            // Release the WebDriver session
            webDriverSessionManager.releaseSession(sessionId);
        }
    }

    /**
     * Detect the validation framework used by the application
     */
    private String detectValidationFramework(WebDriver driver, List<Page> pages) {
        Map<String, Integer> frameworkCounts = new HashMap<>();

        for (Page page : pages) {
            try {
                // Navigate to the page
                driver.get(page.getUrl());

                // Wait for page to load
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

                JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;

                // Check for common validation frameworks
                Object hasJQuery = jsExecutor.executeScript("return typeof jQuery !== 'undefined'");
                if (Boolean.TRUE.equals(hasJQuery)) {
                    Object hasValidate = jsExecutor.executeScript("return typeof jQuery.fn.validate !== 'undefined'");
                    if (Boolean.TRUE.equals(hasValidate)) {
                        incrementFrameworkCount(frameworkCounts, "jQuery Validation");
                    }
                }

                Object hasFormik = jsExecutor.executeScript("return typeof Formik !== 'undefined'");
                if (Boolean.TRUE.equals(hasFormik)) {
                    incrementFrameworkCount(frameworkCounts, "Formik");
                }

                Object hasYup = jsExecutor.executeScript("return typeof yup !== 'undefined'");
                if (Boolean.TRUE.equals(hasYup)) {
                    incrementFrameworkCount(frameworkCounts, "Yup");
                }

                Object hasVuelidate = jsExecutor.executeScript("return typeof Vuelidate !== 'undefined'");
                if (Boolean.TRUE.equals(hasVuelidate)) {
                    incrementFrameworkCount(frameworkCounts, "Vuelidate");
                }

                // Check script tags for known validation libraries
                List<WebElement> scripts = driver.findElements(By.tagName("script"));
                for (WebElement script : scripts) {
                    String src = script.getAttribute("src");
                    if (src != null) {
                        if (src.contains("validate.js")) {
                            incrementFrameworkCount(frameworkCounts, "validate.js");
                        } else if (src.contains("validator.js")) {
                            incrementFrameworkCount(frameworkCounts, "validator.js");
                        } else if (src.contains("parsley")) {
                            incrementFrameworkCount(frameworkCounts, "Parsley");
                        }
                    }
                }

                // Check for HTML5 validation
                List<WebElement> requiredInputs = driver.findElements(By.cssSelector("[required], [data-val-required]"));
                if (!requiredInputs.isEmpty()) {
                    incrementFrameworkCount(frameworkCounts, "HTML5 Validation");
                }

            } catch (Exception e) {
                logger.warn("Error detecting validation framework on page: {}", page.getUrl(), e);
            }
        }

        // Determine the most used framework
        String dominantFramework = "None detected";
        int maxCount = 0;

        for (Map.Entry<String, Integer> entry : frameworkCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominantFramework = entry.getKey();
            }
        }

        return dominantFramework;
    }

    /**
     * Helper method to increment framework count
     */
    private void incrementFrameworkCount(Map<String, Integer> counts, String framework) {
        counts.put(framework, counts.getOrDefault(framework, 0) + 1);
    }

    /**
     * Extract validation rules from HTML attributes
     */
    private void extractRulesFromAttributes(WebDriver driver, List<Page> pages, BusinessLogicResult result) {
        for (Page page : pages) {
            try {
                // Navigate to the page
                driver.get(page.getUrl());

                // Wait for page to load
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

                // Find all input elements
                List<WebElement> inputs = driver.findElements(By.cssSelector("input, select, textarea"));

                for (WebElement input : inputs) {
                    String name = input.getAttribute("name");
                    if (name == null || name.isEmpty()) {
                        continue;
                    }

                    // Extract HTML5 validation attributes
                    if (input.getAttribute("required") != null) {
                        ValidationRule rule = new ValidationRule();
                        rule.setFieldName(name);
                        rule.setRuleType("required");
                        rule.setCondition("value != null && value != ''");
                        rule.setErrorMessage("This field is required");
                        result.addValidationRule(rule);
                    }

                    String pattern = input.getAttribute("pattern");
                    if (pattern != null && !pattern.isEmpty()) {
                        ValidationRule rule = new ValidationRule();
                        rule.setFieldName(name);
                        rule.setRuleType("pattern");
                        rule.setCondition("value.match(/" + pattern + "/)");
                        rule.setImplementation("Pattern validation: " + pattern);
                        result.addValidationRule(rule);
                    }

                    String minLength = input.getAttribute("minlength");
                    if (minLength != null && !minLength.isEmpty()) {
                        ValidationRule rule = new ValidationRule();
                        rule.setFieldName(name);
                        rule.setRuleType("minlength");
                        rule.setCondition("value.length >= " + minLength);
                        rule.setErrorMessage("Minimum length: " + minLength);
                        result.addValidationRule(rule);
                    }

                    String maxLength = input.getAttribute("maxlength");
                    if (maxLength != null && !maxLength.isEmpty()) {
                        ValidationRule rule = new ValidationRule();
                        rule.setFieldName(name);
                        rule.setRuleType("maxlength");
                        rule.setCondition("value.length <= " + maxLength);
                        rule.setErrorMessage("Maximum length: " + maxLength);
                        result.addValidationRule(rule);
                    }

                    String min = input.getAttribute("min");
                    if (min != null && !min.isEmpty()) {
                        ValidationRule rule = new ValidationRule();
                        rule.setFieldName(name);
                        rule.setRuleType("min");
                        rule.setCondition("parseFloat(value) >= " + min);
                        rule.setErrorMessage("Minimum value: " + min);
                        result.addValidationRule(rule);
                    }

                    String max = input.getAttribute("max");
                    if (max != null && !max.isEmpty()) {
                        ValidationRule rule = new ValidationRule();
                        rule.setFieldName(name);
                        rule.setRuleType("max");
                        rule.setCondition("parseFloat(value) <= " + max);
                        rule.setErrorMessage("Maximum value: " + max);
                        result.addValidationRule(rule);
                    }

                    // Check for data attributes that might contain validation rules
                    String html = input.getAttribute("outerHTML");
                    extractValidationFromDataAttributes(html, name, result);
                }

            } catch (Exception e) {
                logger.warn("Error extracting rules from attributes on page: {}", page.getUrl(), e);
            }
        }
    }

    /**
     * Extract validation rules from data attributes
     */
    private void extractValidationFromDataAttributes(String html, String fieldName, BusinessLogicResult result) {
        // Match data-val-*, data-rule-*, data-validate-* attributes
        Pattern pattern = Pattern.compile("data-(val|rule|validate)-([a-zA-Z0-9]+)\\s*=\\s*[\"']([^\"']*)[\"']");
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String prefix = matcher.group(1);
            String ruleType = matcher.group(2);
            String ruleValue = matcher.group(3);

            ValidationRule rule = new ValidationRule();
            rule.setFieldName(fieldName);
            rule.setRuleType(ruleType);

            switch (ruleType.toLowerCase()) {
                case "required":
                    rule.setCondition("value != null && value != ''");
                    rule.setErrorMessage(ruleValue.isEmpty() ? "This field is required" : ruleValue);
                    break;
                case "email":
                    rule.setCondition("value.match(/^[^@]+@[^@]+\\.[^@]+$/)");
                    rule.setErrorMessage(ruleValue.isEmpty() ? "Please enter a valid email address" : ruleValue);
                    break;
                case "length":
                case "minlength":
                case "maxlength":
                    rule.setCondition(ruleType.equals("minlength") ?
                            "value.length >= " + ruleValue :
                            "value.length <= " + ruleValue);
                    rule.setErrorMessage(ruleValue.isEmpty() ?
                            ruleType.equals("minlength") ? "Minimum length requirement" : "Maximum length exceeded" :
                            ruleValue);
                    break;
                case "range":
                case "min":
                case "max":
                    rule.setCondition(ruleType.equals("min") ?
                            "parseFloat(value) >= " + ruleValue :
                            "parseFloat(value) <= " + ruleValue);
                    rule.setErrorMessage(ruleValue.isEmpty() ?
                            ruleType.equals("min") ? "Minimum value requirement" : "Maximum value exceeded" :
                            ruleValue);
                    break;
                default:
                    rule.setCondition("Custom validation: " + ruleType);
                    rule.setErrorMessage(ruleValue);
                    break;
            }

            result.addValidationRule(rule);
        }
    }

    /**
     * Extract rules from JavaScript code
     */
    private void extractRulesFromJavaScript(WebDriver driver, List<Page> pages, BusinessLogicResult result) {
        for (Page page : pages) {
            try {
                // Navigate to the page
                driver.get(page.getUrl());

                // Wait for page to load
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

                JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;

                // Extract inline scripts
                List<WebElement> scripts = driver.findElements(By.tagName("script"));
                for (WebElement script : scripts) {
                    String scriptText = script.getAttribute("innerHTML");
                    if (scriptText != null && !scriptText.isEmpty()) {
                        extractRulesFromScriptContent(scriptText, page.getUrl(), result);
                    }
                }

                // Try to extract validation configuration from common frameworks
                if ("jQuery Validation".equals(result.getDominantValidationFramework())) {
                    extractJQueryValidationRules(jsExecutor, result);
                } else if ("Formik".equals(result.getDominantValidationFramework())) {
                    extractFormikValidationRules(jsExecutor, result);
                }

            } catch (Exception e) {
                logger.warn("Error extracting rules from JavaScript on page: {}", page.getUrl(), e);
            }
        }
    }

    /**
     * Extract validation rules from a script content
     */
    private void extractRulesFromScriptContent(String script, String pageUrl, BusinessLogicResult result) {
        // Look for common validation patterns

        // Example: if (value.length < 8) { ... error message ... }
        extractConditionalValidations(script, pageUrl, result);

        // Look for regular expressions
        extractRegexValidations(script, pageUrl, result);

        // Look for business logic functions
        extractBusinessFunctions(script, pageUrl, result);
    }

    /**
     * Extract validation rules based on conditional statements
     */
    private void extractConditionalValidations(String script, String pageUrl, BusinessLogicResult result) {
        // Pattern for if statements checking form fields
        // Example: if (field.value === "") { showError(...) }
        Pattern ifPattern = Pattern.compile("if\\s*\\(([^)]+)\\)\\s*\\{([^}]+)\\}", Pattern.MULTILINE);
        Matcher ifMatcher = ifPattern.matcher(script);

        while (ifMatcher.find()) {
            String condition = ifMatcher.group(1).trim();
            String body = ifMatcher.group(2).trim();

            // Now parse the condition to see if it's a validation rule
            // Look for field names
            Pattern fieldPattern = Pattern.compile("([a-zA-Z0-9_]+)\\.value|document\\.getElementById\\(['\"]([^'\"]+)['\"]\\)\\s*\\.value|\\$\\(['\"]#([^'\"]+)['\"]\\)");
            Matcher fieldMatcher = fieldPattern.matcher(condition);

            if (fieldMatcher.find()) {
                String fieldName = fieldMatcher.group(1);
                if (fieldName == null) fieldName = fieldMatcher.group(2);
                if (fieldName == null) fieldName = fieldMatcher.group(3);

                if (fieldName != null) {
                    // Look for error messages in the body
                    Pattern errorPattern = Pattern.compile("error|invalid|alert\\(|message|\\$\\('[^']*'\\)\\.text\\(|innerHTML");
                    Matcher errorMatcher = errorPattern.matcher(body);

                    if (errorMatcher.find()) {
                        // This looks like a validation rule
                        ValidationRule rule = new ValidationRule();
                        rule.setFieldName(fieldName);
                        rule.setCondition(condition);
                        rule.setImplementation("if (" + condition + ") { " + body + " }");
                        rule.setRuleType("custom");

                        // Try to extract error message
                        Pattern messagePatt = Pattern.compile("['\"](.*?)['\"]");
                        Matcher messageMatcher = messagePatt.matcher(body);
                        if (messageMatcher.find()) {
                            rule.setErrorMessage(messageMatcher.group(1));
                        } else {
                            rule.setErrorMessage("Validation failed");
                        }

                        result.addValidationRule(rule);
                    }
                }
            }
        }
    }

    /**
     * Extract validation rules based on regular expressions
     */
    private void extractRegexValidations(String script, String pageUrl, BusinessLogicResult result) {
        // Pattern for regex definitions
        // Example: var emailRegex = /^[^@]+@[^@]+\.[^@]+$/;
        Pattern regexPattern = Pattern.compile("(?:var|let|const)\\s+([a-zA-Z0-9_]+)\\s*=\\s*/(.*?)/([gimuy]*)\\s*;", Pattern.MULTILINE);
        Matcher regexMatcher = regexPattern.matcher(script);

        Map<String, String> regexDefinitions = new HashMap<>();

        // First collect all regex definitions
        while (regexMatcher.find()) {
            String regexName = regexMatcher.group(1);
            String regexValue = regexMatcher.group(2);
            String regexFlags = regexMatcher.group(3);

            regexDefinitions.put(regexName, "/" + regexValue + "/" + regexFlags);
        }

        // Now look for usage of these regexes in validation context
        for (Map.Entry<String, String> regexEntry : regexDefinitions.entrySet()) {
            String regexName = regexEntry.getKey();
            String regexValue = regexEntry.getValue();

            // Look for places where this regex is used with test() or match() methods
            Pattern usagePattern = Pattern.compile(regexName + "\\.test\\(([^)]+)\\)|([^)]+)\\.match\\(" + regexName + "\\)");
            Matcher usageMatcher = usagePattern.matcher(script);

            while (usageMatcher.find()) {
                String testedValue = usageMatcher.group(1);
                if (testedValue == null) testedValue = usageMatcher.group(2);

                // Extract field name from the tested value
                Pattern fieldPattern = Pattern.compile("([a-zA-Z0-9_]+)\\.value|document\\.getElementById\\(['\"]([^'\"]+)['\"]\\)\\s*\\.value|\\$\\(['\"]#([^'\"]+)['\"]\\)");
                Matcher fieldMatcher = fieldPattern.matcher(testedValue);

                if (fieldMatcher.find()) {
                    String fieldName = fieldMatcher.group(1);
                    if (fieldName == null) fieldName = fieldMatcher.group(2);
                    if (fieldName == null) fieldName = fieldMatcher.group(3);

                    if (fieldName != null) {
                        // This is likely a validation rule
                        ValidationRule rule = new ValidationRule();
                        rule.setFieldName(fieldName);
                        rule.setRuleType("pattern");
                        rule.setCondition(regexName + ".test(" + testedValue + ")");
                        rule.setImplementation("Regex validation: " + regexValue);

                        // Guess the rule purpose based on regex name and content
                        if (regexName.toLowerCase().contains("email")) {
                            rule.setErrorMessage("Please enter a valid email address");
                        } else if (regexName.toLowerCase().contains("phone")) {
                            rule.setErrorMessage("Please enter a valid phone number");
                        } else if (regexName.toLowerCase().contains("zip") || regexName.toLowerCase().contains("postal")) {
                            rule.setErrorMessage("Please enter a valid postal code");
                        } else if (regexName.toLowerCase().contains("url")) {
                            rule.setErrorMessage("Please enter a valid URL");
                        } else if (regexName.toLowerCase().contains("password")) {
                            rule.setErrorMessage("Password does not meet requirements");
                        } else {
                            rule.setErrorMessage("Invalid format");
                        }

                        result.addValidationRule(rule);
                    }
                }
            }
        }
    }

    /**
     * Extract business logic functions
     */
    private void extractBusinessFunctions(String script, String pageUrl, BusinessLogicResult result) {
        // Pattern for function declarations
        // Example: function calculateTotal() { ... }
        Pattern funcPattern = Pattern.compile("function\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)\\s*\\{([^}]+)\\}", Pattern.MULTILINE);
        Matcher funcMatcher = funcPattern.matcher(script);

        while (funcMatcher.find()) {
            String functionName = funcMatcher.group(1);
            String parameters = funcMatcher.group(2);
            String body = funcMatcher.group(3);

            // Check if the function is doing calculations
            if (isCalculationFunction(functionName, body)) {
                extractCalculationRule(functionName, parameters, body, pageUrl, result);
            }
            // Check if the function is doing validations
            else if (isValidationFunction(functionName, body)) {
                extractValidationFunction(functionName, parameters, body, pageUrl, result);
            }
            // Check if the function contains more general business logic
            else if (isBusinessLogicFunction(functionName, body)) {
                extractBusinessRule(functionName, parameters, body, pageUrl, result);
            }
        }
    }

    /**
     * Check if a function appears to do calculations
     */
    private boolean isCalculationFunction(String name, String body) {
        String lowerName = name.toLowerCase();
        return (lowerName.contains("calculat") || lowerName.contains("comput") ||
                lowerName.contains("total") || lowerName.contains("sum") ||
                lowerName.contains("amount") || lowerName.contains("price")) &&
                (body.contains("+") || body.contains("-") || body.contains("*") ||
                        body.contains("/") || body.contains("Math."));
    }

    /**
     * Check if a function appears to do validations
     */
    private boolean isValidationFunction(String name, String body) {
        String lowerName = name.toLowerCase();
        return (lowerName.contains("validat") || lowerName.contains("check") ||
                lowerName.contains("verify") || lowerName.contains("isValid")) &&
                (body.contains("if") || body.contains("return") || body.contains("error"));
    }

    /**
     * Check if a function appears to contain business logic
     */
    private boolean isBusinessLogicFunction(String name, String body) {
        String lowerName = name.toLowerCase();
        return (lowerName.contains("process") || lowerName.contains("handle") ||
                lowerName.contains("update") || lowerName.contains("apply") ||
                lowerName.contains("rule") || lowerName.contains("policy")) &&
                (body.contains("if") || body.contains("for") || body.contains("switch"));
    }

    /**
     * Extract a calculation rule from a function
     */
    private void extractCalculationRule(String name, String parameters, String body, String pageUrl, BusinessLogicResult result) {
        CalculationRule rule = new CalculationRule();
        rule.setName(name);

        // Parse parameters as input fields
        String[] params = parameters.split(",");
        for (String param : params) {
            param = param.trim();
            if (!param.isEmpty()) {
                rule.addInputField(param);
            }
        }

        // Try to identify the output field
        Pattern returnPattern = Pattern.compile("return\\s+([^;]+)");
        Matcher returnMatcher = returnPattern.matcher(body);
        if (returnMatcher.find()) {
            rule.setFormula(returnMatcher.group(1).trim());
        }

        // Check for assignments to DOM elements
        Pattern assignPattern = Pattern.compile("(document\\.getElementById\\(['\"]([^'\"]+)['\"]\\)|\\$\\(['\"]#([^'\"]+)['\"]\\))\\s*\\.(?:value|innerText|innerHTML)\\s*=\\s*([^;]+)");
        Matcher assignMatcher = assignPattern.matcher(body);
        if (assignMatcher.find()) {
            String elementId = assignMatcher.group(2);
            if (elementId == null) elementId = assignMatcher.group(3);
            rule.setOutputField(elementId);
        }

        rule.setImplementation(body);

        result.addCalculationRule(rule);
    }

    /**
     * Extract a validation rule from a function
     */
    private void extractValidationFunction(String name, String parameters, String body, String pageUrl, BusinessLogicResult result) {
        ValidationRule rule = new ValidationRule();

        // Parse parameters to identify field name
        String[] params = parameters.split(",");
        if (params.length > 0) {
            String fieldParam = params[0].trim();
            // Field might be passed as parameter, or function might work on a specific field
            if (!fieldParam.isEmpty()) {
                rule.setFieldName(fieldParam);
            } else {
                // Try to extract field name from the body
                Pattern fieldPattern = Pattern.compile("document\\.getElementById\\(['\"]([^'\"]+)['\"]\\)|\\$\\(['\"]#([^'\"]+)['\"]\\)");
                Matcher fieldMatcher = fieldPattern.matcher(body);
                if (fieldMatcher.find()) {
                    String fieldName = fieldMatcher.group(1);
                    if (fieldName == null) fieldName = fieldMatcher.group(2);
                    rule.setFieldName(fieldName);
                }
            }
        }

        // Try to extract the condition
        Pattern ifPattern = Pattern.compile("if\\s*\\(([^)]+)\\)");
        Matcher ifMatcher = ifPattern.matcher(body);
        if (ifMatcher.find()) {
            rule.setCondition(ifMatcher.group(1).trim());
        }

        // Try to extract error message
        Pattern errorPattern = Pattern.compile("['\"](.*?)['\"]");
        Matcher errorMatcher = errorPattern.matcher(body);
        if (errorMatcher.find()) {
            rule.setErrorMessage(errorMatcher.group(1));
        }

        rule.setRuleType("custom");
        rule.setImplementation(body);

        result.addValidationRule(rule);
    }

    /**
     * Extract a business rule from a function
     */
    private void extractBusinessRule(String name, String parameters, String body, String pageUrl, BusinessLogicResult result) {
        BusinessRule rule = new BusinessRule();
        rule.setName(name);

        // Set a description based on the function name
        rule.setDescription(camelCaseToDescription(name));

        // Set the source location
        rule.setSourceLocation(pageUrl);

        // Set the implementation
        rule.setImplementation(body);

        // Determine rule type based on content
        if (body.contains("if") && (body.contains("required") || body.contains("valid"))) {
            rule.setRuleType("validation");
        } else if (body.contains("permission") || body.contains("allow") || body.contains("restrict")) {
            rule.setRuleType("authorization");
        } else if (body.contains("format") || body.contains("transform")) {
            rule.setRuleType("transformation");
        } else if (body.contains("calculate") || body.contains("sum") || body.contains("total")) {
            rule.setRuleType("calculation");
        } else {
            rule.setRuleType("process");
        }

        // Try to extract affected fields
        Pattern fieldPattern = Pattern.compile("document\\.getElementById\\(['\"]([^'\"]+)['\"]\\)|\\$\\(['\"]#([^'\"]+)['\"]\\)");
        Matcher fieldMatcher = fieldPattern.matcher(body);
        while (fieldMatcher.find()) {
            String fieldName = fieldMatcher.group(1);
            if (fieldName == null) fieldName = fieldMatcher.group(2);
            if (fieldName != null) {
                rule.addAffectedField(fieldName);
            }
        }

        // Set confidence score based on complexity
        rule.setConfidenceScore(calculateConfidenceScore(body));

        result.addBusinessRule(rule);
    }

    /**
     * Extract jQuery validation rules
     */
    private void extractJQueryValidationRules(JavascriptExecutor jsExecutor, BusinessLogicResult result) {
        try {
            // Try to extract validation rules from jQuery validate
            String script =
                    "try {" +
                            "  var validationRules = {};" +
                            "  if ($.validator && $.validator.prototype) {" +
                            "    $('form').each(function() {" +
                            "      var form = $(this);" +
                            "      var validator = form.data('validator');" +
                            "      if (validator && validator.settings && validator.settings.rules) {" +
                            "        var formId = form.attr('id') || 'form_' + $('form').index(form);" +
                            "        validationRules[formId] = validator.settings.rules;" +
                            "      }" +
                            "    });" +
                            "  }" +
                            "  return JSON.stringify(validationRules);" +
                            "} catch(e) { return '{}'; }";

            String result_str = (String) jsExecutor.executeScript(script);

            // In a real implementation, parse the JSON and create validation rules
            // This is a simplified version that just checks if we got rules
            if (result_str != null && !result_str.equals("{}")) {
                // Parse the JSON and create ValidationRule objects
                // For each rule, create a ValidationRule and add it to the result
            }
        } catch (Exception e) {
            logger.warn("Error extracting jQuery validation rules", e);
        }
    }

    /**
     * Extract Formik validation rules
     */
    private void extractFormikValidationRules(JavascriptExecutor jsExecutor, BusinessLogicResult result) {
        try {
            // Try to extract validation rules from Formik
            String script =
                    "try {" +
                            "  var formikRules = {};" +
                            "  if (window.formikInstances) {" +
                            "    window.formikInstances.forEach(function(instance, index) {" +
                            "      formikRules['formik_' + index] = instance.validationSchema || {};" +
                            "    });" +
                            "  }" +
                            "  return JSON.stringify(formikRules);" +
                            "} catch(e) { return '{}'; }";

            String result_str = (String) jsExecutor.executeScript(script);

            // In a real implementation, parse the JSON and create validation rules
            // This is a simplified version that just checks if we got rules
            if (result_str != null && !result_str.equals("{}")) {
                // Parse the JSON and create ValidationRule objects
                // For each rule, create a ValidationRule and add it to the result
            }
        } catch (Exception e) {
            logger.warn("Error extracting Formik validation rules", e);
        }
    }

    /**
     * Extract calculation rules from the application
     */
    private void extractCalculationRules(WebDriver driver, List<Page> pages, BusinessLogicResult result) {
        for (Page page : pages) {
            try {
                // Navigate to the page
                driver.get(page.getUrl());

                // Wait for page to load
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

                JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;

                // Look for event handlers that might perform calculations
                detectCalculationEventHandlers(jsExecutor, page.getUrl(), result);

                // Analyze forms for calculation fields
                detectCalculationFields(driver, page.getUrl(), result);

            } catch (Exception e) {
                logger.warn("Error extracting calculation rules on page: {}", page.getUrl(), e);
            }
        }
    }

    /**
     * Detect calculation event handlers
     */
    private void detectCalculationEventHandlers(JavascriptExecutor jsExecutor, String pageUrl, BusinessLogicResult result) {
        try {
            // Try to find elements with event handlers that do calculations
            String script =
                    "var handlers = [];" +
                            "var calculationKeywords = ['sum', 'total', 'calculate', 'price', 'amount', 'compute'];" +
                            "function getEvents(element) {" +
                            "  var events = [];" +
                            "  for (var prop in element) {" +
                            "    if (prop.startsWith('on') && typeof element[prop] === 'function') {" +
                            "      var handler = element[prop].toString();" +
                            "      for (var keyword of calculationKeywords) {" +
                            "        if (handler.indexOf(keyword) !== -1 && (handler.indexOf('+') !== -1 || handler.indexOf('-') !== -1 || handler.indexOf('*') !== -1 || handler.indexOf('/') !== -1)) {" +
                            "          events.push({" +
                            "            event: prop.substring(2)," +
                            "            element: element.id || element.name || element.tagName," +
                            "            handler: handler" +
                            "          });" +
                            "          break;" +
                            "        }" +
                            "      }" +
                            "    }" +
                            "  }" +
                            "  return events;" +
                            "}" +
                            "try {" +
                            "  document.querySelectorAll('input[type=\"number\"], input[type=\"text\"], select').forEach(function(el) {" +
                            "    handlers = handlers.concat(getEvents(el));" +
                            "  });" +
                            "} catch(e) {}" +
                            "return JSON.stringify(handlers);";

            String result_str = (String) jsExecutor.executeScript(script);

            // In a real implementation, parse the JSON and create calculation rules
            // This is a simplified version that just checks if we got handlers
            if (result_str != null && !result_str.equals("[]")) {
                // Parse handlers and create CalculationRule objects
            }
        } catch (Exception e) {
            logger.warn("Error detecting calculation event handlers", e);
        }
    }

    /**
     * Detect fields that appear to be calculated based on other fields
     */
    private void detectCalculationFields(WebDriver driver, String pageUrl, BusinessLogicResult result) {
        try {
            // Look for read-only or disabled fields that might display calculation results
            List<WebElement> possibleResultFields = driver.findElements(
                    By.cssSelector("input[readonly], input[disabled], span.total, span.sum, span.result, [data-calc], .calculated"));

            for (WebElement field : possibleResultFields) {
                String id = field.getAttribute("id");
                String name = field.getAttribute("name");
                String fieldId = id != null ? id : (name != null ? name : "unknown");

                // Try to find calculation patterns near this field
                List<WebElement> nearbyFields = driver.findElements(
                        By.xpath("//input[@type='number' or @type='text'][not(@readonly) and not(@disabled)]" +
                                "[(ancestor::form and ancestor::form//span[@id='" + fieldId + "']) or (preceding::* and preceding::*//span[@id='" + fieldId + "']) or (following::* and following::*//span[@id='" + fieldId + "'])]"));

                if (nearbyFields.size() >= 2) {
                    // This field might be calculated from nearby fields
                    CalculationRule rule = new CalculationRule();
                    rule.setName("calculated_" + fieldId);
                    rule.setOutputField(fieldId);

                    // Add nearby fields as inputs
                    for (WebElement inputField : nearbyFields) {
                        String inputId = inputField.getAttribute("id");
                        String inputName = inputField.getAttribute("name");
                        if (inputId != null && !inputId.isEmpty()) {
                            rule.addInputField(inputId);
                        } else if (inputName != null && !inputName.isEmpty()) {
                            rule.addInputField(inputName);
                        }
                    }

                    // Set placeholder formula
                    rule.setFormula("Calculation involving " + String.join(", ", rule.getInputFields()));

                    result.addCalculationRule(rule);
                }
            }
        } catch (Exception e) {
            logger.warn("Error detecting calculation fields", e);
        }
    }

    /**
     * Extract field dependencies from the application
     */
    private void extractFieldDependencies(WebDriver driver, List<Page> pages, BusinessLogicResult result) {
        for (Page page : pages) {
            try {
                // Navigate to the page
                driver.get(page.getUrl());

                // Wait for page to load
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

                // Look for visible dependencies (e.g., fields that appear when others are selected)
                detectVisibleDependencies(driver, result);

                // Look for value dependencies (e.g., dropdown options that change based on another field)
                detectValueDependencies(driver, result);

            } catch (Exception e) {
                logger.warn("Error extracting field dependencies on page: {}", page.getUrl(), e);
            }
        }
    }

    /**
     * Detect fields that control the visibility of other fields
     */
    private void detectVisibleDependencies(WebDriver driver, BusinessLogicResult result) {
        try {
            // Find checkboxes and radio buttons
            List<WebElement> toggles = driver.findElements(
                    By.cssSelector("input[type='checkbox'], input[type='radio'], select"));

            // For each toggle field, try interacting with it to see if it shows/hides other fields
            for (WebElement toggle : toggles) {
                // Record which fields are currently visible
                List<WebElement> allFields = driver.findElements(
                        By.cssSelector("input:not([type='checkbox']):not([type='radio']), select, textarea"));
                Set<String> visibleFieldsBefore = getVisibleFieldIds(allFields);

                // Interact with the toggle
                if (toggle.getTagName().equalsIgnoreCase("select")) {
                    // For selects, try changing the selection
                    List<WebElement> options = toggle.findElements(By.tagName("option"));
                    if (options.size() > 1) {
                        // Select an option that isn't currently selected
                        WebElement currentOption = options.stream()
                                                          .filter(WebElement::isSelected)
                                                          .findFirst()
                                                          .orElse(null);
                        WebElement optionToSelect = currentOption == options.get(0) ? options.get(1) : options.get(0);
                        optionToSelect.click();
                    }
                } else {
                    // For checkboxes and radios, click if not already selected
                    if (!toggle.isSelected()) {
                        toggle.click();
                    }
                }

                // Wait a moment for any visibility changes
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Check which fields are now visible
                allFields = driver.findElements(
                        By.cssSelector("input:not([type='checkbox']):not([type='radio']), select, textarea"));
                Set<String> visibleFieldsAfter = getVisibleFieldIds(allFields);

                // Find fields that appeared
                Set<String> appearedFields = new HashSet<>(visibleFieldsAfter);
                appearedFields.removeAll(visibleFieldsBefore);

                // Find fields that disappeared
                Set<String> disappearedFields = new HashSet<>(visibleFieldsBefore);
                disappearedFields.removeAll(visibleFieldsAfter);

                // Record dependencies
                String toggleId = getFieldIdentifier(toggle);

                for (String fieldId : appearedFields) {
                    result.addFieldDependency(fieldId, toggleId);
                }
                for (String fieldId : disappearedFields) {
                    result.addFieldDependency(fieldId, toggleId);
                }

                // Reset the toggle to its original state
                if (toggle.getTagName().equalsIgnoreCase("select")) {
                    // For selects, we already changed the selection
                    // We could reset it here if needed
                } else {
                    // For checkboxes and radios, click it again to reset
                    toggle.click();
                }
            }
        } catch (Exception e) {
            logger.warn("Error detecting visible dependencies", e);
        }
    }

    /**
     * Get a set of IDs/names for visible fields
     */
    private Set<String> getVisibleFieldIds(List<WebElement> fields) {
        Set<String> visibleFieldIds = new HashSet<>();

        for (WebElement field : fields) {
            if (field.isDisplayed()) {
                String id = getFieldIdentifier(field);
                if (id != null) {
                    visibleFieldIds.add(id);
                }
            }
        }

        return visibleFieldIds;
    }

    /**
     * Get an identifier for a field (id or name)
     */
    private String getFieldIdentifier(WebElement field) {
        String id = field.getAttribute("id");
        String name = field.getAttribute("name");

        if (id != null && !id.isEmpty()) {
            return id;
        } else if (name != null && !name.isEmpty()) {
            return name;
        } else {
            return null;
        }
    }

    /**
     * Detect fields that affect the values of other fields
     */
    private void detectValueDependencies(WebDriver driver, BusinessLogicResult result) {
        try {
            // Find select elements and multi-step controls
            List<WebElement> selects = driver.findElements(By.tagName("select"));

            // Look for dependency pairs
            for (int i = 0; i < selects.size(); i++) {
                WebElement select1 = selects.get(i);
                String select1Id = getFieldIdentifier(select1);

                if (select1Id == null) {
                    continue;
                }

                // Get initial options for all other selects
                Map<String, List<String>> initialOptions = new HashMap<>();
                for (int j = 0; j < selects.size(); j++) {
                    if (i == j) {
                        continue;
                    }

                    WebElement select2 = selects.get(j);
                    String select2Id = getFieldIdentifier(select2);

                    if (select2Id != null) {
                        initialOptions.put(select2Id, getSelectOptions(select2));
                    }
                }

                // Interact with select1 to see if it changes options in other selects
                List<WebElement> options = select1.findElements(By.tagName("option"));
                if (options.size() <= 1) {
                    continue;
                }

                // Select a different option
                WebElement currentOption = options.stream()
                                                  .filter(WebElement::isSelected)
                                                  .findFirst()
                                                  .orElse(null);
                WebElement optionToSelect = currentOption == options.get(0) ? options.get(1) : options.get(0);
                optionToSelect.click();

                // Wait a moment for any changes
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Check if options changed in any other selects
                for (int j = 0; j < selects.size(); j++) {
                    if (i == j) {
                        continue;
                    }

                    WebElement select2 = selects.get(j);
                    String select2Id = getFieldIdentifier(select2);

                    if (select2Id != null && initialOptions.containsKey(select2Id)) {
                        List<String> newOptions = getSelectOptions(select2);
                        List<String> oldOptions = initialOptions.get(select2Id);

                        if (!newOptions.equals(oldOptions)) {
                            // Options changed, indicating a dependency
                            result.addFieldDependency(select2Id, select1Id);
                        }
                    }
                }

                // Reset select1 to its original state
                if (currentOption != null) {
                    currentOption.click();
                }
            }
        } catch (Exception e) {
            logger.warn("Error detecting value dependencies", e);
        }
    }

    /**
     * Get the option values for a select element
     */
    private List<String> getSelectOptions(WebElement select) {
        List<String> options = new ArrayList<>();

        try {
            for (WebElement option : select.findElements(By.tagName("option"))) {
                String value = option.getAttribute("value");
                if (value != null && !value.isEmpty()) {
                    options.add(value);
                }
            }
        } catch (Exception e) {
            logger.warn("Error getting select options", e);
        }

        return options;
    }

    /**
     * Calculate a confidence score for a business rule
     */
    private double calculateConfidenceScore(String implementation) {
        // A simple heuristic to estimate confidence based on implementation complexity

        // Start with medium confidence
        double confidence = 0.5;

        // Increase confidence based on certain indicators
        if (implementation.contains("if") || implementation.contains("switch")) {
            confidence += 0.1;  // Conditional logic
        }
        if (implementation.contains("for") || implementation.contains("while")) {
            confidence += 0.1;  // Loops
        }
        if (implementation.contains("return")) {
            confidence += 0.1;  // Returns a value
        }

        // Length-based adjustment - longer functions might be more complex
        confidence += Math.min(0.2, implementation.length() / 1000.0);

        // Cap at 0.95 (never completely certain)
        return Math.min(0.95, confidence);
    }

    /**
     * Convert camel case to a readable description
     */
    private String camelCaseToDescription(String camelCase) {
        // Replace camelCase with spaces
        String result = camelCase.replaceAll("([a-z])([A-Z])", "$1 $2");

        // Capitalize first letter
        if (!result.isEmpty()) {
            result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
        }

        return result;
    }
}