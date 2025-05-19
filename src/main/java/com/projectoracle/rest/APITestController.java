package com.projectoracle.rest;

import com.projectoracle.model.Page;
import com.projectoracle.model.TestCase;
import com.projectoracle.service.crawler.APITestGenerationService;
import com.projectoracle.service.crawler.UICrawlerService;
import com.projectoracle.service.crawler.APITestGenerationService.APIEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST controller for API test generation endpoints.
 * Provides API access to automatic API endpoint detection and test generation.
 */
@RestController
@RequestMapping("/api/v1/api-tests")
public class APITestController {

    private static final Logger logger = LoggerFactory.getLogger(APITestController.class);

    @Autowired
    private UICrawlerService crawlerService;

    @Autowired
    private APITestGenerationService apiTestGenerationService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    /**
     * Discover API endpoints and generate tests
     */
    @PostMapping("/discover-and-generate")
    public ResponseEntity<DiscoveryResponse> discoverAndGenerateTests(@RequestBody DiscoveryRequest request) {
        if (request.getBaseUrl() == null || request.getBaseUrl().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new DiscoveryResponse("error", "Base URL is required", null)
            );
        }

        logger.info("Starting API discovery and test generation for URL: {}", request.getBaseUrl());

        // Start discovery in background
        CompletableFuture.supplyAsync(() -> {
            try {
                // Crawl the application
                int maxPages = request.getMaxPages() > 0 ? request.getMaxPages() : 20;
                List<Page> pages = crawlerService.crawlApplication(request.getBaseUrl(), maxPages);

                // Extract API endpoints
                List<APIEndpoint> endpoints = apiTestGenerationService.extractAPIEndpoints(pages);

                // Generate tests for the endpoints
                List<TestCase> testCases = apiTestGenerationService.generateAPITests(endpoints);

                logger.info("Generated {} API tests for {} endpoints", testCases.size(), endpoints.size());

                return new DiscoveryResult(pages.size(), endpoints.size(), testCases.size());
            } catch (Exception e) {
                logger.error("Error during API discovery and test generation", e);
                return new DiscoveryResult(0, 0, 0);
            }
        }, executorService);

        // Return accepted response immediately
        return ResponseEntity.accepted().body(
                new DiscoveryResponse("processing", "Discovery and test generation started", null)
        );
    }

    /**
     * Get the results of the API discovery and test generation
     */
    @GetMapping("/discovery/{discoveryId}")
    public ResponseEntity<DiscoveryResponse> getDiscoveryResults(@PathVariable String discoveryId) {
        // This is a placeholder. In a real implementation, we would track
        // discovery jobs and return the actual results.
        return ResponseEntity.ok(
                new DiscoveryResponse("completed", "Discovery and test generation completed",
                        new DiscoveryResult(10, 5, 5))
        );
    }

    /**
     * Request object for discovery
     */
    public static class DiscoveryRequest {
        private String baseUrl;
        private int maxPages;

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
    }

    /**
     * Response object for discovery
     */
    public static class DiscoveryResponse {
        private String status;
        private String message;
        private DiscoveryResult result;

        public DiscoveryResponse(String status, String message, DiscoveryResult result) {
            this.status = status;
            this.message = message;
            this.result = result;
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

        public DiscoveryResult getResult() {
            return result;
        }

        public void setResult(DiscoveryResult result) {
            this.result = result;
        }
    }

    /**
     * Result object for discovery
     */
    public static class DiscoveryResult {
        private int pagesAnalyzed;
        private int endpointsDiscovered;
        private int testsGenerated;

        public DiscoveryResult(int pagesAnalyzed, int endpointsDiscovered, int testsGenerated) {
            this.pagesAnalyzed = pagesAnalyzed;
            this.endpointsDiscovered = endpointsDiscovered;
            this.testsGenerated = testsGenerated;
        }

        public int getPagesAnalyzed() {
            return pagesAnalyzed;
        }

        public void setPagesAnalyzed(int pagesAnalyzed) {
            this.pagesAnalyzed = pagesAnalyzed;
        }

        public int getEndpointsDiscovered() {
            return endpointsDiscovered;
        }

        public void setEndpointsDiscovered(int endpointsDiscovered) {
            this.endpointsDiscovered = endpointsDiscovered;
        }

        public int getTestsGenerated() {
            return testsGenerated;
        }

        public void setTestsGenerated(int testsGenerated) {
            this.testsGenerated = testsGenerated;
        }
    }
}