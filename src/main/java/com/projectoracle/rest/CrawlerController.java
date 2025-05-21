package com.projectoracle.rest;

import com.projectoracle.model.Page;
import com.projectoracle.service.crawler.UICrawlerService;
import com.projectoracle.config.CrawlerConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST controller for UI crawler endpoints.
 * Provides API access to web application crawling functionality.
 */
@RestController
@RequestMapping("/api/v1/crawler")
public class CrawlerController {

    private static final Logger logger = LoggerFactory.getLogger(CrawlerController.class);

    @Autowired
    private UICrawlerService crawlerService;

    @Autowired
    private CrawlerConfig crawlerConfig;

    private final Map<String, CrawlStatus> activeCrawls = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    /**
     * Start crawling a web application
     */
    @PostMapping("/start")
    public ResponseEntity<CrawlResponse> startCrawl(@RequestBody CrawlRequest request) {
        if (request.getBaseUrl() == null || request.getBaseUrl().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new CrawlResponse("error", "Base URL is required", null, null)
            );
        }

        String crawlId = generateCrawlId(request.getBaseUrl());

        // Check if there's already an active crawl for this URL
        if (activeCrawls.containsKey(crawlId)) {
            CrawlStatus status = activeCrawls.get(crawlId);
            if (!status.isCompleted()) {
                return ResponseEntity.ok(
                        new CrawlResponse("active", "Crawl already in progress", crawlId, status)
                );
            }
        }
        
        // Set authentication parameters in the crawler config if provided
        if (request.isEnableAuthentication()) {
            crawlerConfig.setHandleAuthentication(true);
            
            if (request.getUsername() != null && !request.getUsername().isEmpty()) {
                crawlerConfig.setUsername(request.getUsername());
            }
            
            if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                crawlerConfig.setPassword(request.getPassword());
            }
            
            if (request.getLoginUrl() != null && !request.getLoginUrl().isEmpty()) {
                crawlerConfig.setLoginUrl(request.getLoginUrl());
            }
            
