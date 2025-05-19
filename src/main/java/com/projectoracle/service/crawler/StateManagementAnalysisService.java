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

/**
 * Service for analyzing application state management across pages.
 * Identifies state persistence mechanisms and tracks state changes.
 * Part of the Data Flow Analysis system described in the roadmap.
 */
@Service
public class StateManagementAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(StateManagementAnalysisService.class);

    @Autowired
    private WebDriverSessionManager webDriverSessionManager;

    @Autowired
    private ElementRepository elementRepository;

    @Autowired
    private CrawlerConfig crawlerConfig;

    /**
     * Represents a state variable detected in the application
     */
    public static class StateVariable {
        private String name;
        private String type;
        private String storageType; // localStorage, sessionStorage, cookie, etc.
        private String initialValue;
        private Map<String, String> valuesOnPages = new HashMap<>();
        private List<StateChangeEvent> changes = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getStorageType() {
            return storageType;
        }

        public void setStorageType(String storageType) {
            this.storageType = storageType;
        }

        public String getInitialValue() {
            return initialValue;
        }

        public void setInitialValue(String initialValue) {
            this.initialValue = initialValue;
        }

        public Map<String, String> getValuesOnPages() {
            return valuesOnPages;
        }

        public void addValueOnPage(String pageUrl, String value) {
            this.valuesOnPages.put(pageUrl, value);
        }

        public List<StateChangeEvent> getChanges() {
            return changes;
        }

        public void addChange(StateChangeEvent change) {
            this.changes.add(change);
        }
    }

    /**
     * Represents a state change event
     */
    public static class StateChangeEvent {
        private String pageUrl;
        private String action;
        private String previousValue;
        private String newValue;
        private long timestamp;

        public StateChangeEvent(String pageUrl, String action, String previousValue, String newValue) {
            this.pageUrl = pageUrl;
            this.action = action;
            this.previousValue = previousValue;
            this.newValue = newValue;
            this.timestamp = System.currentTimeMillis();
        }

        public String getPageUrl() {
            return pageUrl;
        }

        public String getAction() {
            return action;
        }

        public String getPreviousValue() {
            return previousValue;
        }

        public String getNewValue() {
            return newValue;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Represents the results of state management analysis
     */
    public static class StateAnalysisResult {
        private List<StateVariable> stateVariables = new ArrayList<>();
        private Map<String, List<String>> stateFlowByPage = new HashMap<>();
        private List<StateDependency> dependencies = new ArrayList<>();
        private String dominantStatePattern;

        public List<StateVariable> getStateVariables() {
            return stateVariables;
        }

        public void addStateVariable(StateVariable variable) {
            this.stateVariables.add(variable);
        }

        public Map<String, List<String>> getStateFlowByPage() {
            return stateFlowByPage;
        }

        public void addPageStateFlow(String pageUrl, List<String> stateVars) {
            this.stateFlowByPage.put(pageUrl, stateVars);
        }

        public List<StateDependency> getDependencies() {
            return dependencies;
        }

        public void addDependency(StateDependency dependency) {
            this.dependencies.add(dependency);
        }

        public String getDominantStatePattern() {
            return dominantStatePattern;
        }

        public void setDominantStatePattern(String dominantStatePattern) {
            this.dominantStatePattern = dominantStatePattern;
        }
    }

    /**
     * Represents a dependency between state variables
     */
    public static class StateDependency {
        private String sourceVariable;
        private String targetVariable;
        private String relationshipType; // controls, feeds, invalidates, etc.

        public StateDependency(String sourceVariable, String targetVariable, String relationshipType) {
            this.sourceVariable = sourceVariable;
            this.targetVariable = targetVariable;
            this.relationshipType = relationshipType;
        }

        public String getSourceVariable() {
            return sourceVariable;
        }

        public String getTargetVariable() {
            return targetVariable;
        }

        public String getRelationshipType() {
            return relationshipType;
        }
    }

    /**
     * Analyze state management across a collection of pages
     *
     * @param pages the collection of pages from the crawl
     * @return the analysis result
     */
    public StateAnalysisResult analyzeStateManagement(List<Page> pages) {
        logger.info("Analyzing state management across {} pages", pages.size());

        StateAnalysisResult result = new StateAnalysisResult();

        // Create WebDriver session
        String sessionId = webDriverSessionManager.createSession(
                WebDriverSessionManager.BrowserType.CHROME, true);
        WebDriver driver = webDriverSessionManager.getDriver(sessionId);

        try {
            // Discover state storage mechanisms
            Map<String, List<String>> browserStorage = discoverBrowserStorage(driver, pages);
            Map<String, List<String>> cookies = discoverCookies(driver, pages);
            Map<String, List<String>> hiddenFields = discoverHiddenFields(driver, pages);

            // Create state variables from discovered storage
            createStateVariables(result, browserStorage, cookies, hiddenFields);

            // Track state changes as we navigate between pages
            trackStateChanges(driver, result, pages);

            // Identify state dependencies
            identifyStateDependencies(driver, result);

            // Determine dominant state pattern
            determineDominantPattern(result);

            logger.info("State management analysis complete. Found {} state variables.",
                    result.getStateVariables().size());

            return result;
        } catch (Exception e) {
            logger.error("Error analyzing state management", e);
            return result;
        } finally {
            // Release the WebDriver session
            webDriverSessionManager.releaseSession(sessionId);
        }
    }

    /**
     * Discover browser storage (localStorage and sessionStorage)
     */
    private Map<String, List<String>> discoverBrowserStorage(WebDriver driver, List<Page> pages) {
        Map<String, List<String>> storage = new HashMap<>();

        for (Page page : pages) {
            try {
                // Navigate to the page
                driver.get(page.getUrl());

                // Wait for page to load
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

                // Check localStorage
                JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
                Map<String, Object> localStorage = (Map<String, Object>) jsExecutor.executeScript(
                        "return Object.entries(localStorage).reduce((obj, [key, value]) => { obj[key] = value; return obj; }, {})");

                if (localStorage != null && !localStorage.isEmpty()) {
                    for (Map.Entry<String, Object> entry : localStorage.entrySet()) {
                        String key = "localStorage:" + entry.getKey();
                        if (!storage.containsKey(key)) {
                            storage.put(key, new ArrayList<>());
                        }
                        storage.get(key).add(page.getUrl());
                    }
                }

                // Check sessionStorage
                Map<String, Object> sessionStorage = (Map<String, Object>) jsExecutor.executeScript(
                        "return Object.entries(sessionStorage).reduce((obj, [key, value]) => { obj[key] = value; return obj; }, {})");

                if (sessionStorage != null && !sessionStorage.isEmpty()) {
                    for (Map.Entry<String, Object> entry : sessionStorage.entrySet()) {
                        String key = "sessionStorage:" + entry.getKey();
                        if (!storage.containsKey(key)) {
                            storage.put(key, new ArrayList<>());
                        }
                        storage.get(key).add(page.getUrl());
                    }
                }
            } catch (Exception e) {
                logger.warn("Error checking browser storage on page: {}", page.getUrl(), e);
            }
        }

        return storage;
    }

    /**
     * Discover cookies set by the application
     */
    private Map<String, List<String>> discoverCookies(WebDriver driver, List<Page> pages) {
        Map<String, List<String>> cookieMap = new HashMap<>();

        for (Page page : pages) {
            try {
                // Navigate to the page
                driver.get(page.getUrl());

                // Wait for page to load
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

                // Get cookies
                driver.manage().getCookies().forEach(cookie -> {
                    String key = "cookie:" + cookie.getName();
                    if (!cookieMap.containsKey(key)) {
                        cookieMap.put(key, new ArrayList<>());
                    }
                    cookieMap.get(key).add(page.getUrl());
                });
            } catch (Exception e) {
                logger.warn("Error checking cookies on page: {}", page.getUrl(), e);
            }
        }

        return cookieMap;
    }

    /**
     * Discover hidden form fields that might store state
     */
    private Map<String, List<String>> discoverHiddenFields(WebDriver driver, List<Page> pages) {
        Map<String, List<String>> hiddenFields = new HashMap<>();

        for (Page page : pages) {
            try {
                // Navigate to the page
                driver.get(page.getUrl());

                // Wait for page to load
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

                // Find hidden input fields
                List<WebElement> elements = driver.findElements(By.cssSelector("input[type='hidden']"));

                for (WebElement element : elements) {
                    String name = element.getAttribute("name");
                    if (name != null && !name.isEmpty()) {
                        String key = "hiddenField:" + name;
                        if (!hiddenFields.containsKey(key)) {
                            hiddenFields.put(key, new ArrayList<>());
                        }
                        hiddenFields.get(key).add(page.getUrl());
                    }
                }
            } catch (Exception e) {
                logger.warn("Error checking hidden fields on page: {}", page.getUrl(), e);
            }
        }

        return hiddenFields;
    }

    /**
     * Create state variables from discovered storage mechanisms
     */
    private void createStateVariables(
            StateAnalysisResult result,
            Map<String, List<String>> browserStorage,
            Map<String, List<String>> cookies,
            Map<String, List<String>> hiddenFields) {

        // Process browser storage (localStorage and sessionStorage)
        for (Map.Entry<String, List<String>> entry : browserStorage.entrySet()) {
            String key = entry.getKey();
            List<String> pages = entry.getValue();

            if (pages.size() > 1) {  // Only consider variables present on multiple pages
                StateVariable variable = new StateVariable();
                String[] parts = key.split(":", 2);

                variable.setStorageType(parts[0]);
                variable.setName(parts[1]);
                variable.setType("string"); // Default type for browser storage

                // Add to result
                result.addStateVariable(variable);
            }
        }

        // Process cookies
        for (Map.Entry<String, List<String>> entry : cookies.entrySet()) {
            String key = entry.getKey();
            List<String> pages = entry.getValue();

            if (pages.size() > 1) {  // Only consider cookies present on multiple pages
                StateVariable variable = new StateVariable();
                String[] parts = key.split(":", 2);

                variable.setStorageType(parts[0]);
                variable.setName(parts[1]);
                variable.setType("string"); // Default type for cookies

                // Add to result
                result.addStateVariable(variable);
            }
        }

        // Process hidden fields
        for (Map.Entry<String, List<String>> entry : hiddenFields.entrySet()) {
            String key = entry.getKey();
            List<String> pages = entry.getValue();

            StateVariable variable = new StateVariable();
            String[] parts = key.split(":", 2);

            variable.setStorageType(parts[0]);
            variable.setName(parts[1]);
            variable.setType("string"); // Default type for hidden fields

            // Add to result
            result.addStateVariable(variable);
        }
    }

    /**
     * Track state changes as we navigate between pages
     */
    private void trackStateChanges(WebDriver driver, StateAnalysisResult result, List<Page> pages) {
        // Skip if no state variables found
        if (result.getStateVariables().isEmpty()) {
            return;
        }

        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;

        // For each page, track the values of all state variables
        for (Page page : pages) {
            try {
                // Navigate to the page
                driver.get(page.getUrl());

                // Wait for page to load
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

                // Track each state variable on this page
                List<String> pageStateVars = new ArrayList<>();

                for (StateVariable variable : result.getStateVariables()) {
                    String value = null;

                    // Get value based on storage type
                    switch (variable.getStorageType()) {
                        case "localStorage":
                            value = (String) jsExecutor.executeScript(
                                    "return localStorage.getItem(arguments[0])", variable.getName());
                            break;
                        case "sessionStorage":
                            value = (String) jsExecutor.executeScript(
                                    "return sessionStorage.getItem(arguments[0])", variable.getName());
                            break;
                        case "cookie":
                            // Try to get cookie by name
                            try {
                                value = driver.manage().getCookieNamed(variable.getName()).getValue();
                            } catch (Exception e) {
                                // Cookie might not exist on this page
                            }
                            break;
                        case "hiddenField":
                            // Try to find hidden field by name
                            try {
                                WebElement element = driver.findElement(
                                        By.cssSelector("input[type='hidden'][name='" + variable.getName() + "']"));
                                value = element.getAttribute("value");
                            } catch (Exception e) {
                                // Field might not exist on this page
                            }
                            break;
                    }

                    // If value was found, update the variable
                    if (value != null) {
                        // Record the value on this page
                        variable.addValueOnPage(page.getUrl(), value);

                        // Add to page state flow
                        pageStateVars.add(variable.getName());

                        // If this is the first value seen, set it as initial
                        if (variable.getInitialValue() == null) {
                            variable.setInitialValue(value);
                        }
                    }
                }

                // Add page state flow to result
                if (!pageStateVars.isEmpty()) {
                    result.addPageStateFlow(page.getUrl(), pageStateVars);
                }
            } catch (Exception e) {
                logger.warn("Error tracking state changes on page: {}", page.getUrl(), e);
            }
        }

        // Now that we have values for each page, identify changes
        for (StateVariable variable : result.getStateVariables()) {
            Map<String, String> values = variable.getValuesOnPages();
            String previousValue = variable.getInitialValue();
            String previousPage = null;

            for (Map.Entry<String, String> entry : values.entrySet()) {
                String pageUrl = entry.getKey();
                String value = entry.getValue();

                if (previousPage != null && !value.equals(previousValue)) {
                    // State change detected
                    StateChangeEvent event = new StateChangeEvent(pageUrl, "navigation", previousValue, value);
                    variable.addChange(event);
                }

                previousValue = value;
                previousPage = pageUrl;
            }
        }
    }

    /**
     * Identify dependencies between state variables
     */
    private void identifyStateDependencies(WebDriver driver, StateAnalysisResult result) {
        // Skip if not enough state variables
        if (result.getStateVariables().size() < 2) {
            return;
        }

        // For each pair of state variables, check if one depends on the other
        for (int i = 0; i < result.getStateVariables().size(); i++) {
            StateVariable var1 = result.getStateVariables().get(i);

            for (int j = i + 1; j < result.getStateVariables().size(); j++) {
                StateVariable var2 = result.getStateVariables().get(j);

                // Check for corresponding changes
                checkCorrelatedChanges(var1, var2, result);

                // Check for name-based relationships
                checkNameRelationships(var1, var2, result);
            }
        }
    }

    /**
     * Check for correlated changes between variables
     */
    private void checkCorrelatedChanges(StateVariable var1, StateVariable var2, StateAnalysisResult result) {
        List<StateChangeEvent> changes1 = var1.getChanges();
        List<StateChangeEvent> changes2 = var2.getChanges();

        if (changes1.isEmpty() || changes2.isEmpty()) {
            return;
        }

        // Look for changes that occur on the same page within a short time
        for (StateChangeEvent event1 : changes1) {
            for (StateChangeEvent event2 : changes2) {
                if (event1.getPageUrl().equals(event2.getPageUrl()) &&
                        Math.abs(event1.getTimestamp() - event2.getTimestamp()) < 1000) {
                    // Changes are correlated, determine direction
                    if (event1.getTimestamp() < event2.getTimestamp()) {
                        result.addDependency(new StateDependency(var1.getName(), var2.getName(), "impacts"));
                    } else {
                        result.addDependency(new StateDependency(var2.getName(), var1.getName(), "impacts"));
                    }
                    // We found a relationship, no need to check further
                    return;
                }
            }
        }
    }

    /**
     * Check for relationships based on variable names
     */
    private void checkNameRelationships(StateVariable var1, StateVariable var2, StateAnalysisResult result) {
        String name1 = var1.getName().toLowerCase();
        String name2 = var2.getName().toLowerCase();

        // Check for common prefixes/suffixes
        if (name1.startsWith(name2) || name1.endsWith(name2)) {
            result.addDependency(new StateDependency(var2.getName(), var1.getName(), "controls"));
        } else if (name2.startsWith(name1) || name2.endsWith(name1)) {
            result.addDependency(new StateDependency(var1.getName(), var2.getName(), "controls"));
        }

        // Check for common patterns
        String[] relatedPairs = {
                "id:token", "user:session", "form:state", "page:data",
                "current:previous", "parent:child", "master:detail"
        };

        for (String pair : relatedPairs) {
            String[] parts = pair.split(":");

            if ((name1.contains(parts[0]) && name2.contains(parts[1]))) {
                result.addDependency(new StateDependency(var1.getName(), var2.getName(), "feeds"));
            } else if ((name2.contains(parts[0]) && name1.contains(parts[1]))) {
                result.addDependency(new StateDependency(var2.getName(), var1.getName(), "feeds"));
            }
        }
    }

    /**
     * Determine the dominant state management pattern used by the application
     */
    private void determineDominantPattern(StateAnalysisResult result) {
        // Count occurrences of each storage type
        Map<String, Integer> storageCounts = new HashMap<>();

        for (StateVariable variable : result.getStateVariables()) {
            String type = variable.getStorageType();
            storageCounts.put(type, storageCounts.getOrDefault(type, 0) + 1);
        }

        // Find the most common storage type
        String dominantType = null;
        int maxCount = 0;

        for (Map.Entry<String, Integer> entry : storageCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominantType = entry.getKey();
            }
        }

        // Set the dominant pattern
        if (dominantType != null) {
            // Map storage type to pattern
            switch (dominantType) {
                case "localStorage":
                    result.setDominantStatePattern("Client-side persistent storage");
                    break;
                case "sessionStorage":
                    result.setDominantStatePattern("Client-side session storage");
                    break;
                case "cookie":
                    result.setDominantStatePattern("Cookie-based state management");
                    break;
                case "hiddenField":
                    result.setDominantStatePattern("Hidden field state persistence");
                    break;
                default:
                    result.setDominantStatePattern("Unknown pattern");
            }
        } else {
            result.setDominantStatePattern("No dominant pattern detected");
        }
    }
}