package com.projectoracle.rest;

import com.projectoracle.model.KnowledgeIntegrationRequest;
import com.projectoracle.model.KnowledgeSource;
import com.projectoracle.model.TestCase;
import com.projectoracle.service.KnowledgeIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for knowledge integration with test generation.
 * Provides endpoints for integrating external knowledge sources into the test generation process.
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeIntegrationController {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeIntegrationController.class);
    
    @Autowired
    private KnowledgeIntegrationService knowledgeIntegrationService;
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Knowledge Integration API is running");
    }
    
    /**
     * Integrate API documentation with test generation
     */
    @PostMapping("/integrate/api-doc")
    public ResponseEntity<TestCase> integrateApiDocumentation(
            @RequestParam String methodSignature,
            @RequestParam String apiDocPath) {
        
        logger.info("Received request to integrate API documentation for method: {}", methodSignature);
        TestCase testCase = knowledgeIntegrationService.integrateApiDocumentation(
                Path.of(apiDocPath), methodSignature);
        
        return ResponseEntity.ok(testCase);
    }
    
    /**
     * Integrate project documentation with test generation
     */
    @PostMapping("/integrate/project-doc")
    public ResponseEntity<TestCase> integrateProjectDocumentation(
            @RequestParam String methodSignature,
            @RequestParam String docPath) {
        
        logger.info("Received request to integrate project documentation for method: {}", methodSignature);
        TestCase testCase = knowledgeIntegrationService.integrateProjectDocumentation(
                Path.of(docPath), methodSignature);
        
        return ResponseEntity.ok(testCase);
    }
    
    /**
     * Integrate historical test data with test generation
     */
    @PostMapping("/integrate/test-history")
    public ResponseEntity<TestCase> integrateTestHistory(
            @RequestParam String methodSignature,
            @RequestParam String historyPath) {
        
        logger.info("Received request to integrate historical test data for method: {}", methodSignature);
        TestCase testCase = knowledgeIntegrationService.integrateHistoricalTestData(
                Path.of(historyPath), methodSignature);
        
        return ResponseEntity.ok(testCase);
    }
    
    /**
     * Integrate multiple knowledge sources with test generation
     */
    @PostMapping("/integrate")
    public ResponseEntity<TestCase> integrateAllSources(@RequestBody KnowledgeIntegrationRequest request) {
        logger.info("Received comprehensive knowledge integration request for method: {}", 
                request.getMethodSignature());
        
        Map<String, Path> knowledgeSources = new HashMap<>();
        
        // Convert request sources to map for service
        for (KnowledgeSource source : request.getKnowledgeSources()) {
            if (source.isEnabled()) {
                knowledgeSources.put(source.getType(), Path.of(source.getPath()));
            }
        }
        
        TestCase testCase = knowledgeIntegrationService.integrateAllKnowledgeSources(
                request.getMethodSignature(), knowledgeSources);
        
        return ResponseEntity.ok(testCase);
    }
    
    /**
     * List available knowledge sources in the system
     */
    @GetMapping("/sources")
    public ResponseEntity<List<KnowledgeSource>> listKnowledgeSources() {
        // This would typically come from a configuration or database
        // Here we're providing a static list of common knowledge source types
        List<KnowledgeSource> availableSources = List.of(
            new KnowledgeSource("api", "docs/api-specs", "swagger", true),
            new KnowledgeSource("docs", "docs/project", "markdown", true),
            new KnowledgeSource("history", "src/test", "junit", true),
            new KnowledgeSource("source", "src/main", "java", true)
        );
        
        return ResponseEntity.ok(availableSources);
    }
}