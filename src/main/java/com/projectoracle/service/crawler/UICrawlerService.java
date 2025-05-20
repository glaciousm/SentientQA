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

import com.projectoracle.model.ElementFingerprint;
import com.projectoracle.model.Page;
import com.projectoracle.model.UIComponent;
import com.projectoracle.repository.ElementRepository;
import com.projectoracle.config.CrawlerConfig;

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
     * Create a WebDriver instance with appropriate configuration
     *
     * @return configured WebDriver
     */
    private WebDriver createWebDriver() {
        ChromeOptions options = new ChromeOptions();

        // Add browser options for crawling
        options.addArguments("--headless"); // Run in headless mode
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--disable-extensions");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        // Create a new ChromeDriver instance
        return new ChromeDriver(options);
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