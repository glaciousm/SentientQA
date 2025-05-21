package com.projectoracle.service.crawler;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.projectoracle.config.CrawlerConfig;

import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing WebDriver sessions for test execution and crawling.
 * Provides pooling and reuse of browser instances to improve performance.
 */
@Service
public class WebDriverSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(WebDriverSessionManager.class);

    @Autowired
    private CrawlerConfig crawlerConfig;
    
    // Remove dependency on specific bean instances that might not be available
    // We'll create drivers directly as needed instead

    // Map of session ID to WebDriver instances
    private final Map<String, WebDriverSession> activeSessions = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        try {
            // Setup WebDriverManager for all driver types
            WebDriverManager.chromedriver().setup();
            WebDriverManager.firefoxdriver().setup();
            WebDriverManager.edgedriver().setup();
            logger.info("WebDriverManager initialized for all browser types");
        } catch (Exception e) {
            logger.error("Error initializing WebDriverManager", e);
            logger.info("Will attempt direct driver creation when needed");
        }
    }

    // Available browser types
    public enum BrowserType {
        CHROME,
        FIREFOX,
        EDGE
    }

    /**
     * Class to hold a WebDriver session with metadata
     */
    public static class WebDriverSession {
        private final String id;
        private final WebDriver driver;
        private final BrowserType browserType;
        private final boolean headless;
        private long lastUsed;
        private boolean inUse;

        public WebDriverSession(String id, WebDriver driver, BrowserType browserType, boolean headless) {
            this.id = id;
            this.driver = driver;
            this.browserType = browserType;
            this.headless = headless;
            this.lastUsed = System.currentTimeMillis();
            this.inUse = true;
        }

        public String getId() {
            return id;
        }

        public WebDriver getDriver() {
            return driver;
        }

        public BrowserType getBrowserType() {
            return browserType;
        }

        public boolean isHeadless() {
            return headless;
        }

        public long getLastUsed() {
            return lastUsed;
        }

        public void updateLastUsed() {
            this.lastUsed = System.currentTimeMillis();
        }

        public boolean isInUse() {
            return inUse;
        }

        public void setInUse(boolean inUse) {
            this.inUse = inUse;
        }
    }

    /**
     * Create a new browser session
     *
     * @param browserType the type of browser to create
     * @param headless whether to run in headless mode
     * @return session ID for the new session
     */
    public String createSession(BrowserType browserType, boolean headless) {
        logger.info("Creating new {} session (headless: {})", browserType, headless);

        // Generate a unique session ID
        String sessionId = UUID.randomUUID().toString();

        // Create the appropriate WebDriver
        WebDriver driver;
        switch (browserType) {
            case FIREFOX:
                driver = createFirefoxDriver(headless);
                break;
            case EDGE:
                driver = createEdgeDriver(headless);
                break;
            case CHROME:
            default:
                driver = createChromeDriver(headless);
                break;
        }

        // Configure common settings
        configureDriver(driver);

        // Create and store the session
        WebDriverSession session = new WebDriverSession(sessionId, driver, browserType, headless);
        activeSessions.put(sessionId, session);

        logger.info("Created session: {}", sessionId);
        return sessionId;
    }

    /**
     * Get a WebDriver for an existing session
     *
     * @param sessionId the session ID
     * @return the WebDriver instance or null if not found
     */
    public WebDriver getDriver(String sessionId) {
        WebDriverSession session = activeSessions.get(sessionId);
        if (session != null) {
            // Update last used timestamp
            session.updateLastUsed();
            session.setInUse(true);
            return session.getDriver();
        }
        return null;
    }

    /**
     * Release a session when no longer in use
     *
     * @param sessionId the session ID
     */
    public void releaseSession(String sessionId) {
        WebDriverSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.setInUse(false);
            session.updateLastUsed();
            logger.info("Released session: {}", sessionId);
        }
    }

    /**
     * Close a specific session
     *
     * @param sessionId the session ID
     */
    public void closeSession(String sessionId) {
        WebDriverSession session = activeSessions.remove(sessionId);
        if (session != null) {
            try {
                session.getDriver().quit();
                logger.info("Closed session: {}", sessionId);
            } catch (Exception e) {
                logger.error("Error closing session: {}", sessionId, e);
            }
        }
    }

    /**
     * Get an available session or create a new one
     *
     * @param browserType the preferred browser type
     * @param headless whether to run in headless mode
     * @return session ID
     */
    public String getOrCreateSession(BrowserType browserType, boolean headless) {
        // Look for an available session of the same type
        for (Map.Entry<String, WebDriverSession> entry : activeSessions.entrySet()) {
            WebDriverSession session = entry.getValue();
            if (!session.isInUse() && session.getBrowserType() == browserType
                    && session.isHeadless() == headless) {
                session.setInUse(true);
                session.updateLastUsed();
                logger.info("Reusing existing session: {}", session.getId());
                return session.getId();
            }
        }

        // No available session found, create a new one
        return createSession(browserType, headless);
    }

    /**
     * Create a Chrome WebDriver
     */
    private WebDriver createChromeDriver(boolean headless) {
        try {
            WebDriverManager.chromedriver().setup();
        } catch (Exception e) {
            logger.warn("Error setting up ChromeDriver with WebDriverManager. Will attempt direct driver creation.", e);
        }
        
        ChromeOptions options = new ChromeOptions();

        // Configure browser options
        if (headless) {
            options.addArguments("--headless=new");
        }

        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--disable-extensions");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--user-agent=" + crawlerConfig.getUserAgent());
        
        try {
            // Create and return the driver
            return new ChromeDriver(options);
        } catch (Exception e) {
            logger.error("Failed to create ChromeDriver with standard options", e);
            // Try with minimal options as a fallback
            ChromeOptions minimalOptions = new ChromeOptions();
            minimalOptions.addArguments("--no-sandbox");
            minimalOptions.addArguments("--disable-dev-shm-usage");
            if (headless) {
                minimalOptions.addArguments("--headless=new");
            }
            logger.info("Attempting to create ChromeDriver with minimal options");
            return new ChromeDriver(minimalOptions);
        }
    }

    /**
     * Create a Firefox WebDriver
     */
    private WebDriver createFirefoxDriver(boolean headless) {
        try {
            WebDriverManager.firefoxdriver().setup();
        } catch (Exception e) {
            logger.warn("Error setting up FirefoxDriver with WebDriverManager. Will attempt direct driver creation.", e);
        }
        
        FirefoxOptions options = new FirefoxOptions();

        // Configure browser options
        if (headless) {
            options.addArguments("-headless");
        }

        options.addArguments("-width=1920");
        options.addArguments("-height=1080");
        options.addPreference("general.useragent.override", crawlerConfig.getUserAgent());

        try {
            // Create and return the driver
            return new FirefoxDriver(options);
        } catch (Exception e) {
            logger.error("Failed to create FirefoxDriver with standard options", e);
            // Try with minimal options as a fallback
            FirefoxOptions minimalOptions = new FirefoxOptions();
            if (headless) {
                minimalOptions.addArguments("-headless");
            }
            logger.info("Attempting to create FirefoxDriver with minimal options");
            return new FirefoxDriver(minimalOptions);
        }
    }

    /**
     * Create an Edge WebDriver
     */
    private WebDriver createEdgeDriver(boolean headless) {
        try {
            WebDriverManager.edgedriver().setup();
        } catch (Exception e) {
            logger.warn("Error setting up EdgeDriver with WebDriverManager. Will attempt direct driver creation.", e);
        }
        
        EdgeOptions options = new EdgeOptions();

        // Configure browser options
        if (headless) {
            options.addArguments("--headless=new");
        }

        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--disable-extensions");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--user-agent=" + crawlerConfig.getUserAgent());

        try {
            // Create and return the driver
            return new EdgeDriver(options);
        } catch (Exception e) {
            logger.error("Failed to create EdgeDriver with standard options", e);
            // Try with minimal options as a fallback
            EdgeOptions minimalOptions = new EdgeOptions();
            minimalOptions.addArguments("--no-sandbox");
            minimalOptions.addArguments("--disable-dev-shm-usage");
            if (headless) {
                minimalOptions.addArguments("--headless=new");
            }
            logger.info("Attempting to create EdgeDriver with minimal options");
            return new EdgeDriver(minimalOptions);
        }
    }

    /**
     * Configure common settings for WebDriver
     */
    private void configureDriver(WebDriver driver) {
        // Set timeouts
        driver.manage().timeouts().pageLoadTimeout(Duration.ofMillis(crawlerConfig.getPageLoadTimeoutMs()));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        driver.manage().window().maximize();
    }

    /**
     * Clean up idle sessions periodically
     * This method could be scheduled to run at fixed intervals
     */
    public void cleanupIdleSessions() {
        logger.info("Cleaning up idle sessions");
        long currentTime = System.currentTimeMillis();
        long idleThreshold = 10 * 60 * 1000; // 10 minutes in milliseconds

        // Find and close idle sessions
        for (Map.Entry<String, WebDriverSession> entry : new HashMap<>(activeSessions).entrySet()) {
            WebDriverSession session = entry.getValue();
            if (!session.isInUse() && (currentTime - session.getLastUsed() > idleThreshold)) {
                closeSession(session.getId());
            }
        }
    }

    /**
     * Clean up all sessions when the application shuts down
     */
    @PreDestroy
    public void cleanup() {
        logger.info("Cleaning up all WebDriver sessions");
        for (String sessionId : new HashMap<>(activeSessions).keySet()) {
            closeSession(sessionId);
        }
        activeSessions.clear();
    }
}