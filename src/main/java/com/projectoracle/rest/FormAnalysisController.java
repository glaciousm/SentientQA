package com.projectoracle.rest;

import com.projectoracle.model.Page;
import com.projectoracle.service.crawler.FormDataTrackingService;
import com.projectoracle.service.crawler.FormDataTrackingService.FormAnalysis;
import com.projectoracle.service.crawler.FormDataTrackingService.DataTransformation;

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
 * REST controller for form data analysis endpoints.
 * Provides API access to form analysis and data flow tracking.
 */
@RestController
@RequestMapping("/api/v1/forms")
public class FormAnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(FormAnalysisController.class);

    @Autowired
    private FormDataTrackingService formDataTrackingService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    /**
     * Analyze a form on a page
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyzeForm(@RequestBody AnalysisRequest request) {
        if (request.getFormUrl() == null || request.getFormUrl().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new AnalysisResponse("error", "Form URL is required", null)
            );
        }

        logger.info("Starting form analysis for URL: {}", request.getFormUrl());

        // Start analysis in background
        CompletableFuture.supplyAsync(() -> {
            try {
                // Create a page object
                Page page = new Page();
                page.setUrl(request.getFormUrl());

                // Analyze the form
                FormAnalysis analysis = formDataTrackingService.analyzeForm(page);

                logger.info("Completed form analysis for URL: {}", request.getFormUrl());

                return analysis;
            } catch (Exception e) {
                logger.error("Error during form analysis", e);
                return null;
            }
        }, executorService);

        // Return accepted response immediately
        return ResponseEntity.accepted().body(
                new AnalysisResponse("processing", "Form analysis started", null)
        );
    }

    /**
     * Analyze form submission to detect data transformations
     */
    @PostMapping("/analyze-submission")
    public ResponseEntity<SubmissionResponse> analyzeSubmission(@RequestBody SubmissionRequest request) {
        if (request.getSourceUrl() == null || request.getSourceUrl().isEmpty() ||
                request.getTargetUrl() == null || request.getTargetUrl().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new SubmissionResponse("error", "Source and target URLs are required", null)
            );
        }

        logger.info("Starting form submission analysis from {} to {}",
                request.getSourceUrl(), request.getTargetUrl());

        // Start analysis in background
        CompletableFuture.supplyAsync(() -> {
            try {
                List<DataTransformation> transformations = formDataTrackingService.analyzeFormSubmission(
                        request.getSourceUrl(), request.getTargetUrl());

                logger.info("Found {} data transformations", transformations.size());

                return transformations;
            } catch (Exception e) {
                logger.error("Error during form submission analysis", e);
                return null;
            }
        }, executorService);

        // Return accepted response immediately
        return ResponseEntity.accepted().body(
                new SubmissionResponse("processing", "Form submission analysis started", null)
        );
    }

    /**
     * Request object for form analysis
     */
    public static class AnalysisRequest {
        private String formUrl;

        public String getFormUrl() {
            return formUrl;
        }

        public void setFormUrl(String formUrl) {
            this.formUrl = formUrl;
        }
    }

    /**
     * Response object for form analysis
     */
    public static class AnalysisResponse {
        private String status;
        private String message;
        private FormAnalysis result;

        public AnalysisResponse(String status, String message, FormAnalysis result) {
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

        public FormAnalysis getResult() {
            return result;
        }

        public void setResult(FormAnalysis result) {
            this.result = result;
        }
    }

    /**
     * Request object for form submission analysis
     */
    public static class SubmissionRequest {
        private String sourceUrl;
        private String targetUrl;

        public String getSourceUrl() {
            return sourceUrl;
        }

        public void setSourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
        }

        public String getTargetUrl() {
            return targetUrl;
        }

        public void setTargetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
        }
    }

    /**
     * Response object for form submission analysis
     */
    public static class SubmissionResponse {
        private String status;
        private String message;
        private List<DataTransformation> transformations;

        public SubmissionResponse(String status, String message, List<DataTransformation> transformations) {
            this.status = status;
            this.message = message;
            this.transformations = transformations;
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

        public List<DataTransformation> getTransformations() {
            return transformations;
        }

        public void setTransformations(List<DataTransformation> transformations) {
            this.transformations = transformations;
        }
    }
}