            logger.info("Authentication enabled for crawl: {}", crawlId);
        } else {
            crawlerConfig.setHandleAuthentication(false);
        }

        // Create new crawl status
        CrawlStatus status = new CrawlStatus();
        status.setBaseUrl(request.getBaseUrl());
        status.setStartTime(System.currentTimeMillis());
        status.setStatus("initializing");
        status.setAuthenticationEnabled(request.isEnableAuthentication());
        activeCrawls.put(crawlId, status);

        // Get max pages from request or use default
        int maxPages = request.getMaxPages() > 0 ?
                request.getMaxPages() :
                crawlerConfig.getMaxPages();

        // Start crawl in background
        CompletableFuture.runAsync(() -> {
            try {
                status.setStatus("running");

                List<Page> pages = crawlerService.crawlApplication(request.getBaseUrl(), maxPages, crawlId);

                // Update status
                status.setStatus("completed");
                status.setEndTime(System.currentTimeMillis());
                status.setPagesDiscovered(pages.size());
                status.setCompleted(true);

                // Calculate completion metrics
                long duration = status.getEndTime() - status.getStartTime();
                status.setDurationMs(duration);

                logger.info("Crawl completed: {} pages discovered in {} ms",
                        pages.size(), duration);

            } catch (Exception e) {
                logger.error("Error during crawl", e);
                status.setStatus("error");
                status.setErrorMessage(e.getMessage());
                status.setEndTime(System.currentTimeMillis());
                status.setCompleted(true);
            }
        }, executorService);

        return ResponseEntity.accepted().body(
                new CrawlResponse("started", "Crawl started", crawlId, status)
        );
    }

    /**
     * Get the status of a crawl
     */
    @GetMapping("/status/{crawlId}")
    public ResponseEntity<CrawlResponse> getCrawlStatus(@PathVariable String crawlId) {
        CrawlStatus status = activeCrawls.get(crawlId);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                new CrawlResponse(
                        status.getStatus(),
                        status.isCompleted() ? "Crawl completed" : "Crawl in progress",
                        crawlId,
                        status
                )
        );
    }

    /**
     * List all active and recent crawls
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, CrawlStatus>> listCrawls() {
        return ResponseEntity.ok(new HashMap<>(activeCrawls));
    }

    /**
     * Stop an active crawl
     */
    @PostMapping("/stop/{crawlId}")
    public ResponseEntity<CrawlResponse> stopCrawl(@PathVariable String crawlId) {
        CrawlStatus status = activeCrawls.get(crawlId);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        if (status.isCompleted()) {
            return ResponseEntity.ok(
                    new CrawlResponse("completed", "Crawl already completed", crawlId, status)
            );
        }

        // Set status to stopping
        status.setStatus("stopping");

        // Call crawler service to stop the crawl
        boolean stopped = crawlerService.stopCrawl(crawlId);
        
        if (stopped) {
            logger.info("Successfully stopped crawl: {}", crawlId);
            
            // Update status information
            status.setStatus("stopped");
            status.setCompleted(true);
            status.setEndTime(System.currentTimeMillis());
            status.setDurationMs(status.getEndTime() - status.getStartTime());
            
            return ResponseEntity.ok(
                    new CrawlResponse("stopped", "Crawl has been stopped", crawlId, status)
            );
        } else {
            // Could not stop the crawl for some reason
            logger.warn("Failed to stop crawl: {}", crawlId);
            status.setStatus("running");
            
            return ResponseEntity.accepted().body(
                    new CrawlResponse("running", "Failed to stop crawl, it's still running", crawlId, status)
            );
        }
    }

    /**
     * Get results of a completed crawl
     */
    @GetMapping("/results/{crawlId}")
    public ResponseEntity<List<Page>> getCrawlResults(@PathVariable String crawlId) {
        CrawlStatus status = activeCrawls.get(crawlId);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        // Retrieve current results - works for both completed and in-progress crawls
        List<Page> results = crawlerService.getCurrentResults();
        
        // If crawl is still in progress, return 202 Accepted status
        if (!status.isCompleted()) {
            return ResponseEntity.accepted().body(results);
        }

        return ResponseEntity.ok(results);
    }

    /**
     * Generate a crawl ID based on the base URL
     */
    private String generateCrawlId(String baseUrl) {
        return "crawl_" + baseUrl.hashCode() + "_" + System.currentTimeMillis();
    }

    /**
     * Request object for starting a crawl
     */
    public static class CrawlRequest {
        private String baseUrl;
        private int maxPages;
        private Map<String, String> customConfig;
        private boolean enableAuthentication;
        private String username;
        private String password;
        private String loginUrl;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(int maxPages) {
            this.maxPages = maxPages;
        }

        public Map<String, String> getCustomConfig() {
            return customConfig;
        }

        public void setCustomConfig(Map<String, String> customConfig) {
            this.customConfig = customConfig;
        }
        
        public boolean isEnableAuthentication() {
            return enableAuthentication;
        }
        
        public void setEnableAuthentication(boolean enableAuthentication) {
            this.enableAuthentication = enableAuthentication;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
        
        public String getLoginUrl() {
            return loginUrl;
        }
        
        public void setLoginUrl(String loginUrl) {
            this.loginUrl = loginUrl;
        }
    }

    /**
     * Response object for crawl operations
     */
    public static class CrawlResponse {
        private String status;
        private String message;
        private String crawlId;
        private CrawlStatus crawlStatus;

        public CrawlResponse(String status, String message, String crawlId, CrawlStatus crawlStatus) {
            this.status = status;
            this.message = message;
            this.crawlId = crawlId;
            this.crawlStatus = crawlStatus;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getCrawlId() {
            return crawlId;
        }

        public void setCrawlId(String crawlId) {
            this.crawlId = crawlId;
        }

        public CrawlStatus getCrawlStatus() {
            return crawlStatus;
        }

        public void setCrawlStatus(CrawlStatus crawlStatus) {
            this.crawlStatus = crawlStatus;
        }
    }

    /**
     * Status object for a crawl
     */
    public static class CrawlStatus {
        private String baseUrl;
        private String status;
        private long startTime;
        private long endTime;
        private long durationMs;
        private int pagesDiscovered;
        private int pagesAnalyzed;
        private int componentsDiscovered;
        private String errorMessage;
        private boolean completed;
        private boolean authenticationEnabled;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        public void setEndTime(long endTime) {
            this.endTime = endTime;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }

        public int getPagesDiscovered() {
            return pagesDiscovered;
        }

        public void setPagesDiscovered(int pagesDiscovered) {
            this.pagesDiscovered = pagesDiscovered;
        }

        public int getPagesAnalyzed() {
            return pagesAnalyzed;
        }

        public void setPagesAnalyzed(int pagesAnalyzed) {
            this.pagesAnalyzed = pagesAnalyzed;
        }

        public int getComponentsDiscovered() {
            return componentsDiscovered;
        }

        public void setComponentsDiscovered(int componentsDiscovered) {
            this.componentsDiscovered = componentsDiscovered;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }
        
        public boolean isAuthenticationEnabled() {
            return authenticationEnabled;
        }
        
        public void setAuthenticationEnabled(boolean authenticationEnabled) {
            this.authenticationEnabled = authenticationEnabled;
        }
    }
}
