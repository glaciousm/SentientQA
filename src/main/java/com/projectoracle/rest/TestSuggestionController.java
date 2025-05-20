package com.projectoracle.rest;

import com.projectoracle.model.TestSuggestion;
import com.projectoracle.service.MethodInfo;
import com.projectoracle.service.TestSuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for test suggestion endpoints.
 * Provides API access to the test suggestion service.
 */
@RestController
@RequestMapping("/api/v1/suggestions")
@Slf4j
@RequiredArgsConstructor
public class TestSuggestionController {

    private final TestSuggestionService testSuggestionService;

    /**
     * Get test suggestions for a specific class
     */
    @GetMapping("/class")
    public ResponseEntity<List<TestSuggestion>> getSuggestionsForClass(@RequestParam String className) {
        log.info("Getting test suggestions for class: {}", className);
        List<TestSuggestion> suggestions = testSuggestionService.suggestTestsForClass(className);
        return ResponseEntity.ok(suggestions);
    }

    /**
     * Get edge case test suggestions for a specific class
     */
    @GetMapping("/edge-cases")
    public ResponseEntity<List<TestSuggestion>> getEdgeCaseSuggestions(@RequestParam String className) {
        log.info("Getting edge case test suggestions for class: {}", className);
        List<TestSuggestion> suggestions = testSuggestionService.suggestEdgeCaseTests(className);
        return ResponseEntity.ok(suggestions);
    }

    /**
     * Get a list of untested methods
     */
    @GetMapping("/untested-methods")
    public ResponseEntity<List<MethodInfo>> getUntestedMethods() {
        log.info("Getting untested methods");
        List<MethodInfo> untestedMethods = testSuggestionService.findUntestedMethods();
        return ResponseEntity.ok(untestedMethods);
    }

    /**
     * Generate test code for a suggestion
     */
    @GetMapping("/{id}/generate-code")
    public ResponseEntity<GeneratedCodeResponse> generateTestCode(@PathVariable UUID id) {
        log.info("Generating test code for suggestion: {}", id);

        // In a real implementation, we would look up the suggestion by ID
        // For this demonstration, we'll create a sample suggestion

        TestSuggestion suggestion = TestSuggestion.builder()
                                                  .id(id)
                                                  .testName("Sample test suggestion")
                                                  .inputValues("value1, value2")
                                                  .expectedOutput("Expected result")
                                                  .category("normal case")
                                                  .build();

        // Generate code
        String testCode = suggestion.generateTestCode();

        // Return response
        GeneratedCodeResponse response = new GeneratedCodeResponse();
        response.setTestCode(testCode);
        response.setMessage("Test code generated successfully");

        return ResponseEntity.ok(response);
    }

    /**
     * Response object for generated code
     */
    public static class GeneratedCodeResponse {
        private String testCode;
        private String message;

        public String getTestCode() {
            return testCode;
        }

        public void setTestCode(String testCode) {
            this.testCode = testCode;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}