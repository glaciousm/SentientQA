package com.projectoracle.service.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.Select;

import com.projectoracle.model.ElementFingerprint;
import com.projectoracle.model.Page;
import com.projectoracle.model.UIComponent;
import com.projectoracle.model.UserFlow;
import com.projectoracle.repository.ElementRepository;
import com.projectoracle.repository.UserFlowRepository;
import com.projectoracle.config.CrawlerConfig;
import org.openqa.selenium.NoSuchElementException;

import java.time.Duration;

/**
 * Service for crawling web applications to analyze their structure and behavior.
 * Implements the UI Crawler described in the roadmap.
 */
@Service
public class UICrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(UICrawlerService.class);

    @Autowired
    private CrawlerConfig crawlerConfig;

    @Autowired
    private ElementRepository elementRepository;

    @Autowired
    private ElementFingerprintService elementFingerprintService;
    
    @Autowired(required = false)
    private WebDriverAuthenticationService authenticationService;

    @Autowired
    private WebDriverSessionManager webDriverSessionManager;
    
    @Autowired
    private UserFlowRepository userFlowRepository;

    private final Map<String, Page> pageRegistry = new ConcurrentHashMap<>();
    private final Set<String> visitedUrls = Collections.synchronizedSet(new HashSet<>());
    private ExecutorService executorService;
    
    // Flag to control crawling state
    private volatile boolean stopCrawling = false;
    
    // Current crawl ID
    private String currentCrawlId;

    /**
     * Crawl a web application starting from the given URL
     *
     * @param baseUrl the starting URL
     * @param maxPages maximum number of pages to crawl
     * @return the list of discovered pages
     */
    public List<Page> crawlApplication(String baseUrl, int maxPages) {
        return crawlApplication(baseUrl, maxPages, null);
    }

    /**
     * Crawl a web application starting from the given URL with crawl ID
     *
     * @param baseUrl the starting URL
     * @param maxPages maximum number of pages to crawl
     * @param crawlId unique identifier for this crawl operation
     * @return the list of discovered pages
     */
    public List<Page> crawlApplication(String baseUrl, int maxPages, String crawlId) {
        logger.info("Starting application crawl from: {} with ID: {}", baseUrl, crawlId);

        // Clear previous crawl data
        pageRegistry.clear();
        visitedUrls.clear();
        stopCrawling = false;
        currentCrawlId = crawlId;
        
        // Initialize executor service
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        }

        try {
            // Start the crawl process
            crawlPage(baseUrl, baseUrl, maxPages);

            // Wait for all crawl tasks to complete
            executorService.shutdown();
            executorService.awaitTermination(crawlerConfig.getCrawlTimeoutMinutes(), TimeUnit.MINUTES);

            logger.info("Crawl completed. Discovered {} pages", pageRegistry.size());
            return new ArrayList<>(pageRegistry.values());
        } catch (Exception e) {
            logger.error("Error during application crawl", e);
            return new ArrayList<>(pageRegistry.values());
        }
    }

    /**
     * Crawl a specific page and discover links to other pages
     *
     * @param url the page URL
     * @param baseUrl the base URL of the application
     * @param maxPages maximum number of pages to crawl
     */
    private void crawlPage(String url, String baseUrl, int maxPages) {
        // Check if we should stop crawling
        if (stopCrawling) {
            logger.debug("Crawling stopped as requested");
            return;
        }
        
        // Check if we already visited this URL or reached the limit
        if (visitedUrls.contains(url) || visitedUrls.size() >= maxPages) {
            return;
        }

        // Mark URL as visited
        visitedUrls.add(url);

        logger.info("Crawling page: {}", url);

        WebDriver driver = null;
        try {
            // Initialize WebDriver
            driver = createWebDriver();
            
            // Perform login if authentication is enabled, service is available, and this is the first page
            if (crawlerConfig.isHandleAuthentication() && authenticationService != null && visitedUrls.size() == 1) {
                boolean loginSuccess = authenticationService.performLogin(driver);
                if (!loginSuccess) {
                    logger.warn("Authentication failed. Crawling may be limited.");
                }
            }

            // Navigate to the URL
            driver.get(url);

            // Wait for page to load
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // Create page object
            Page page = createPageObject(driver, url);
            pageRegistry.put(url, page);

            // Find all links
            List<String> links = discoverLinks(driver, baseUrl);

            // Schedule crawl tasks for discovered links
            for (String link : links) {
                if (visitedUrls.size() < maxPages && !visitedUrls.contains(link)) {
                    executorService.submit(() -> crawlPage(link, baseUrl, maxPages));
                }
            }

            // Analyze page elements and interactions
            analyzePage(driver, page);

        } catch (Exception e) {
            logger.error("Error crawling page: {}", url, e);
        } finally {
            // Close the WebDriver
            if (driver != null) {
                driver.quit();
            }
        }
    }
    
    /**
     * Explore a page interactively by clicking elements, filling forms, etc.
     * More thorough than the standard crawl.
     * 
     * @param pageUrl the URL of the page to explore
     * @param explorationDepth depth of the exploration
     * @param includeForms whether to interact with forms
     * @param maxInteractions maximum number of interactions to perform
     * @return list of discovered pages
     */
    public List<Page> explorePageInteractively(String pageUrl, int explorationDepth, 
                                            boolean includeForms, int maxInteractions) {
        logger.info("Starting interactive exploration of page: {}", pageUrl);
        
        // Clear previous data but keep pageRegistry for reference
        visitedUrls.clear();
        stopCrawling = false;
        
        WebDriver driver = null;
        List<Page> discoveredPages = new ArrayList<>();
        Set<String> visitedStates = new HashSet<>();
        
        try {
            // Initialize WebDriver
            driver = createWebDriver();
            
            // Perform login if authentication is enabled and service is available
            if (crawlerConfig.isHandleAuthentication() && authenticationService != null) {
                boolean loginSuccess = authenticationService.performLogin(driver);
                if (!loginSuccess) {
                    logger.warn("Authentication failed. Exploration may be limited.");
                }
            }
            
            // Navigate to the starting URL
            driver.get(pageUrl);
            
            // Wait for page to load
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            
            // Create page object for the start page
            Page startPage = createPageObject(driver, pageUrl);
            discoveredPages.add(startPage);
            
            // Start interactive crawling
            discoveredPages.addAll(
                crawlPageInteractively(driver, startPage, 0, explorationDepth, 
                                     visitedStates, includeForms, maxInteractions)
            );
            
            logger.info("Interactive exploration completed. Discovered {} pages/states", discoveredPages.size());
            return discoveredPages;
            
        } catch (Exception e) {
            logger.error("Error during interactive exploration", e);
            return discoveredPages;
        } finally {
            // Close the WebDriver
            if (driver != null) {
                driver.quit();
            }
        }
    }
    
    /**
     * Crawl a page interactively by clicking elements, filling forms, etc.
     * 
     * @param driver WebDriver instance
     * @param page Current page
     * @param currentDepth Current depth of crawling
     * @param maxDepth Maximum depth to crawl
     * @param visitedStates States already visited
     * @param includeForms Whether to interact with forms
     * @param maxInteractions Maximum number of interactions to perform
     * @return List of discovered pages
     */
    private List<Page> crawlPageInteractively(WebDriver driver, Page page, int currentDepth, int maxDepth, 
            Set<String> visitedStates, boolean includeForms, int maxInteractions) {
        List<Page> discoveredPages = new ArrayList<>();
        
        // Add current page to discovered pages
        discoveredPages.add(page);
        
        // If we've reached max depth, stop
        if (currentDepth >= maxDepth) {
            logger.info("Reached max depth of {}", maxDepth);
            return discoveredPages;
        }
        
        // Generate current state fingerprint
        String stateFingerprint = generateStateFingerprint(driver);
        
        // Check if we've already visited this state
        if (visitedStates.contains(stateFingerprint)) {
            logger.info("Already visited state with fingerprint: {}", stateFingerprint);
            return discoveredPages;
        }
        
        // Add this state to visited states
        visitedStates.add(stateFingerprint);
        
        // Get all interactive elements on the page
        logger.info("🔍 SEARCHING for interactive elements on page...");
        List<WebElement> interactiveElements = findInteractiveElements(driver);
        
        logger.info("🎯 Found {} interactive elements", interactiveElements.size());
        
        // Sort elements by priority (important UI components first)
        logger.info("📊 Sorting elements by priority...");
        sortElementsByPriority(interactiveElements);
        
        // Log top elements found
        logger.info("🏆 Top interactive elements found:");
        for (int i = 0; i < Math.min(interactiveElements.size(), 10); i++) {
            String elementInfo = getElementDescription(interactiveElements.get(i));
            logger.info("   {}. {}", i + 1, elementInfo);
        }
        
        // Limit the number of interactions if needed
        if (interactiveElements.size() > maxInteractions) {
            logger.info("⚡ Limiting interactions to {} out of {} elements", 
                    maxInteractions, interactiveElements.size());
            interactiveElements = interactiveElements.subList(0, maxInteractions);
        } else {
            logger.info("📝 Will interact with all {} elements found", interactiveElements.size());
        }
        
        // Save original window handle
        String originalWindowHandle = driver.getWindowHandle();
        String originalUrl = driver.getCurrentUrl();
        
        // Interact with each element
        logger.info("🚀 STARTING INTERACTIONS - Processing {} elements", interactiveElements.size());
        int interactionCount = 0;
        for (WebElement element : interactiveElements) {
            if (interactionCount >= maxInteractions) {
                logger.info("🛑 Reached maximum interaction count of {}", maxInteractions);
                break;
            }
            
            try {
                // Skip invisible elements
                if (!element.isDisplayed()) {
                    logger.debug("👻 Skipping invisible element");
                    continue;
                }
                
                // Determine element type
                String elementType = determineElementType(element);
                String elementInfo = getElementDescription(element);
                
                logger.info("🔄 INTERACTION #{} - Processing: {} (Type: {})", 
                    interactionCount + 1, elementInfo, elementType);
                
                // Skip form elements if includeForms is false
                if (!includeForms && (elementType.startsWith("input:") || 
                                    elementType.equals("select") || 
                                    elementType.equals("textarea"))) {
                    logger.info("📝 Skipping form element (forms disabled): {}", elementInfo);
                    continue;
                }
                
                // Take a screenshot before interaction (optional)
                if (crawlerConfig.isTakeScreenshots()) {
                    takeScreenshot(driver, page.getUrl() + "_before_" + elementType + "_interaction");
                }
                
                // Store current URL and state
                String preInteractionUrl = driver.getCurrentUrl();
                String preInteractionState = generateStateFingerprint(driver);
                
                // Interact with element based on its type
                boolean interacted = false;
                
                switch (elementType) {
                    case "link":
                    case "button":
                        interacted = clickElement(driver, element);
                        break;
                        
                    case "input:text":
                    case "input:email":
                    case "input:password":
                        interacted = typeIntoElement(element, generateAppropriateInput(elementType));
                        break;
                        
                    case "select":
                        interacted = selectOption(element, 1); // Select second option by default
                        break;
                        
                    case "checkbox":
                    case "radio":
                        interacted = clickElement(driver, element);
                        break;
                        
                    default:
                        // For other elements, try clicking
                        interacted = clickElement(driver, element);
                        break;
                }
                
                // If interaction failed, continue to next element
                if (!interacted) {
                    continue;
                }
                
                interactionCount++;
                
                // Check for new window/tab
                Set<String> windowHandles = driver.getWindowHandles();
                if (windowHandles.size() > 1) {
                    // Switch to new window
                    for (String windowHandle : windowHandles) {
                        if (!windowHandle.equals(originalWindowHandle)) {
                            driver.switchTo().window(windowHandle);
                            
                            // Create page for new window
                            Page newPage = createPageObject(driver, driver.getCurrentUrl());
                            discoveredPages.add(newPage);
                            
                            // Record this as a flow
                            recordUserFlow(page, newPage, element, "navigation");
                            
                            // Recursively crawl new window if needed
                            if (currentDepth + 1 < maxDepth) {
                                discoveredPages.addAll(
                                    crawlPageInteractively(driver, newPage, currentDepth + 1, maxDepth, 
                                                       visitedStates, includeForms, maxInteractions)
                                );
                            }
                            
                            // Close new window and switch back to original
                            driver.close();
                            driver.switchTo().window(originalWindowHandle);
                            break;
                        }
                    }
                } else {
                    // Check if URL changed
                    if (!driver.getCurrentUrl().equals(preInteractionUrl)) {
                        // We navigated to a new page
                        Page newPage = createPageObject(driver, driver.getCurrentUrl());
                        discoveredPages.add(newPage);
                        
                        // Record this as a navigation flow
                        recordUserFlow(page, newPage, element, "navigation");
                        
                        // Recursively crawl new page if needed
                        if (currentDepth + 1 < maxDepth) {
                            discoveredPages.addAll(
                                crawlPageInteractively(driver, newPage, currentDepth + 1, maxDepth, 
                                                   visitedStates, includeForms, maxInteractions)
                            );
                        }
                        
                        // Navigate back to original page
                        driver.navigate().back();
                        
                        // Wait for original page to load
                        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                        wait.until(ExpectedConditions.urlToBe(preInteractionUrl));
                    } else {
                        // URL didn't change, but page state might have
                        String newStateFingerprint = generateStateFingerprint(driver);
                        
                        // If state changed significantly
                        if (!newStateFingerprint.equals(preInteractionState)) {
                            // Create a new page object for the new state
                            Page newStatePage = createPageObject(driver, driver.getCurrentUrl());
                            discoveredPages.add(newStatePage);
                            
                            // Record this as a state change flow
                            recordUserFlow(page, newStatePage, element, "state_change");
                            
                            // Recursively crawl new state if needed
                            if (currentDepth + 1 < maxDepth) {
                                discoveredPages.addAll(
                                    crawlPageInteractively(driver, newStatePage, currentDepth + 1, maxDepth, 
                                                       visitedStates, includeForms, maxInteractions)
                                );
                            }
                            
                            // Try to restore original state (may not always work)
                            // For example by clicking the element again or refreshing
                            try {
                                clickElement(driver, element); // Toggle back
                            } catch (Exception e) {
                                // If toggling fails, refresh the page
                                driver.navigate().refresh();
                                
                                // Wait for page to load
                                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
                            }
                        }
                    }
                }
                
            } catch (Exception e) {
                logger.error("Error interacting with element", e);
                
                // Try to get back to the original URL if something went wrong
                try {
                    if (!driver.getCurrentUrl().equals(originalUrl)) {
                        driver.navigate().to(originalUrl);
                    }
                } catch (Exception ex) {
                    logger.error("Failed to navigate back to original URL", ex);
                }
            }
        }
        
        return discoveredPages;
    }
    
    /**
     * Clicks an element and waits for page changes
     * 
     * @param driver WebDriver instance
     * @param element Element to click
     * @return true if successful, false otherwise
     */
    private boolean clickElement(WebDriver driver, WebElement element) {
        try {
            // Store current URL to detect navigation
            String currentUrl = driver.getCurrentUrl();
            String currentPageSource = driver.getPageSource().length() > 1000 ? 
                    driver.getPageSource().substring(0, 1000) : driver.getPageSource();
            
            // Log what we're about to click
            String elementInfo = getElementDescription(element);
            logger.info("🖱️  CLICKING ELEMENT: {}", elementInfo);
            logger.info("📍 Current URL: {}", currentUrl);
            
            // Scroll to element to ensure it's visible
            logger.info("📜 Scrolling to element...");
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", element);
            
            // Wait a moment for scroll to complete
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Highlight element before clicking (for visual feedback)
            try {
                ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].style.border='3px solid red'; arguments[0].style.backgroundColor='yellow';", element);
                Thread.sleep(1000); // Hold highlight for 1 second
            } catch (Exception e) {
                // Ignore highlighting errors
            }
            
            // Click the element
            logger.info("🎯 Performing click...");
            element.click();
            
            // Wait for page changes
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            // Wait for either URL change or page content change
            try {
                wait.until(driver1 -> {
                    // Check if URL changed
                    if (!driver1.getCurrentUrl().equals(currentUrl)) {
                        logger.info("✅ URL CHANGED: {} -> {}", currentUrl, driver1.getCurrentUrl());
                        return true;
                    }
                    
                    // Check if page content changed significantly
                    String newPageSource = driver1.getPageSource().length() > 1000 ? 
                            driver1.getPageSource().substring(0, 1000) : driver1.getPageSource();
                    if (!newPageSource.equals(currentPageSource)) {
                        logger.info("✅ PAGE CONTENT CHANGED after click");
                        return true;
                    }
                    return false;
                });
            } catch (Exception e) {
                // Timeout or other error, but the click might still have had an effect
                logger.warn("⏰ No significant change detected after clicking element: {}", e.getMessage());
            }
            
            logger.info("✅ Click completed successfully");
            return true;
        } catch (Exception e) {
            logger.error("Error clicking element", e);
            return false;
        }
    }

    /**
     * Types text into an input field
     * 
     * @param element Input element
     * @param text Text to type
     * @return true if successful, false otherwise
     */
    private boolean typeIntoElement(WebElement element, String text) {
        try {
            String elementInfo = getElementDescription(element);
            logger.info("⌨️  TYPING INTO ELEMENT: {}", elementInfo);
            logger.info("📝 Text to type: '{}'", text);
            
            // Highlight element before typing (for visual feedback)
            try {
                WebDriver driver = ((org.openqa.selenium.WrapsDriver) element).getWrappedDriver();
                ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].style.border='3px solid blue'; arguments[0].style.backgroundColor='lightblue';", element);
                Thread.sleep(500);
            } catch (Exception e) {
                // Ignore highlighting errors
            }
            
            // Clear existing text
            logger.info("🧹 Clearing existing text...");
            element.clear();
            
            // Type the text
            logger.info("⌨️  Typing text...");
            element.sendKeys(text);
            
            logger.info("✅ Text entered successfully");
            return true;
        } catch (Exception e) {
            logger.error("❌ Error typing into element: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Selects an option from a dropdown
     * 
     * @param element Select element
     * @param optionIndex Index of option to select
     * @return true if successful, false otherwise
     */
    private boolean selectOption(WebElement element, int optionIndex) {
        try {
            String elementInfo = getElementDescription(element);
            logger.info("🔽 SELECTING FROM DROPDOWN: {}", elementInfo);
            logger.info("🎯 Option index to select: {}", optionIndex);
            
            Select select = new Select(element);
            
            // Get all options
            List<WebElement> options = select.getOptions();
            logger.info("📋 Available options: {}", options.size());
            
            // Log all available options
            for (int i = 0; i < Math.min(options.size(), 5); i++) {
                logger.info("   {}. {}", i, options.get(i).getText());
            }
            if (options.size() > 5) {
                logger.info("   ... and {} more options", options.size() - 5);
            }
            
            // Check if index is valid
            if (optionIndex >= 0 && optionIndex < options.size()) {
                String optionText = options.get(optionIndex).getText();
                logger.info("🎯 Selecting option: '{}'", optionText);
                
                // Select by index
                select.selectByIndex(optionIndex);
                
                logger.info("✅ Option selected successfully");
                return true;
            } else {
                logger.warn("❌ Invalid option index: {}, max: {}", optionIndex, options.size() - 1);
                return false;
            }
        } catch (Exception e) {
            logger.error("❌ Error selecting option: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Generate appropriate input based on field type
     */
    private String generateAppropriateInput(String elementType) {
        switch (elementType) {
            case "input:email":
                return "test@example.com";
            case "input:password":
                return "Password123!";
            case "input:number":
                return "123";
            case "input:date":
                return "2025-01-01";
            case "input:tel":
                return "1234567890";
            default:
                return "Test Input";
        }
    }

    /**
     * Find all interactive elements on a page
     */
    private List<WebElement> findInteractiveElements(WebDriver driver) {
        List<WebElement> interactiveElements = new ArrayList<>();
        
        try {
            // Find links
            interactiveElements.addAll(driver.findElements(By.tagName("a")));
            
            // Find buttons
            interactiveElements.addAll(driver.findElements(By.tagName("button")));
            interactiveElements.addAll(driver.findElements(By.cssSelector("input[type='button'], input[type='submit']")));
            
            // Find text inputs
            interactiveElements.addAll(driver.findElements(By.cssSelector("input[type='text'], input[type='email'], input[type='password'], input[type='search']")));
            
            // Find selects
            interactiveElements.addAll(driver.findElements(By.tagName("select")));
            
            // Find checkboxes and radios
            interactiveElements.addAll(driver.findElements(By.cssSelector("input[type='checkbox'], input[type='radio']")));
            
            // Find other clickables with JS event handlers
            interactiveElements.addAll(driver.findElements(By.cssSelector("[onclick], [data-toggle], [role=button], .btn, .button")));
        } catch (Exception e) {
            logger.error("Error finding interactive elements", e);
        }
        
        return interactiveElements;
    }

    /**
     * Sort elements by priority (important UI elements first)
     */
    private void sortElementsByPriority(List<WebElement> elements) {
        elements.sort((e1, e2) -> {
            int priority1 = getElementPriority(e1);
            int priority2 = getElementPriority(e2);
            return Integer.compare(priority1, priority2);
        });
    }

    /**
     * Get priority score for an element (lower is higher priority)
     */
    private int getElementPriority(WebElement element) {
        String tagName = element.getTagName().toLowerCase();
        String type = element.getAttribute("type");
        
        // Buttons and links are high priority
        if (tagName.equals("button") || 
            (tagName.equals("input") && (type != null && (type.equals("button") || type.equals("submit"))))) {
            return 1;
        }
        
        // Links are medium priority
        if (tagName.equals("a")) {
            // Links with IDs or text are higher priority
            if (element.getAttribute("id") != null || !element.getText().trim().isEmpty()) {
                return 2;
            }
            return 3;
        }
        
        // Form inputs are medium-low priority
        if (tagName.equals("input") || tagName.equals("select") || tagName.equals("textarea")) {
            return 4;
        }
        
        // Everything else is low priority
        return 5;
    }

    /**
     * Determine element type for interaction purposes
     */
    private String determineElementType(WebElement element) {
        String tagName = element.getTagName().toLowerCase();
        String type = element.getAttribute("type");
        
        if (tagName.equals("a")) {
            return "link";
        }
        
        if (tagName.equals("button") || 
            (tagName.equals("input") && (type != null && (type.equals("button") || type.equals("submit"))))) {
            return "button";
        }
        
        if (tagName.equals("input")) {
            if (type == null) {
                return "input:text";
            }
            return "input:" + type;
        }
        
        if (tagName.equals("select")) {
            return "select";
        }
        
        return tagName;
    }

    /**
     * Generate a fingerprint for the current page state
     */
    private String generateStateFingerprint(WebDriver driver) {
        // Simplified state fingerprinting - in a real implementation, this would be more sophisticated
        // to detect UI state changes even when URL doesn't change
        
        try {
            // Get visible text content
            String visibleText = driver.findElement(By.tagName("body")).getText();
            
            // Get current URL
            String url = driver.getCurrentUrl();
            
            // Count visible interactive elements
            int interactiveElementCount = findInteractiveElements(driver).size();
            
            // Generate a simple fingerprint
            return url + "_" + visibleText.hashCode() + "_" + interactiveElementCount;
        } catch (Exception e) {
            logger.error("Error generating state fingerprint", e);
            return driver.getCurrentUrl() + "_" + System.currentTimeMillis();
        }
    }

    /**
     * Record a user flow between two pages
     */
    private void recordUserFlow(Page sourcePage, Page targetPage, WebElement interactedElement, String flowType) {
        try {
            // Create a flow record
            UserFlow flow = new UserFlow();
            flow.setSourcePageId(sourcePage.getId());
            flow.setTargetPageId(targetPage.getId());
            flow.setFlowType(flowType);
            flow.setInteractionType(determineElementType(interactedElement));
            flow.setInteractionDescription(getElementDescription(interactedElement));
            flow.setElementSelector(createXPath(interactedElement));
            flow.setDiscoveryTimestamp(System.currentTimeMillis());
            
            // Set form submission flag if applicable
            if (flowType.equals("form_submission") || 
                    (interactedElement.getTagName().equalsIgnoreCase("input") && 
                     interactedElement.getAttribute("type") != null &&
                     interactedElement.getAttribute("type").equalsIgnoreCase("submit"))) {
                flow.setFormSubmission(true);
            }
            
            // Set source and target pages
            flow.setSourcePage(sourcePage);
            flow.setTargetPage(targetPage);
            
            // Save the flow
            userFlowRepository.saveFlow(flow);
            
            logger.info("Recorded user flow: {} -> {} via {}", 
                    sourcePage.getUrl(), targetPage.getUrl(), flow.getInteractionDescription());
        } catch (Exception e) {
            logger.error("Error recording user flow", e);
        }
    }

    /**
     * Get a human-readable description of an element
     */
    private String getElementDescription(WebElement element) {
        String tagName = element.getTagName();
        String id = element.getAttribute("id");
        String text = element.getText();
        String type = element.getAttribute("type");
        String name = element.getAttribute("name");
        
        StringBuilder description = new StringBuilder();
        description.append(tagName);
        
        if (id != null && !id.isEmpty()) {
            description.append("#").append(id);
        }
        
        if (type != null && !type.isEmpty()) {
            description.append("[type=").append(type).append("]");
        }
        
        if (name != null && !name.isEmpty()) {
            description.append("[name=").append(name).append("]");
        }
        
        if (text != null && !text.isEmpty() && text.length() < 30) {
            description.append(": \"").append(text).append("\"");
        }
        
        return description.toString();
    }

    /**
     * Create a WebDriver instance with appropriate configuration
     *
     * @return configured WebDriver
     */
    private WebDriver createWebDriver() {
        try {
            // Try to use the session manager to create a WebDriver session
            String sessionId = webDriverSessionManager.getOrCreateSession(
                    WebDriverSessionManager.BrowserType.CHROME, 
                    !crawlerConfig.isTakeScreenshots() // Use headless mode if not taking screenshots
            );
            return webDriverSessionManager.getDriver(sessionId);
        } catch (Exception e) {
            // Fallback to direct creation if session manager fails
            logger.warn("Failed to get WebDriver from session manager, creating directly", e);
            
            ChromeOptions options = new ChromeOptions();

            // Add browser options for crawling - DISABLE HEADLESS MODE FOR MONITORING
            logger.info("🌐 Configuring Chrome browser (VISIBLE MODE for monitoring)");
            // Never use headless mode - user wants to see the crawling
            // options.addArguments("--headless=new"); // DISABLED for user monitoring
            
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=" + crawlerConfig.getViewportWidth() + "," + 
                    crawlerConfig.getViewportHeight());
            options.addArguments("--ignore-certificate-errors");
            options.addArguments("--disable-extensions");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--user-agent=" + crawlerConfig.getUserAgent());
            
            // Add options for better visibility
            options.addArguments("--start-maximized");
            options.addArguments("--disable-web-security");
            options.addArguments("--allow-running-insecure-content");
            
            logger.info("🚀 Launching Chrome browser...");

            // Create a new ChromeDriver instance
            return new ChromeDriver(options);
        }
    }

    /**
     * Create a Page object representing the current page
     *
     * @param driver WebDriver instance
     * @param url the page URL
     * @return Page object
     */
    private Page createPageObject(WebDriver driver, String url) {
        String title = driver.getTitle();
        String pageSource = driver.getPageSource();

        // Create page with basic metadata
        Page page = new Page();
        page.setId(UUID.randomUUID());
        page.setUrl(url);
        page.setTitle(title);
        page.setDiscoveryTimestamp(System.currentTimeMillis());

        // Extract metadata
        try {
            WebElement metaDescription = driver.findElement(By.cssSelector("meta[name='description']"));
            if (metaDescription != null) {
                page.setDescription(metaDescription.getAttribute("content"));
            }
        } catch (Exception e) {
            // No description meta tag, ignore
        }

        return page;
    }

    /**
     * Discover links to other pages in the application
     *
     * @param driver WebDriver instance
     * @param baseUrl the base URL of the application
     * @return list of discovered URLs
     */
    private List<String> discoverLinks(WebDriver driver, String baseUrl) {
        List<String> links = new ArrayList<>();

        try {
            // Find all <a> elements
            List<WebElement> anchorElements = driver.findElements(By.tagName("a"));

            // Extract and normalize links
            for (WebElement anchor : anchorElements) {
                String href = anchor.getAttribute("href");

                // Skip if href is empty, null, or JavaScript
                if (href == null || href.isEmpty() || href.startsWith("javascript:") || href.startsWith("#")) {
                    continue;
                }

                // Normalize the URL
                String normalizedUrl = normalizeUrl(href, baseUrl);

                // Make sure link is within the same domain
                if (normalizedUrl != null && normalizedUrl.startsWith(baseUrl)) {
                    links.add(normalizedUrl);
                }
            }
        } catch (Exception e) {
            logger.error("Error discovering links", e);
        }

        return links;
    }

    /**
     * Normalize a URL to ensure it's absolute and remove fragments
     *
     * @param url the URL to normalize
     * @param baseUrl the base URL of the application
     * @return normalized URL
     */
    private String normalizeUrl(String url, String baseUrl) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // Convert relative URL to absolute
        if (url.startsWith("/")) {
            url = baseUrl + (baseUrl.endsWith("/") ? url.substring(1) : url);
        } else if (!url.startsWith("http")) {
            url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + url;
        }

        // Remove fragment
        int fragmentIndex = url.indexOf("#");
        if (fragmentIndex > 0) {
            url = url.substring(0, fragmentIndex);
        }

        // Remove trailing slash
        if (url.endsWith("/") && url.length() > baseUrl.length()) {
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }

    /**
     * Analyze the page structure and extract interactive elements
     *
     * @param driver WebDriver instance
     * @param page Page object to update with analysis results
     */
    private void analyzePage(WebDriver driver, Page page) {
        try {
            // Find all interactive elements
            List<UIComponent> components = new ArrayList<>();

            // Find form elements
            analyzeFormElements(driver, components);

            // Find buttons
            analyzeButtons(driver, components);

            // Find other interactive elements
            analyzeInteractiveElements(driver, components);

            // Update page with components
            page.setComponents(components);

            // Create element fingerprints for all components
            for (UIComponent component : components) {
                ElementFingerprint fingerprint = elementFingerprintService.createFingerprint(
                        driver, component.getElementLocator());
                component.setFingerprint(fingerprint);

                // Store fingerprint in repository
                elementRepository.saveFingerprint(fingerprint);
            }

            logger.info("Analyzed page: {} - Found {} components", page.getUrl(), components.size());
        } catch (Exception e) {
            logger.error("Error analyzing page", e);
        }
    }

    /**
     * Analyze form elements on the page
     *
     * @param driver WebDriver instance
     * @param components list to update with found components
     */
    private void analyzeFormElements(WebDriver driver, List<UIComponent> components) {
        try {
            // Find all form input elements
            List<WebElement> inputElements = driver.findElements(By.tagName("input"));
            List<WebElement> selectElements = driver.findElements(By.tagName("select"));
            List<WebElement> textareaElements = driver.findElements(By.tagName("textarea"));

            // Process input elements
            for (WebElement input : inputElements) {
                String type = input.getAttribute("type");
                String name = input.getAttribute("name");
                String id = input.getAttribute("id");

                // Skip hidden inputs
                if ("hidden".equals(type)) {
                    continue;
                }

                UIComponent component = new UIComponent();
                component.setType("input");
                component.setSubtype(type != null ? type : "text");
                component.setName(name);
                component.setId(id);
                component.setElementLocator(createXPath(input));

                components.add(component);
            }

            // Process select elements
            for (WebElement select : selectElements) {
                String name = select.getAttribute("name");
                String id = select.getAttribute("id");

                UIComponent component = new UIComponent();
                component.setType("select");
                component.setName(name);
                component.setId(id);
                component.setElementLocator(createXPath(select));

                // Extract options
                List<WebElement> options = select.findElements(By.tagName("option"));
                List<String> optionValues = new ArrayList<>();

                for (WebElement option : options) {
                    optionValues.add(option.getAttribute("value"));
                }

                component.setOptions(optionValues);
                components.add(component);
            }

            // Process textarea elements
            for (WebElement textarea : textareaElements) {
                String name = textarea.getAttribute("name");
                String id = textarea.getAttribute("id");

                UIComponent component = new UIComponent();
                component.setType("textarea");
                component.setName(name);
                component.setId(id);
                component.setElementLocator(createXPath(textarea));

                components.add(component);
            }
        } catch (Exception e) {
            logger.error("Error analyzing form elements", e);
        }
    }

    /**
     * Analyze button elements on the page
     *
     * @param driver WebDriver instance
     * @param components list to update with found components
     */
    private void analyzeButtons(WebDriver driver, List<UIComponent> components) {
        try {
            // Find all button elements
            List<WebElement> buttonElements = driver.findElements(By.tagName("button"));
            List<WebElement> inputButtons = driver.findElements(By.xpath("//input[@type='button' or @type='submit']"));

            // Process button elements
            for (WebElement button : buttonElements) {
                String text = button.getText();
                String id = button.getAttribute("id");

                UIComponent component = new UIComponent();
                component.setType("button");
                component.setName(text);
                component.setId(id);
                component.setElementLocator(createXPath(button));

                components.add(component);
            }

            // Process input buttons
            for (WebElement button : inputButtons) {
                String value = button.getAttribute("value");
                String id = button.getAttribute("id");
                String type = button.getAttribute("type");

                UIComponent component = new UIComponent();
                component.setType("button");
                component.setSubtype(type);
                component.setName(value);
                component.setId(id);
                component.setElementLocator(createXPath(button));

                components.add(component);
            }
        } catch (Exception e) {
            logger.error("Error analyzing button elements", e);
        }
    }

    /**
     * Analyze other interactive elements on the page
     *
     * @param driver WebDriver instance
     * @param components list to update with found components
     */
    private void analyzeInteractiveElements(WebDriver driver, List<UIComponent> components) {
        try {
            // Find elements with click handlers
            List<WebElement> clickableElements = driver.findElements(
                    By.cssSelector("[onclick], [data-toggle], [role=button], .btn, .button"));

            for (WebElement element : clickableElements) {
                // Skip if already captured as button
                if (element.getTagName().equals("button") ||
                        (element.getTagName().equals("input") &&
                                ("button".equals(element.getAttribute("type")) ||
                                        "submit".equals(element.getAttribute("type"))))) {
                    continue;
                }

                String text = element.getText();
                String id = element.getAttribute("id");
                String className = element.getAttribute("class");

                UIComponent component = new UIComponent();
                component.setType("interactive");
                component.setSubtype("clickable");
                component.setName(text);
                component.setId(id);
                component.setClassName(className);
                component.setElementLocator(createXPath(element));

                components.add(component);
            }
        } catch (Exception e) {
            logger.error("Error analyzing interactive elements", e);
        }
    }

    /**
     * Create an XPath locator for an element
     *
     * @param element WebElement to create XPath for
     * @return XPath string
     */
    private String createXPath(WebElement element) {
        // This is a simplified implementation
        // In a production system, we would use a more robust approach

        String id = element.getAttribute("id");
        if (id != null && !id.isEmpty()) {
            return "//*[@id='" + id + "']";
        }

        String name = element.getAttribute("name");
        if (name != null && !name.isEmpty()) {
            return "//" + element.getTagName() + "[@name='" + name + "']";
        }

        // Fallback to a basic XPath
        return getElementXPath(element);
    }

    /**
     * Generate a full XPath for an element
     *
     * @param element WebElement to create XPath for
     * @return XPath string
     */
    private String getElementXPath(WebElement element) {
        // This is a placeholder implementation
        // In a real system, we would use a more sophisticated approach
        // to generate a robust XPath

        return "(//tagname)[1]".replace("tagname", element.getTagName());
    }
    
    /**
     * Take a screenshot of the current page
     */
    private void takeScreenshot(WebDriver driver, String name) {
        if (!crawlerConfig.isTakeScreenshots()) {
            return;
        }
        
        try {
            // Take screenshot
            org.openqa.selenium.OutputType<byte[]> outputType = org.openqa.selenium.OutputType.BYTES;
            byte[] screenshotBytes = ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(outputType);
            
            // Save to file
            java.nio.file.Path screenshotDir = java.nio.file.Paths.get(crawlerConfig.getScreenshotDir());
            if (!java.nio.file.Files.exists(screenshotDir)) {
                java.nio.file.Files.createDirectories(screenshotDir);
            }
            
            // Sanitize filename
            String filename = name.replaceAll("[^a-zA-Z0-9\\._-]", "_") + ".png";
            java.nio.file.Path screenshotPath = screenshotDir.resolve(filename);
            
            // Write screenshot to file
            java.nio.file.Files.write(screenshotPath, screenshotBytes);
            
            logger.info("Saved screenshot to {}", screenshotPath);
        } catch (Exception e) {
            logger.error("Error taking screenshot", e);
        }
    }
    
    /**
     * Stop the current crawl operation
     * 
     * @param crawlId the ID of the crawl to stop (if it matches the current crawl)
     * @return true if crawl was stopped, false if not active or ID doesn't match
     */
    public boolean stopCrawl(String crawlId) {
        if (currentCrawlId != null && currentCrawlId.equals(crawlId)) {
            logger.info("Stopping crawl with ID: {}", crawlId);
            stopCrawling = true;
            
            // Try to initiate an orderly shutdown of the executor
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdownNow();
            }
            
            return true;
        }
        return false;
    }
    
    /**
     * Check if a crawl is currently active
     * 
     * @return true if a crawl is in progress
     */
    public boolean isCrawlActive() {
        return executorService != null && !executorService.isTerminated() && !stopCrawling;
    }
    
    /**
     * Get the current results of the crawl (can be called while crawling is in progress)
     * 
     * @return the list of pages discovered so far
     */
    public List<Page> getCurrentResults() {
        return new ArrayList<>(pageRegistry.values());
    }
}