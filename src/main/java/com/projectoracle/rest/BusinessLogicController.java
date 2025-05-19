package com.projectoracle.rest;

import com.projectoracle.model.Page;
import com.projectoracle.service.crawler.BusinessLogicExtractionService;
import com.projectoracle.service.crawler.BusinessLogicExtractionService.BusinessLogicResult;
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
 * REST controller for business logic extraction endpoints.
 * Provides API access to application business rule discovery.
 */
@RestController
@RequestMapping("/api/v1/business-logic")
public class BusinessLogicController {

    private static final Logger logger = LoggerFactory.getLogger(BusinessLogicController.class);

    @Autowired
    private UICrawlerService crawlerService;

    @Autowired
    private BusinessLogicExtractionService businessLogicService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    /**
     * Extract business logic from a web application
     */
    @PostMapping("/extract")
    public ResponseEntity<ExtractionResponse> extractBusinessLogic(@RequestBody ExtractionRequest request) {
        if (request.getBaseUrl() == null || request.getBaseUrl().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new ExtractionResponse("error", "Base URL is required", null)
            );
        }

        logger.info("Starting business logic extraction for URL: {}", request.getBaseUrl());

        // Start extraction in background
        CompletableFuture.supplyAsync(() -> {
            try {
                // Crawl the application
                int maxPages = request.getMaxPages() > 0 ? request.getMaxPages() : 20;
                List<Page> pages = crawlerService.crawlApplication(request.getBaseUrl(), maxPages);

                // Extract business logic
                BusinessLogicResult result = businessLogicService.extractBusinessLogic(pages);

                logger.info("Completed business logic extraction for URL: {}", request.getBaseUrl());

                return result;
            } catch (Exception e) {
                logger.error("Error during business logic extraction", e);
                return null;
            }
        }, executorService);

        // Return accepted response immediately
        return ResponseEntity.accepted().body(
                new ExtractionResponse("processing", "Extraction started", null)
        );
    }

    /**
     * Get the results of the business logic extraction
     */
    @GetMapping("/extraction/{extractionId}")
    public ResponseEntity<ExtractionResponse> getExtractionResults(@PathVariable String extractionId) {
        // This is a placeholder. In a real implementation, we would track
        // extraction jobs and return the actual results.
        return ResponseEntity.ok(
                new ExtractionResponse("completed", "Extraction completed", null)
        );
    }

    /**
     * Request object for extraction
     */
    public static class ExtractionRequest {
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
     * Response object for extraction
     */
    public static class ExtractionResponse {
        private String status;
        private String message;
        private BusinessLogicResult result;

        public ExtractionResponse(String status, String message, BusinessLogicResult result) {
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

        public BusinessLogicResult getResult() {
            return result;
        }

        public void setResult(BusinessLogicResult result) {
            this.result = result;
        }
    }
}