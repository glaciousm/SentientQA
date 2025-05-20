package com.projectoracle.rest;

import com.projectoracle.model.NLPQueryRequest;
import com.projectoracle.model.TestCase;
import com.projectoracle.service.NLPQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for natural language processing interface to generate tests.
 * This allows users to describe test scenarios in plain English.
 */
@RestController
@RequestMapping("/api/v1/nlp")
public class NLPQueryController {
    
    private static final Logger logger = LoggerFactory.getLogger(NLPQueryController.class);
    
    @Autowired
    private NLPQueryService nlpQueryService;
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("NLP Query API is running");
    }
    
    /**
     * Generate tests from a natural language query
     */
    @PostMapping("/generate")
    public ResponseEntity<List<TestCase>> generateTests(@RequestBody NLPQueryRequest request) {
        logger.info("Received NLP query: {}", request.getQuery());
        
        List<TestCase> testCases = nlpQueryService.processQuery(request);
        
        if (testCases.isEmpty()) {
            logger.warn("No test cases could be generated from the query");
            return ResponseEntity.noContent().build();
        }
        
        return ResponseEntity.ok(testCases);
    }
    
    /**
     * Generate simple test from a plain text query
     */
    @GetMapping("/generate-simple")
    public ResponseEntity<List<TestCase>> generateSimpleTest(
            @RequestParam String query,
            @RequestParam(required = false) String targetClass,
            @RequestParam(required = false) String targetMethod,
            @RequestParam(required = false, defaultValue = "false") boolean useAllKnowledge) {
        
        logger.info("Received simple NLP query: {}", query);
        
        NLPQueryRequest request = NLPQueryRequest.builder()
                .query(query)
                .targetClass(targetClass)
                .targetMethod(targetMethod)
                .useAllAvailableKnowledge(useAllKnowledge)
                .build();
        
        List<TestCase> testCases = nlpQueryService.processQuery(request);
        
        if (testCases.isEmpty()) {
            logger.warn("No test cases could be generated from the simple query");
            return ResponseEntity.noContent().build();
        }
        
        return ResponseEntity.ok(testCases);
    }
    
    /**
     * Example natural language queries
     */
    @GetMapping("/examples")
    public ResponseEntity<List<String>> getExampleQueries() {
        List<String> examples = List.of(
            "Generate a test for the login method that checks for invalid credentials",
            "Test the calculateTotal method in OrderService with negative values",
            "Create edge case tests for the UserValidator class",
            "Write tests for the authentication flow in the system",
            "Generate API tests for the /api/v1/users endpoint",
            "Test the connection timeout handling in NetworkService",
            "Create tests for validating email addresses in the UserRegistrationService",
            "Write unit tests for the payment processing in OrderCheckout class"
        );
        
        return ResponseEntity.ok(examples);
    }
}