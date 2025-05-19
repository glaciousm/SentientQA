package com.projectoracle.rest;

import com.projectoracle.model.Page;
import com.projectoracle.model.TestCase;
import com.projectoracle.service.crawler.FlowAnalysisService;
import com.projectoracle.service.crawler.UICrawlerService;
import com.projectoracle.service.crawler.UserJourneyTestGenerator;
import com.projectoracle.service.crawler.FlowAnalysisService.UserJourney;

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
 * REST controller for user journey test generation endpoints.
 * Provides API access to flow analysis and user journey test generation.
 */
@RestController
@RequestMapping("/api/v1/journeys")
public class UserJourneyController {

    private static final Logger logger = LoggerFactory.getLogger(UserJourneyController.class);

    @Autowired
    private UICrawlerService crawlerService;

    @Autowired
    private FlowAnalysisService flowAnalysisService;

    @Autowired
    private UserJourneyTestGenerator journeyTestGenerator;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    /**
     * Analyze a web application to identify user journeys
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyzeUserJourneys(@RequestBody AnalysisRequest request) {
        if (request.getBaseUrl() == null || request.getBaseUrl().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new AnalysisResponse("error", "Base URL is required", null)
            );
        }

        logger.info("Starting user journey analysis for URL: {}", request.getBaseUrl());

        // Start analysis in background
        CompletableFuture.supplyAsync(() -> {
            try {
                // Crawl the application
                int maxPages = request.getMaxPages() > 0 ? request.getMaxPages() : 20;
                List<Page> pages = crawlerService.crawlApplication(request.getBaseUrl(), maxPages);

                // Analyze user journeys
                List<UserJourney> journeys = flowAnalysisService.identifyUserJourneys(pages);

                logger.info("Found {} user journeys for URL: {}", journeys.size(), request.getBaseUrl());

                return new AnalysisResult(pages.size(), journeys.size(), journeys);
            } catch (Exception e) {
                logger.error("Error during user journey analysis", e);
                return new AnalysisResult(0, 0, null);
            }
        }, executorService);

        // Return accepted response immediately
        return ResponseEntity.accepted().body(
                new AnalysisResponse("processing", "Analysis started", null)
        );
    }

    /**
     * Get the results of the user journey analysis
     */
    @GetMapping("/analysis/{analysisId}")
    public ResponseEntity<AnalysisResponse> getAnalysisResults(@PathVariable String analysisId) {
        // This is a placeholder. In a real implementation, we would track
        // analysis jobs and return the actual results.
        return ResponseEntity.ok(
                new AnalysisResponse("completed", "Analysis completed",
                        new AnalysisResult(10, 3, null))
        );
    }

    /**
     * Generate tests for user journeys in a web application
     */
    @PostMapping("/generate-tests")
    public ResponseEntity<GenerationResponse> generateJourneyTests(@RequestBody AnalysisRequest request) {
        if (request.getBaseUrl() == null || request.getBaseUrl().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new GenerationResponse("error", "Base URL is required", 0)
            );
        }

        logger.info("Starting user journey test generation for URL: {}", request.getBaseUrl());

        // Start test generation in background
        CompletableFuture.supplyAsync(() -> {
            try {
                // Crawl the application
                int maxPages = request.getMaxPages() > 0 ? request.getMaxPages() : 20;
                List<Page> pages = crawlerService.crawlApplication(request.getBaseUrl(), maxPages);

                // Generate tests for user journeys
                List<TestCase> testCases = journeyTestGenerator.generateUserJourneyTests(pages);

                logger.info("Generated {} user journey tests for URL: {}", testCases.size(), request.getBaseUrl());

                return testCases.size();
            } catch (Exception e) {
                logger.error("Error during user journey test generation", e);
                return 0;
            }
        }, executorService);

        // Return accepted response immediately
        return ResponseEntity.accepted().body(
                new GenerationResponse("processing", "Test generation started", 0)
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
        private AnalysisResult result;

        public AnalysisResponse(String status, String message, AnalysisResult result) {
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

        public AnalysisResult getResult() {
            return result;
        }

        public void setResult(AnalysisResult result) {
            this.result = result;
        }
    }

    /**
     * Result object for analysis
     */
    public static class AnalysisResult {
        private int pagesAnalyzed;
        private int journeysFound;
        private List<UserJourney> journeys;

        public AnalysisResult(int pagesAnalyzed, int journeysFound, List<UserJourney> journeys) {
            this.pagesAnalyzed = pagesAnalyzed;
            this.journeysFound = journeysFound;
            this.journeys = journeys;
        }

        public int getPagesAnalyzed() {
            return pagesAnalyzed;
        }

        public void setPagesAnalyzed(int pagesAnalyzed) {
            this.pagesAnalyzed = pagesAnalyzed;
        }

        public int getJourneysFound() {
            return journeysFound;
        }

        public void setJourneysFound(int journeysFound) {
            this.journeysFound = journeysFound;
        }

        public List<UserJourney> getJourneys() {
            return journeys;
        }

        public void setJourneys(List<UserJourney> journeys) {
            this.journeys = journeys;
        }
    }

    /**
     * Response object for test generation
     */
    public static class GenerationResponse {
        private String status;
        private String message;
        private int testsGenerated;

        public GenerationResponse(String status, String message, int testsGenerated) {
            this.status = status;
            this.message = message;
            this.testsGenerated = testsGenerated;
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

        public int getTestsGenerated() {
            return testsGenerated;
        }

        public void setTestsGenerated(int testsGenerated) {
            this.testsGenerated = testsGenerated;
        }
    }
}