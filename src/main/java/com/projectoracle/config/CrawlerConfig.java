package com.projectoracle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration for UI crawler functionality.
 * Controls crawler behavior and resource usage.
 */
@Configuration
@ConfigurationProperties(prefix = "crawler")
@Data
public class CrawlerConfig {

    /**
     * Maximum number of pages to crawl
     */
    private int maxPages = 100;

    /**
     * Maximum depth of crawling from starting URL
     */
    private int maxDepth = 5;

    /**
     * Timeout in milliseconds for page loading
     */
    private int pageLoadTimeoutMs = 30000;

    /**
     * Timeout in minutes for the entire crawl process
     */
    private int crawlTimeoutMinutes = 60;

    /**
     * User agent to use for crawling
     */
    private String userAgent = "Sentinel Crawler / 0.1";

    /**
     * Whether to follow links to external domains
     */
    private boolean followExternalLinks = false;

    /**
     * Whether to ignore URLs containing these patterns
     */
    private String[] excludeUrlPatterns = {
            "logout", "sign-out", "delete", "remove", "/api/", "/auth/",
            "/settings/", "/admin/", "/actuator/", "/metrics/", "/monitoring/"
    };

    /**
     * Whether to respect robots.txt
     */
    private boolean respectRobotsTxt = true;

    /**
     * Delay between requests in milliseconds
     */
    private int delayBetweenRequestsMs = 500;

    /**
     * Maximum number of concurrent crawlers
     */
    private int maxConcurrentCrawlers = 4;

    /**
     * Whether to take screenshots during crawling
     */
    private boolean takeScreenshots = true;

    /**
     * Directory to save screenshots
     */
    private String screenshotDir = "output/screenshots";

    /**
     * Whether to execute JavaScript when crawling
     */
    private boolean executeJavaScript = true;

    /**
     * Whether to simulate user interactions (clicks, hovers)
     */
    private boolean simulateUserInteractions = true;

    /**
     * Viewport width for crawling
     */
    private int viewportWidth = 1366;

    /**
     * Viewport height for crawling
     */
    private int viewportHeight = 768;

    /**
     * Whether to handle authentication
     */
    private boolean handleAuthentication = false;

    /**
     * Username for authentication
     */
    private String username = "";

    /**
     * Password for authentication
     */
    private String password = "";

    /**
     * URL of login page
     */
    private String loginUrl = "";

    /**
     * CSS selector for username field
     */
    private String usernameSelector = "input[name='username']";

    /**
     * CSS selector for password field
     */
    private String passwordSelector = "input[name='password']";

    /**
     * CSS selector for login button
     */
    private String loginButtonSelector = "button[type='submit']";

    /**
     * Whether to store DOM snapshots
     */
    private boolean storeDomSnapshots = true;

    /**
     * Directory to save DOM snapshots
     */
    private String domSnapshotDir = "output/dom-snapshots";

    /**
     * Maximum size in bytes of a page to analyze (to prevent OOM)
     */
    private int maxPageSizeBytes = 5 * 1024 * 1024; // 5MB

    /**
     * Whether to extract CSS styles during analysis
     */
    private boolean extractCssStyles = true;

    /**
     * Whether to analyze form validations
     */
    private boolean analyzeFormValidations = true;

    /**
     * Whether to extract JavaScript event handlers
     */
    private boolean extractEventHandlers = true;

    /**
     * Check if a URL should be excluded from crawling
     *
     * @param url URL to check
     * @return true if URL should be excluded
     */
    public boolean shouldExcludeUrl(String url) {
        if (url == null) {
            return true;
        }

        for (String pattern : excludeUrlPatterns) {
            if (url.contains(pattern)) {
                return true;
            }
        }

        return false;
    }
}