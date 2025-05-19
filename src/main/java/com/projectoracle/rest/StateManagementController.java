package com.projectoracle.rest;

import com.projectoracle.model.Page;
import com.projectoracle.service.crawler.StateManagementAnalysisService;
import com.projectoracle.service.crawler.StateManagementAnalysisService.StateAnalysisResult;
import com.projectoracle.service.crawler.UICrawlerService;

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
 * REST controller for state management analysis endpoints.
 * Provides API access to application state tracking.
 */
@RestController
@RequestMapping("/api/v1/state")
public class StateManagementController {

    private static final Logger logger = LoggerFactory.getLogger(StateManagementController.class);

    @Autowired
    private UICrawlerService crawlerService;

    @Autowired
    private StateManagementAnalysisService stateAnalysisService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    /**
     * Analyze state management in a web application
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyzeStateManagement(@RequestBody AnalysisRequest request) {
        if (request.getBaseUrl() == null || request.getBaseUrl().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new AnalysisResponse("error", "Base URL is required", null)
            );
        }

        logger.info("Starting state management analysis for URL: {}", request.getBaseUrl());

        // Start analysis in background
        CompletableFuture.supplyAsync(() -> {
            try {
                // Crawl the application
                int maxPages = request.getMaxPages() > 0 ? request.getMaxPages() : 20;
                List<Page> pages = crawlerService.crawlApplication(request.getBaseUrl(), maxPages);

                // Analyze state management
                StateAnalysisResult result = stateAnalysisService.analyzeStateManagement(pages);

                logger.info("Completed state management analysis for URL: {}", request.getBaseUrl());

                return result;
            } catch (Exception e) {
                logger.error("Error during state management analysis", e);
                return null;
            }
        }, executorService);

        // Return accepted response immediately
        return ResponseEntity.accepted().body(
                new AnalysisResponse("processing", "Analysis started", null)
        );
    }

    /**
     * Get the results of the state management analysis
     */
    @GetMapping("/analysis/{analysisId}")
    public ResponseEntity<AnalysisResponse> getAnalysisResults(@PathVariable String analysisId) {
        // This is a placeholder. In a real implementation, we would track
        // analysis jobs and return the actual results.
        return ResponseEntity.ok(
                new AnalysisResponse("completed", "Analysis completed", null)
        );
    }

    /**
     * Request object for analysis
     */
    public static class AnalysisRequest {
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
     * Response object for analysis
     */
    public static class AnalysisResponse {
        private String status;
        private String message;
        private StateAnalysisResult result;

        public AnalysisResponse(String status, String message, StateAnalysisResult result) {
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

        public StateAnalysisResult getResult() {
            return result;
        }

        public void setResult(StateAnalysisResult result) {
            this.result = result;
        }
    }
}