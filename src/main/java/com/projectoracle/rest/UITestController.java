package com.projectoracle.rest;

import com.projectoracle.model.Page;
import com.projectoracle.model.TestCase;
import com.projectoracle.service.crawler.UICrawlerService;
import com.projectoracle.service.crawler.UITestGenerationService;
import com.projectoracle.repository.TestCaseRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for UI test generation endpoints.
 * Integrates UI crawler with test generation capabilities.
 */
@RestController
@RequestMapping("/api/v1/ui-tests")
public class UITestController {

    private static final Logger logger = LoggerFactory.getLogger(UITestController.class);

    @Autowired
    private UICrawlerService crawlerService;

    @Autowired
    private UITestGenerationService uiTestGenerationService;

    @Autowired
    private TestCaseRepository testCaseRepository;

    /**
     * Crawl an application and generate tests
     */
    @PostMapping("/crawl-and-generate")
    public ResponseEntity<GenerationResponse> crawlAndGenerateTests(@RequestBody CrawlAndGenerateRequest request) {
        if (request.getBaseUrl() == null || request.getBaseUrl().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new GenerationResponse("error", "Base URL is required", null)
            );
        }

        logger.info("Starting crawl and test generation for URL: {}", request.getBaseUrl());

        try {
            // Crawl the application
            int maxPages = request.getMaxPages() > 0 ? request.getMaxPages() : 20;
            List<Page> pages = crawlerService.crawlApplication(request.getBaseUrl(), maxPages);

            logger.info("Crawl completed. Discovered {} pages", pages.size());

            // Generate tests from crawled pages
            List<TestCase> generatedTests = uiTestGenerationService.generateTestsForApplication(pages);

            // Group tests by type for the response
            Map<String, Long> testTypeCount = generatedTests.stream()
                                                            .collect(Collectors.groupingBy(
                                                                    test -> test.getType().toString(),
                                                                    Collectors.counting()
                                                            ));

            GenerationResponse response = new GenerationResponse();
            response.setStatus("success");
            response.setMessage("Generated " + generatedTests.size() + " test cases");
            response.setTestCount(generatedTests.size());
            response.setPageCount(pages.size());
            response.setTestsByType(testTypeCount);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during crawl and test generation", e);
            return ResponseEntity.internalServerError().body(
                    new GenerationResponse("error", "Error during crawl and test generation: " + e.getMessage(), null)
            );
        }
    }

    /**
     * Get all UI tests
     */
    @GetMapping
    public ResponseEntity<List<TestCase>> getAllUITests() {
        List<TestCase> uiTests = testCaseRepository.findAll().stream()
                                                   .filter(test -> test.getType() == TestCase.TestType.UI)
                                                   .collect(Collectors.toList());

        return ResponseEntity.ok(uiTests);
    }

    /**
     * Request object for crawl and generate
     */
    public static class CrawlAndGenerateRequest {
        private String baseUrl;
        private int maxPages;
        private int maxDepth;
        private boolean generateEndToEndTests;

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

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        public boolean isGenerateEndToEndTests() {
            return generateEndToEndTests;
        }

        public void setGenerateEndToEndTests(boolean generateEndToEndTests) {
            this.generateEndToEndTests = generateEndToEndTests;
        }
    }

    /**
     * Response object for generation
     */
    public static class GenerationResponse {
        private String status;
        private String message;
        private Integer testCount;
        private Integer pageCount;
        private Map<String, Long> testsByType;

        public GenerationResponse() {
        }

        public GenerationResponse(String status, String message, Integer testCount) {
            this.status = status;
            this.message = message;
            this.testCount = testCount;
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

        public Integer getTestCount() {
            return testCount;
        }

        public void setTestCount(Integer testCount) {
            this.testCount = testCount;
        }

        public Integer getPageCount() {
            return pageCount;
        }

        public void setPageCount(Integer pageCount) {
            this.pageCount = pageCount;
        }

        public Map<String, Long> getTestsByType() {
            return testsByType;
        }

        public void setTestsByType(Map<String, Long> testsByType) {
            this.testsByType = testsByType;
        }
    }
}