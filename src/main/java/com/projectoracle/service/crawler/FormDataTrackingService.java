package com.projectoracle.service.crawler;

import org.openqa.selenium.remote.RemoteWebElement;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for tracking and analyzing form data across the application.
 * Monitors form submissions, validates form inputs, and analyzes data transformations.
 * Part of the Data Flow Analysis system described in the roadmap.
 */
@Service
public class FormDataTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(FormDataTrackingService.class);

    @Autowired
    private WebDriverSessionManager webDriverSessionManager;

    @Autowired
    private ElementRepository elementRepository;

    @Autowired
    private CrawlerConfig crawlerConfig;

    // Cache of form analysis results
    private final Map<String, FormAnalysis> formAnalysisCache = new ConcurrentHashMap<>();

    /**
     * Represents the analysis of a form including its fields and validation rules
     */
    public static class FormAnalysis {
        private String formId;
        private String formUrl;
        private String formName;
        private List<FormField> fields = new ArrayList<>();
        private List<ValidationRule> validationRules = new ArrayList<>();
        private List<DataTransformation> dataTransformations = new ArrayList<>();
        private String submitAction;
        private boolean isMultiStep;
        private boolean hasFileUpload;
        private Map<String, String> detectedPatterns = new HashMap<>();

        // Getters and Setters
        public String getFormId() {
            return formId;
        }

        public void setFormId(String formId) {
            this.formId = formId;
        }

        public String getFormUrl() {
            return formUrl;
        }

        public void setFormUrl(String formUrl) {
            this.formUrl = formUrl;
        }

        public String getFormName() {
            return formName;
        }

        public void setFormName(String formName) {
            this.formName = formName;
        }

        public List<FormField> getFields() {
            return fields;
        }

        public void setFields(List<FormField> fields) {
            this.fields = fields;
        }

        public void addField(FormField field) {
            this.fields.add(field);
        }

        public List<ValidationRule> getValidationRules() {
            return validationRules;
        }

        public void setValidationRules(List<ValidationRule> validationRules) {
            this.validationRules = validationRules;
        }

        public void addValidationRule(ValidationRule rule) {
            this.validationRules.add(rule);
        }

        public List<DataTransformation> getDataTransformations() {
            return dataTransformations;
        }

        public void setDataTransformations(List<DataTransformation> dataTransformations) {
            this.dataTransformations = dataTransformations;
        }

        public void addDataTransformation(DataTransformation transformation) {
            this.dataTransformations.add(transformation);
        }

        public String getSubmitAction() {
            return submitAction;
        }

        public void setSubmitAction(String submitAction) {
            this.submitAction = submitAction;
        }

        public boolean isMultiStep() {
            return isMultiStep;
        }

        public void setMultiStep(boolean multiStep) {
            isMultiStep = multiStep;
        }

        public boolean isHasFileUpload() {
            return hasFileUpload;
        }

        public void setHasFileUpload(boolean hasFileUpload) {
            this.hasFileUpload = hasFileUpload;
        }

        public Map<String, String> getDetectedPatterns() {
            return detectedPatterns;
        }

        public void setDetectedPatterns(Map<String, String> detectedPatterns) {
            this.detectedPatterns = detectedPatterns;
        }

        public void addDetectedPattern(String fieldName, String pattern) {
            this.detectedPatterns.put(fieldName, pattern);
        }
    }

    /**
     * Represents a form field with its properties and validation rules
     */
    public static class FormField {
        private String name;
        private String id;
        private String type;
        private String label;
        private boolean required;
        private String defaultValue;
        private List<String> options = new ArrayList<>();
        private Map<String, String> attributes = new HashMap<>();
        private String elementLocator;
        private List<String> dependentFields = new ArrayList<>();
        private String validationPattern;
        private String errorMessage;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public List<String> getOptions() {
            return options;
        }

        public void setOptions(List<String> options) {
            this.options = options;
        }

        public void addOption(String option) {
            this.options.add(option);
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, String> attributes) {
            this.attributes = attributes;
        }

        public void addAttribute(String name, String value) {
            this.attributes.put(name, value);
        }

        public String getElementLocator() {
            return elementLocator;
        }

        public void setElementLocator(String elementLocator) {
            this.elementLocator = elementLocator;
        }

        public List<String> getDependentFields() {
            return dependentFields;
        }

        public void setDependentFields(List<String> dependentFields) {
            this.dependentFields = dependentFields;
        }

        public void addDependentField(String fieldName) {
            this.dependentFields.add(fieldName);
        }

        public String getValidationPattern() {
            return validationPattern;
        }

        public void setValidationPattern(String validationPattern) {
            this.validationPattern = validationPattern;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        /**
         * Generate test data based on field type and validation rules
         */
        public String generateTestData() {
            // Generate appropriate test data based on field type
            switch (type) {
                case "text":
                    if (validationPattern != null) {
                        // Generate data that matches the pattern
                        if (validationPattern.contains("email")) {
                            return "test@example.com";
                        } else if (validationPattern.contains("\\d+")) {
                            return "12345";
                        } else {
                            return "Test Value";
                        }
                    }
                    return "Test " + (label != null ? label : name);
                case "email":
                    return "test@example.com";
                case "password":
                    return "Password123!";
                case "number":
                    return "42";
                case "date":
                    return "2023-06-15";
                case "checkbox":
                    return "true";
                case "radio":
                    return options.isEmpty() ? "option1" : options.get(0);
                case "select":
                    return options.isEmpty() ? "Option 1" : options.get(0);
                case "textarea":
                    return "This is a test comment for the " + (label != null ? label : name) + " field.";
                case "tel":
                    return "555-123-4567";
                case "url":
                    return "https://example.com";
                case "file":
                    return "test-file.txt";
                default:
                    return "Test Value";
            }
        }

        /**
         * Generate invalid test data for negative testing
         */
        public String generateInvalidTestData() {
            // Generate invalid data based on field type
            switch (type) {
                case "email":
                    return "invalid-email";
                case "number":
                    return "not-a-number";
                case "date":
                    return "invalid-date";
                case "tel":
                    return "not-a-phone";
                case "url":
                    return "not-a-url";
                case "password":
                    // Generate a password that's too short
                    return "pw1";
                default:
                    // For required fields, return empty string
                    if (required) {
                        return "";
                    }
                    // Otherwise, return a very long string
                    return "This is an extremely long input value that might exceed the maximum length allowed for this field and cause validation errors for testing purposes.";
            }
        }
    }

    /**
     * Represents a validation rule for a form field
     */
    public static class ValidationRule {
        private String fieldName;
        private String ruleType;
        private String pattern;
        private String errorMessage;
        private List<String> dependentFields = new ArrayList<>();
        private Map<String, String> parameters = new HashMap<>();

        // Getters and Setters
        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getRuleType() {
            return ruleType;
        }

        public void setRuleType(String ruleType) {
            this.ruleType = ruleType;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public List<String> getDependentFields() {
            return dependentFields;
        }

        public void setDependentFields(List<String> dependentFields) {
            this.dependentFields = dependentFields;
        }

        public void addDependentField(String fieldName) {
            this.dependentFields.add(fieldName);
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, String> parameters) {
            this.parameters = parameters;
        }

        public void addParameter(String name, String value) {
            this.parameters.put(name, value);
        }
    }

    /**
     * Represents a data transformation between forms or pages
     */
    public static class DataTransformation {
        private String sourceField;
        private String targetField;
        private String transformationType;
        private String transformationRule;
        private String sourceValue;
        private String targetValue;

        // Getters and Setters
        public String getSourceField() {
            return sourceField;
        }

        public void setSourceField(String sourceField) {
            this.sourceField = sourceField;
        }

        public String getTargetField() {
            return targetField;
        }

        public void setTargetField(String targetField) {
            this.targetField = targetField;
        }

        public String getTransformationType() {
            return transformationType;
        }

        public void setTransformationType(String transformationType) {
            this.transformationType = transformationType;
        }

        public String getTransformationRule() {
            return transformationRule;
        }

        public void setTransformationRule(String transformationRule) {
            this.transformationRule = transformationRule;
        }

        public String getSourceValue() {
            return sourceValue;
        }

        public void setSourceValue(String sourceValue) {
            this.sourceValue = sourceValue;
        }

        public String getTargetValue() {
            return targetValue;
        }

        public void setTargetValue(String targetValue) {
            this.targetValue = targetValue;
        }
    }

    /**
     * Analyze a form page to extract its structure and validation rules
     *
     * @param page the form page to analyze
     * @return analysis of the form
     */
    public FormAnalysis analyzeForm(Page page) {
        // Check if we have a cached analysis for this page
        if (formAnalysisCache.containsKey(page.getUrl())) {
            logger.info("Using cached form analysis for: {}", page.getUrl());
            return formAnalysisCache.get(page.getUrl());
        }

        logger.info("Analyzing form on page: {}", page.getUrl());

        FormAnalysis analysis = new FormAnalysis();
        analysis.setFormUrl(page.getUrl());

        // Create WebDriver session
        String sessionId = webDriverSessionManager.createSession(
                WebDriverSessionManager.BrowserType.CHROME, true);
        WebDriver driver = webDriverSessionManager.getDriver(sessionId);

        try {
            // Navigate to the page
            driver.get(page.getUrl());

            // Wait for page to load
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // Find the form element
            List<WebElement> forms = driver.findElements(By.tagName("form"));
            if (forms.isEmpty()) {
                logger.warn("No form found on page: {}", page.getUrl());
                analysis.setFormName("Unknown Form");
            } else {
                WebElement formElement = forms.get(0); // Analyze the first form for now

                // Extract form attributes
                String formId = formElement.getAttribute("id");
                String formName = formElement.getAttribute("name");
                String formAction = formElement.getAttribute("action");

                analysis.setFormId(formId != null ? formId : "form_" + page.getUrl().hashCode());
                analysis.setFormName(formName != null ? formName : "Form on " + page.getTitle());
                analysis.setSubmitAction(formAction);

                // Analyze form fields
                analyzeFormFields(formElement, analysis, driver);

                // Detect field dependencies
                detectFieldDependencies(formElement, analysis, driver);

                // Extract validation rules
                extractValidationRules(formElement, analysis);

                // Check if it's a multi-step form
                analysis.setMultiStep(isMultiStepForm(formElement));

                // Check for file uploads
                analysis.setHasFileUpload(hasFileUpload(formElement));
            }

            // Run JavaScript to find validation rules
            extractValidationRulesWithJavaScript(driver, analysis);

            // Cache the analysis
            formAnalysisCache.put(page.getUrl(), analysis);

            return analysis;
        } catch (Exception e) {
            logger.error("Error analyzing form: {}", page.getUrl(), e);
            return analysis;
        } finally {
            // Release the WebDriver session
            webDriverSessionManager.releaseSession(sessionId);
        }
    }

    /**
     * Analyze the form fields
     */
    private void analyzeFormFields(WebElement formElement, FormAnalysis analysis, WebDriver driver) {
        // Find all input elements
        List<WebElement> inputElements = formElement.findElements(By.tagName("input"));
        for (WebElement input : inputElements) {
            String type = input.getAttribute("type");
            if ("hidden".equals(type)) {
                continue; // Skip hidden inputs
            }

            FormField field = new FormField();
            field.setName(input.getAttribute("name"));
            field.setId(input.getAttribute("id"));
            field.setType(type != null ? type : "text");
            field.setDefaultValue(input.getAttribute("value"));
            field.setRequired("true".equals(input.getAttribute("required")) ||
                    input.getAttribute("aria-required") != null);
            field.setElementLocator(getElementXPath(input, driver));

            // Extract label
            WebElement label = findLabelForElement(formElement, input, driver);
            if (label != null) {
                field.setLabel(label.getText());
            }

            // Extract HTML5 validation attributes
            String pattern = input.getAttribute("pattern");
            if (pattern != null && !pattern.isEmpty()) {
                field.setValidationPattern(pattern);
                extractValidationRuleFromPattern(field, pattern, analysis);
            }

            if ("checkbox".equals(type) || "radio".equals(type)) {
                // For checkbox/radio, look for all options with the same name
                List<WebElement> options = formElement.findElements(
                        By.cssSelector("input[name='" + field.getName() + "']"));
                for (WebElement option : options) {
                    String value = option.getAttribute("value");
                    if (value != null && !value.isEmpty()) {
                        field.addOption(value);
                    }
                }
            }

            // Extract additional attributes
            extractCommonAttributes(input, field);

            analysis.addField(field);
        }

        // Find all select elements
        List<WebElement> selectElements = formElement.findElements(By.tagName("select"));
        for (WebElement select : selectElements) {
            FormField field = new FormField();
            field.setName(select.getAttribute("name"));
            field.setId(select.getAttribute("id"));
            field.setType("select");
            field.setRequired("true".equals(select.getAttribute("required")) ||
                    select.getAttribute("aria-required") != null);
            field.setElementLocator(getElementXPath(select, driver));

            // Extract label
            WebElement label = findLabelForElement(formElement, select, driver);
            if (label != null) {
                field.setLabel(label.getText());
            }

            // Extract options
            List<WebElement> options = select.findElements(By.tagName("option"));
            for (WebElement option : options) {
                String value = option.getAttribute("value");
                if (value != null && !value.isEmpty()) {
                    field.addOption(value);
                }
            }

            // Extract additional attributes
            extractCommonAttributes(select, field);

            analysis.addField(field);
        }

        // Find all textarea elements
        List<WebElement> textareaElements = formElement.findElements(By.tagName("textarea"));
        for (WebElement textarea : textareaElements) {
            FormField field = new FormField();
            field.setName(textarea.getAttribute("name"));
            field.setId(textarea.getAttribute("id"));
            field.setType("textarea");
            field.setDefaultValue(textarea.getText());
            field.setRequired("true".equals(textarea.getAttribute("required")) ||
                    textarea.getAttribute("aria-required") != null);
            field.setElementLocator(getElementXPath(textarea, driver));

            // Extract label
            WebElement label = findLabelForElement(formElement, textarea, driver);
            if (label != null) {
                field.setLabel(label.getText());
            }

            // Extract additional attributes
            extractCommonAttributes(textarea, field);

            analysis.addField(field);
        }
    }

    /**
     * Extract common attributes from a form element
     */
    private void extractCommonAttributes(WebElement element, FormField field) {
        // Check for common validation attributes
        String minLength = element.getAttribute("minlength");
        if (minLength != null && !minLength.isEmpty()) {
            field.addAttribute("minlength", minLength);
            field.setValidationPattern(field.getValidationPattern() != null ?
                    field.getValidationPattern() + " min=" + minLength : "min=" + minLength);
        }

        String maxLength = element.getAttribute("maxlength");
        if (maxLength != null && !maxLength.isEmpty()) {
            field.addAttribute("maxlength", maxLength);
            field.setValidationPattern(field.getValidationPattern() != null ?
                    field.getValidationPattern() + " max=" + maxLength : "max=" + maxLength);
        }

        String min = element.getAttribute("min");
        if (min != null && !min.isEmpty()) {
            field.addAttribute("min", min);
            field.setValidationPattern(field.getValidationPattern() != null ?
                    field.getValidationPattern() + " min=" + min : "min=" + min);
        }

        String max = element.getAttribute("max");
        if (max != null && !max.isEmpty()) {
            field.addAttribute("max", max);
            field.setValidationPattern(field.getValidationPattern() != null ?
                    field.getValidationPattern() + " max=" + max : "max=" + max);
        }

        // Check for aria attributes
        String ariaRequired = element.getAttribute("aria-required");
        if (ariaRequired != null && !ariaRequired.isEmpty()) {
            field.addAttribute("aria-required", ariaRequired);
            field.setRequired("true".equals(ariaRequired));
        }

        String ariaDescribedBy = element.getAttribute("aria-describedby");
        if (ariaDescribedBy != null && !ariaDescribedBy.isEmpty()) {
            field.addAttribute("aria-describedby", ariaDescribedBy);
            // Try to find the description element for potential error message
            try {
                WebElement descElement = element.findElement(By.id(ariaDescribedBy));
                if (descElement != null) {
                    field.setErrorMessage(descElement.getText());
                }
            } catch (Exception e) {
                // Element not found, ignore
            }
        }

        // Check for data attributes
        for (String attr : element.getAttribute("outerHTML").split(" ")) {
            if (attr.startsWith("data-")) {
                int eqIdx = attr.indexOf('=');
                if (eqIdx > 0) {
                    String attrName = attr.substring(0, eqIdx);
                    String attrValue = attr.substring(eqIdx + 1).replaceAll("[\"']", "");
                    field.addAttribute(attrName, attrValue);
                }
            }
        }
    }

    /**
     * Find the label element associated with a form field
     */
    private WebElement findLabelForElement(WebElement formElement, WebElement inputElement, WebDriver driver) {
        String id = inputElement.getAttribute("id");
        if (id != null && !id.isEmpty()) {
            try {
                // Try to find label with "for" attribute
                WebElement label = formElement.findElement(By.cssSelector("label[for='" + id + "']"));
                return label;
            } catch (Exception e) {
                // No label found, try to find parent label
                try {
                    WebElement parent = getParentElement(inputElement, driver);
                    if (parent.getTagName().equalsIgnoreCase("label")) {
                        return parent;
                    }
                } catch (Exception ex) {
                    // Ignore, no label found
                }
            }
        }
        return null;
    }

    /**
     * Get the parent WebElement
     */
    private WebElement getParentElement(WebElement element, WebDriver driver) {
        return (WebElement) ((JavascriptExecutor) driver)
                .executeScript("return arguments[0].parentNode;", element);
    }

    /**
     * Detect dependencies between form fields
     */
    private void detectFieldDependencies(WebElement formElement, FormAnalysis analysis, WebDriver driver) {
        // Look for fields that might have dependencies
        for (FormField field : analysis.getFields()) {
            // Check for data-depends-on attribute
            String dependsOn = field.getAttributes().get("data-depends-on");
            if (dependsOn != null) {
                field.addDependentField(dependsOn);
                continue;
            }

            // Check for fields that might be conditionally displayed
            try {
                WebElement fieldElement = formElement.findElement(By.xpath(field.getElementLocator()));
                String display = fieldElement.getCssValue("display");
                if ("none".equals(display)) {
                    // This field might be dependent on another field
                    detectDynamicDependency(formElement, field, analysis, driver);
                }
            } catch (Exception e) {
                // Element not found, ignore
            }
        }
    }

    /**
     * Detect dynamic dependencies by interacting with the form
     */
    private void detectDynamicDependency(WebElement formElement, FormField field, FormAnalysis analysis, WebDriver driver) {
        // Get all checkboxes, radios, and selects in the form
        List<WebElement> possibleTriggers = new ArrayList<>();
        possibleTriggers.addAll(formElement.findElements(By.cssSelector("input[type='checkbox']")));
        possibleTriggers.addAll(formElement.findElements(By.cssSelector("input[type='radio']")));
        possibleTriggers.addAll(formElement.findElements(By.tagName("select")));

        // Try to find triggers that show the hidden field
        for (WebElement trigger : possibleTriggers) {
            try {
                // Get current state of field
                WebElement fieldElement = formElement.findElement(By.xpath(field.getElementLocator()));
                boolean wasVisible = fieldElement.isDisplayed();

                // Interact with potential trigger
                if (trigger.getTagName().equals("select")) {
                    // Try changing the select
                    List<WebElement> options = trigger.findElements(By.tagName("option"));
                    if (options.size() > 1) {
                        options.get(1).click(); // Select the second option
                    }
                } else {
                    // Click the checkbox or radio
                    trigger.click();
                }

                // Check if field visibility changed
                boolean isNowVisible = fieldElement.isDisplayed();
                if (isNowVisible != wasVisible) {
                    // Found a dependency
                    String triggerName = trigger.getAttribute("name");
                    field.addDependentField(triggerName);

                    // Create a validation rule for this dependency
                    ValidationRule rule = new ValidationRule();
                    rule.setFieldName(field.getName());
                    rule.setRuleType("dependency");
                    rule.addDependentField(triggerName);
                    rule.addParameter("trigger", triggerName);
                    rule.addParameter("triggerValue", trigger.getAttribute("value"));
                    analysis.addValidationRule(rule);
                }
            } catch (Exception e) {
                // Element interaction failed, skip
            }
        }
    }

    /**
     * Extract validation rules from HTML attributes and patterns
     */
    private void extractValidationRules(WebElement formElement, FormAnalysis analysis) {
        // Extract validation rules from field attributes
        for (FormField field : analysis.getFields()) {
            extractValidationRulesFromAttributes(field, analysis);
        }
    }

    /**
     * Extract validation rules from HTML attributes and patterns
     */
    private void extractValidationRulesFromAttributes(FormField field, FormAnalysis analysis) {
        // Create validation rule for required fields
        if (field.isRequired()) {
            ValidationRule rule = new ValidationRule();
            rule.setFieldName(field.getName());
            rule.setRuleType("required");
            rule.setErrorMessage("This field is required");
            analysis.addValidationRule(rule);
        }

        // Create validation rules based on field type
        switch (field.getType()) {
            case "email":
                ValidationRule emailRule = new ValidationRule();
                emailRule.setFieldName(field.getName());
                emailRule.setRuleType("email");
                emailRule.setPattern("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$");
                emailRule.setErrorMessage("Please enter a valid email address");
                analysis.addValidationRule(emailRule);
                break;

            case "number":
                ValidationRule numberRule = new ValidationRule();
                numberRule.setFieldName(field.getName());
                numberRule.setRuleType("number");

                // Check for min/max attributes
                String min = field.getAttributes().get("min");
                String max = field.getAttributes().get("max");
                if (min != null) {
                    numberRule.addParameter("min", min);
                }
                if (max != null) {
                    numberRule.addParameter("max", max);
                }

                numberRule.setErrorMessage("Please enter a valid number");
                analysis.addValidationRule(numberRule);
                break;

            case "date":
                ValidationRule dateRule = new ValidationRule();
                dateRule.setFieldName(field.getName());
                dateRule.setRuleType("date");
                dateRule.setErrorMessage("Please enter a valid date");
                analysis.addValidationRule(dateRule);
                break;

            case "tel":
                ValidationRule telRule = new ValidationRule();
                telRule.setFieldName(field.getName());
                telRule.setRuleType("telephone");
                telRule.setPattern("^[0-9\\-\\+\\s\\(\\)]+$");
                telRule.setErrorMessage("Please enter a valid phone number");
                analysis.addValidationRule(telRule);
                break;

            case "url":
                ValidationRule urlRule = new ValidationRule();
                urlRule.setFieldName(field.getName());
                urlRule.setRuleType("url");
                urlRule.setPattern("^https?:\\/\\/[^\\s]+$");
                urlRule.setErrorMessage("Please enter a valid URL");
                analysis.addValidationRule(urlRule);
                break;

            case "password":
                // Common password rules
                ValidationRule passwordRule = new ValidationRule();
                passwordRule.setFieldName(field.getName());
                passwordRule.setRuleType("password");
                passwordRule.setPattern("^.{8,}$"); // At least 8 characters
                passwordRule.setErrorMessage("Password must be at least 8 characters long");
                analysis.addValidationRule(passwordRule);
                break;
        }
    }

    /**
     * Extract validation rule from a pattern attribute
     */
    private void extractValidationRuleFromPattern(FormField field, String pattern, FormAnalysis analysis) {
        ValidationRule rule = new ValidationRule();
        rule.setFieldName(field.getName());
        rule.setRuleType("pattern");
        rule.setPattern(pattern);
        rule.setErrorMessage("Please match the requested format");
        analysis.addValidationRule(rule);

        // Store pattern for the field
        analysis.addDetectedPattern(field.getName(), pattern);
    }

    /**
     * Extract validation rules with JavaScript
     */
    private void extractValidationRulesWithJavaScript(WebDriver driver, FormAnalysis analysis) {
        try {
            // Run JavaScript to detect client-side validation
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Try to detect common validation libraries
            Boolean hasJquery = (Boolean) js.executeScript("return typeof jQuery !== 'undefined'");
            Boolean hasJqueryValidation = false;

            if (hasJquery) {
                hasJqueryValidation = (Boolean) js.executeScript(
                        "return typeof jQuery.fn.validate !== 'undefined'");

                if (hasJqueryValidation) {
                    // Extract jQuery validation rules
                    extractJQueryValidationRules(js, analysis);
                }
            }

            // Check for HTML5 form validation
            extractHTML5ValidationRules(js, analysis);

        } catch (Exception e) {
            logger.warn("Error extracting validation rules with JavaScript", e);
        }
    }

    /**
     * Extract jQuery validation rules
     */
    private void extractJQueryValidationRules(JavascriptExecutor js, FormAnalysis analysis) {
        try {
            // Try to get validation rules from jQuery validate plugin
            StringBuilder scriptBuilder = new StringBuilder();
            scriptBuilder.append("var rules = {}; ");
            scriptBuilder.append("if (typeof jQuery !== 'undefined' && jQuery.fn.validate) { ");
            scriptBuilder.append("  var validator = jQuery('form').data('validator'); ");
            scriptBuilder.append("  if (validator) { ");
            scriptBuilder.append("    rules = validator.settings.rules; ");
            scriptBuilder.append("  } else { ");
            scriptBuilder.append("    // Try to get rules from HTML attributes ");
            scriptBuilder.append("    jQuery('form [data-rule-required], form [data-rule-email], form [data-rule-minlength], form [data-rule-maxlength]').each(function() { ");
            scriptBuilder.append("      var $el = jQuery(this); ");
            scriptBuilder.append("      var name = $el.attr('name'); ");
            scriptBuilder.append("      if (name) { ");
            scriptBuilder.append("        rules[name] = {}; ");
            scriptBuilder.append("        if ($el.attr('data-rule-required')) { ");
            scriptBuilder.append("          rules[name].required = true; ");
            scriptBuilder.append("        } ");
            scriptBuilder.append("        if ($el.attr('data-rule-email')) { ");
            scriptBuilder.append("          rules[name].email = true; ");
            scriptBuilder.append("        } ");
            scriptBuilder.append("        if ($el.attr('data-rule-minlength')) { ");
            scriptBuilder.append("          rules[name].minlength = parseInt($el.attr('data-rule-minlength')); ");
            scriptBuilder.append("        } ");
            scriptBuilder.append("        if ($el.attr('data-rule-maxlength')) { ");
            scriptBuilder.append("          rules[name].maxlength = parseInt($el.attr('data-rule-maxlength')); ");
            scriptBuilder.append("        } ");
            scriptBuilder.append("      } ");
            scriptBuilder.append("    }); ");
            scriptBuilder.append("  } ");
            scriptBuilder.append("} ");
            scriptBuilder.append("return JSON.stringify(rules);");

            String script = scriptBuilder.toString();
            String rulesJson = (String) js.executeScript(script);

            if (rulesJson != null && !rulesJson.equals("{}")) {
                // Parse the JSON and create validation rules
                Map<String, Map<String, Object>> rules = new HashMap<>();
                // In a real implementation, parse the JSON here

                // Create validation rules for each field
                for (Map.Entry<String, Map<String, Object>> entry : rules.entrySet()) {
                    String fieldName = entry.getKey();
                    Map<String, Object> fieldRules = entry.getValue();

                    for (Map.Entry<String, Object> ruleEntry : fieldRules.entrySet()) {
                        String ruleType = ruleEntry.getKey();
                        Object ruleValue = ruleEntry.getValue();

                        ValidationRule rule = new ValidationRule();
                        rule.setFieldName(fieldName);
                        rule.setRuleType(ruleType);

                        if (ruleValue instanceof String) {
                            rule.addParameter("value", (String) ruleValue);
                        } else if (ruleValue instanceof Number) {
                            rule.addParameter("value", ruleValue.toString());
                        } else if (ruleValue instanceof Boolean && (Boolean) ruleValue) {
                            // For boolean true, no parameter needed
                        }

                        // Set default error messages
                        switch (ruleType) {
                            case "required":
                                rule.setErrorMessage("This field is required");
                                break;
                            case "email":
                                rule.setErrorMessage("Please enter a valid email address");
                                break;
                            case "minlength":
                                rule.setErrorMessage("Please enter at least " + ruleValue + " characters");
                                break;
                            case "maxlength":
                                rule.setErrorMessage("Please enter no more than " + ruleValue + " characters");
                                break;
                            default:
                                rule.setErrorMessage("Please enter a valid value");
                                break;
                        }

                        analysis.addValidationRule(rule);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error extracting jQuery validation rules", e);
        }
    }

    /**
     * Extract HTML5 validation rules
     */
    private void extractHTML5ValidationRules(JavascriptExecutor js, FormAnalysis analysis) {
        try {
            // Get all form fields with HTML5 validation attributes
            String script =
                    "var fields = []; " +
                            "document.querySelectorAll('form input, form select, form textarea').forEach(function(el) { " +
                            "  var field = { " +
                            "    name: el.name, " +
                            "    type: el.type || el.tagName.toLowerCase(), " +
                            "    validation: {} " +
                            "  }; " +
                            "  if (el.required) { " +
                            "    field.validation.required = true; " +
                            "  } " +
                            "  if (el.pattern) { " +
                            "    field.validation.pattern = el.pattern; " +
                            "  } " +
                            "  if (el.minLength) { " +
                            "    field.validation.minLength = el.minLength; " +
                            "  } " +
                            "  if (el.maxLength && el.maxLength < 524288) { " + // Exclude default maxlength
                            "    field.validation.maxLength = el.maxLength; " +
                            "  } " +
                            "  if (el.min) { " +
                            "    field.validation.min = el.min; " +
                            "  } " +
                            "  if (el.max) { " +
                            "    field.validation.max = el.max; " +
                            "  } " +
                            "  if (Object.keys(field.validation).length > 0) { " +
                            "    fields.push(field); " +
                            "  } " +
                            "}); " +
                            "return JSON.stringify(fields);";

            String fieldsJson = (String) js.executeScript(script);

            if (fieldsJson != null && !fieldsJson.equals("[]")) {
                // In a real implementation, parse the JSON here
                // For this example, we'll simulate some fields with validation
                List<Map<String, Object>> fields = new ArrayList<>();

                // Create validation rules for each field
                for (Map<String, Object> field : fields) {
                    String fieldName = (String) field.get("name");
                    Map<String, Object> validation = (Map<String, Object>) field.get("validation");

                    for (Map.Entry<String, Object> validationEntry : validation.entrySet()) {
                        String ruleType = validationEntry.getKey();
                        Object ruleValue = validationEntry.getValue();

                        ValidationRule rule = new ValidationRule();
                        rule.setFieldName(fieldName);
                        rule.setRuleType(ruleType);

                        if (ruleValue instanceof String) {
                            rule.addParameter("value", (String) ruleValue);
                        } else if (ruleValue instanceof Number) {
                            rule.addParameter("value", ruleValue.toString());
                        } else if (ruleValue instanceof Boolean && (Boolean) ruleValue) {
                            // For boolean true, no parameter needed
                        }

                        analysis.addValidationRule(rule);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error extracting HTML5 validation rules", e);
        }
    }

    /**
     * Check if a form is a multi-step form
     */
    private boolean isMultiStepForm(WebElement formElement) {
        try {
            // Look for common indicators of multi-step forms

            // Check for multiple fieldsets or divs with step/wizard classes
            int stepContainers = formElement.findElements(By.cssSelector(
                    "fieldset, .step, .wizard-step, [data-step], .form-step"
            )).size();

            if (stepContainers > 1) {
                return true;
            }

            // Check for next/prev buttons
            List<WebElement> nextButtons = formElement.findElements(By.cssSelector(
                    "button.next, .btn-next, [data-next], #next, .next-step"
            ));

            List<WebElement> prevButtons = formElement.findElements(By.cssSelector(
                    "button.prev, .btn-prev, [data-prev], #prev, .prev-step"
            ));

            if (!nextButtons.isEmpty() && !prevButtons.isEmpty()) {
                return true;
            }

            // Check for step indicators
            int stepIndicators = formElement.findElements(By.cssSelector(
                    ".steps, .wizard-steps, .form-steps, .progress-indicator"
            )).size();

            if (stepIndicators > 0) {
                return true;
            }

            return false;
        } catch (Exception e) {
            logger.warn("Error checking for multi-step form", e);
            return false;
        }
    }

    /**
     * Check if a form has file upload fields
     */
    private boolean hasFileUpload(WebElement formElement) {
        try {
            // Look for file input fields
            List<WebElement> fileInputs = formElement.findElements(By.cssSelector("input[type='file']"));
            return !fileInputs.isEmpty();
        } catch (Exception e) {
            logger.warn("Error checking for file uploads", e);
            return false;
        }
    }

    /**
     * Get element XPath
     */
    private String getElementXPath(WebElement element, WebDriver driver) {
        // Get XPath using JavaScript
        try {
            return (String) ((JavascriptExecutor) driver).executeScript(
                    "function getXPath(element) {" +
                            "   if (element.id) return '//*[@id=\"' + element.id + '\"]';" +
                            "   if (element === document.body) return '/html/body';" +
                            "   var index = 1;" +
                            "   var siblings = element.parentNode.childNodes;" +
                            "   for (var i = 0; i < siblings.length; i++) {" +
                            "       var sibling = siblings[i];" +
                            "       if (sibling === element) return getXPath(element.parentNode) + '/' + element.tagName.toLowerCase() + '[' + index + ']';" +
                            "       if (sibling.nodeType === 1 && sibling.tagName === element.tagName) index++;" +
                            "   }" +
                            "}" +
                            "return getXPath(arguments[0])", element);
        } catch (Exception e) {
            // Fallback: use a simple identifier
            String id = element.getAttribute("id");
            if (id != null && !id.isEmpty()) {
                return "//*[@id='" + id + "']";
            }

            String name = element.getAttribute("name");
            if (name != null && !name.isEmpty()) {
                return "//*[@name='" + name + "']";
            }

            return "//unknown-element";
        }
    }

    /**
     * Analyze form submission to detect data transformations
     *
     * @param sourceUrl the URL of the form page
     * @param targetUrl the URL of the page after form submission
     * @return list of detected data transformations
     */
    public List<DataTransformation> analyzeFormSubmission(String sourceUrl, String targetUrl) {
        logger.info("Analyzing form submission from {} to {}", sourceUrl, targetUrl);

        List<DataTransformation> transformations = new ArrayList<>();

        // Get form analysis for source page
        FormAnalysis sourceAnalysis = getFormAnalysis(sourceUrl);
        if (sourceAnalysis == null) {
            logger.warn("No form analysis available for source page: {}", sourceUrl);
            return transformations;
        }

        // Create WebDriver session
        String sessionId = webDriverSessionManager.createSession(
                WebDriverSessionManager.BrowserType.CHROME, true);
        WebDriver driver = webDriverSessionManager.getDriver(sessionId);

        try {
            // Navigate to the source page
            driver.get(sourceUrl);

            // Wait for page to load
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // Fill the form with test values
            Map<String, String> formData = fillForm(driver, sourceAnalysis);

            // Submit the form
            WebElement submitButton = findSubmitButton(driver);
            if (submitButton != null) {
                submitButton.click();

                // Wait for navigation to complete
                wait.until(ExpectedConditions.or(
                        ExpectedConditions.urlContains(targetUrl),
                        ExpectedConditions.titleContains("Success"),
                        ExpectedConditions.stalenessOf(submitButton)
                ));

                // Check if the target page has our values (data transformations)
                String currentUrl = driver.getCurrentUrl();
                if (currentUrl.contains(targetUrl)) {
                    detectDataTransformations(driver, formData, transformations);
                }
            } else {
                logger.warn("Could not find submit button on form: {}", sourceUrl);
            }
        } catch (Exception e) {
            logger.error("Error analyzing form submission", e);
        } finally {
            webDriverSessionManager.releaseSession(sessionId);
        }

        return transformations;
    }

    /**
     * Get form analysis for a page, loading it if needed
     */
    private FormAnalysis getFormAnalysis(String url) {
        if (formAnalysisCache.containsKey(url)) {
            return formAnalysisCache.get(url);
        }

        // Need to create a page object and analyze it
        Page page = new Page();
        page.setUrl(url);

        return analyzeForm(page);
    }

    /**
     * Fill a form with test values
     */
    private Map<String, String> fillForm(WebDriver driver, FormAnalysis analysis) {
        Map<String, String> formData = new HashMap<>();

        for (FormField field : analysis.getFields()) {
            try {
                WebElement element = driver.findElement(By.xpath(field.getElementLocator()));

                // Generate test value
                String testValue = field.generateTestData();
                formData.put(field.getName(), testValue);

                // Fill the field based on its type
                switch (field.getType()) {
                    case "checkbox":
                        if (testValue.equals("true") && !element.isSelected()) {
                            element.click();
                        }
                        break;
                    case "radio":
                        // Find the radio button with matching value
                        if (!element.isSelected()) {
                            List<WebElement> radioOptions = driver.findElements(
                                    By.cssSelector("input[type='radio'][name='" + field.getName() + "']"));
                            for (WebElement radio : radioOptions) {
                                if (radio.getAttribute("value").equals(testValue)) {
                                    radio.click();
                                    break;
                                }
                            }
                        }
                        break;
                    case "select":
                        // Select the option with matching value
                        WebElement select = element.findElement(By.xpath("//option[@value='" + testValue + "']"));
                        select.click();
                        break;
                    default:
                        // For text inputs, clear and send keys
                        element.clear();
                        element.sendKeys(testValue);
                        break;
                }
            } catch (Exception e) {
                logger.warn("Error filling field: {}", field.getName(), e);
            }
        }

        return formData;
    }

    /**
     * Find the submit button for a form
     */
    private WebElement findSubmitButton(WebDriver driver) {
        try {
            // Try various selectors for submit buttons
            List<String> selectors = Arrays.asList(
                    "button[type='submit']",
                    "input[type='submit']",
                    "button.submit",
                    "button.btn-submit",
                    ".btn-primary",
                    "button:contains('Submit')",
                    "button:contains('Save')",
                    "button:contains('Continue')",
                    "button:contains('Next')"
            );

            for (String selector : selectors) {
                try {
                    List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                    if (!elements.isEmpty()) {
                        return elements.get(0);
                    }
                } catch (Exception e) {
                    // Try next selector
                }
            }

            // If no button found, try to find form and submit it
            WebElement form = driver.findElement(By.tagName("form"));
            return (WebElement) ((JavascriptExecutor) driver).executeScript(
                    "return arguments[0].querySelector('button[type=\"submit\"], " +
                            "input[type=\"submit\"], button.submit, button.btn-primary')", form);
        } catch (Exception e) {
            logger.warn("Error finding submit button", e);
            return null;
        }
    }

    /**
     * Detect data transformations by examining the target page
     */
    private void detectDataTransformations(WebDriver driver, Map<String, String> formData,
            List<DataTransformation> transformations) {
        try {
            // Look for our submitted data in different formats
            for (Map.Entry<String, String> entry : formData.entrySet()) {
                String fieldName = entry.getKey();
                String sourceValue = entry.getValue();

                // Skip empty values
                if (sourceValue == null || sourceValue.isEmpty()) {
                    continue;
                }

                // Look for the value in page content
                String pageSource = driver.getPageSource();
                if (pageSource.contains(sourceValue)) {
                    // Found the value, look for surrounding elements to detect transformation
                    List<WebElement> elements = driver.findElements(By.xpath(
                            "//*[contains(text(), '" + sourceValue + "')]"));

                    for (WebElement element : elements) {
                        // Check if this is a transformed value display
                        String targetValue = element.getText();
                        if (!targetValue.equals(sourceValue) && targetValue.contains(sourceValue)) {
                            // Possible transformation
                            DataTransformation transformation = new DataTransformation();
                            transformation.setSourceField(fieldName);
                            transformation.setSourceValue(sourceValue);
                            transformation.setTargetValue(targetValue);

                            // Try to determine transformation type
                            if (sourceValue.length() < targetValue.length()) {
                                transformation.setTransformationType("expansion");
                            } else if (sourceValue.length() > targetValue.length()) {
                                transformation.setTransformationType("truncation");
                            } else {
                                transformation.setTransformationType("formatting");
                            }

                            transformations.add(transformation);
                        }
                    }
                } else {
                    // Value not found directly, look for transformations
                    lookForCommonTransformations(driver, fieldName, sourceValue, transformations);
                }
            }
        } catch (Exception e) {
            logger.warn("Error detecting data transformations", e);
        }
    }

    /**
     * Look for common data transformations
     */
    private void lookForCommonTransformations(WebDriver driver, String fieldName, String sourceValue,
            List<DataTransformation> transformations) {
        try {
            // Common transformations to check:

            // 1. Date formatting
            if (sourceValue.matches("\\d{4}-\\d{2}-\\d{2}")) {
                // ISO date might be displayed in various formats
                List<String> datePatterns = Arrays.asList(
                        sourceValue.replaceAll("-", "/"),  // YYYY/MM/DD
                        sourceValue.substring(5) + "-" + sourceValue.substring(0, 4), // MM-DD-YYYY
                        sourceValue.substring(5).replaceAll("-", "/") + "/" + sourceValue.substring(0, 4) // MM/DD/YYYY
                );

                for (String pattern : datePatterns) {
                    if (driver.getPageSource().contains(pattern)) {
                        DataTransformation transformation = new DataTransformation();
                        transformation.setSourceField(fieldName);
                        transformation.setSourceValue(sourceValue);
                        transformation.setTargetValue(pattern);
                        transformation.setTransformationType("date_formatting");
                        transformation.setTransformationRule("ISO to formatted date");
                        transformations.add(transformation);
                        break;
                    }
                }
            }

            // 2. Number formatting
            if (sourceValue.matches("\\d+")) {
                // Integer might be displayed with commas or in different formats
                String withCommas = addCommasToNumber(sourceValue);
                if (driver.getPageSource().contains(withCommas)) {
                    DataTransformation transformation = new DataTransformation();
                    transformation.setSourceField(fieldName);
                    transformation.setSourceValue(sourceValue);
                    transformation.setTargetValue(withCommas);
                    transformation.setTransformationType("number_formatting");
                    transformation.setTransformationRule("Add thousands separators");
                    transformations.add(transformation);
                }
            }

            // 3. Case transformations
            String upperCase = sourceValue.toUpperCase();
            if (!sourceValue.equals(upperCase) && driver.getPageSource().contains(upperCase)) {
                DataTransformation transformation = new DataTransformation();
                transformation.setSourceField(fieldName);
                transformation.setSourceValue(sourceValue);
                transformation.setTargetValue(upperCase);
                transformation.setTransformationType("case_formatting");
                transformation.setTransformationRule("Convert to uppercase");
                transformations.add(transformation);
            }

            String lowerCase = sourceValue.toLowerCase();
            if (!sourceValue.equals(lowerCase) && driver.getPageSource().contains(lowerCase)) {
                DataTransformation transformation = new DataTransformation();
                transformation.setSourceField(fieldName);
                transformation.setSourceValue(sourceValue);
                transformation.setTargetValue(lowerCase);
                transformation.setTransformationType("case_formatting");
                transformation.setTransformationRule("Convert to lowercase");
                transformations.add(transformation);
            }
        } catch (Exception e) {
            logger.warn("Error looking for common transformations", e);
        }
    }

    /**
     * Format number with commas
     */
    private String addCommasToNumber(String number) {
        try {
            long value = Long.parseLong(number);
            return String.format("%,d", value);
        } catch (NumberFormatException e) {
            return number;
        }
    }
}