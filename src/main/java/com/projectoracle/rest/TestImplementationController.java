package com.projectoracle.rest;

import com.projectoracle.model.TestCase;
import com.projectoracle.model.TestSuggestion;
import com.projectoracle.repository.TestSuggestionRepository;
import com.projectoracle.service.TestImplementationService;
import com.projectoracle.service.TestImplementationService.ImplementationStats;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for test implementation endpoints.
 * Provides API access for implementing test suggestions.
 */
@RestController
@RequestMapping("/api/v1/implementations")
@Slf4j
@RequiredArgsConstructor
public class TestImplementationController {

    private final TestImplementationService implementationService;
    private final TestSuggestionRepository suggestionRepository;

    /**
     * Implement a test case from a suggestion
     */
    @PostMapping("/suggestion/{id}")
    public ResponseEntity<TestCase> implementSuggestion(@PathVariable UUID id) {
        log.info("Implementing test case from suggestion: {}", id);

        TestCase testCase = implementationService.implementSuggestion(id);
        if (testCase == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(testCase);
    }

    /**
     * Implement all unimplemented suggestions for a class
     */
    @PostMapping("/class")
    public ResponseEntity<ImplementationResponse> implementAllForClass(@RequestParam String className) {
        log.info("Implementing all suggestions for class: {}", className);

        int implementedCount = implementationService.implementAllSuggestionsForClass(className);

        ImplementationResponse response = new ImplementationResponse();
        response.setImplementedCount(implementedCount);
        response.setMessage(implementedCount + " test cases implemented for class: " + className);
        response.setSuccessful(true);

        return ResponseEntity.ok(response);
    }

    /**
     * Get all unimplemented suggestions
     */
    @GetMapping("/unimplemented")
    public ResponseEntity<List<TestSuggestion>> getUnimplementedSuggestions() {
        log.info("Getting all unimplemented suggestions");

        List<TestSuggestion> suggestions = suggestionRepository.findUnimplemented();
        return ResponseEntity.ok(suggestions);
    }

    /**
     * Get implementation statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<ImplementationStats> getImplementationStats() {
        log.info("Getting implementation statistics");

        ImplementationStats stats = implementationService.getImplementationStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Response object for implementation operations
     */
    @Getter
    @Setter
    public static class ImplementationResponse {
        private int implementedCount;
        private String message;
        private boolean successful;
    }
}