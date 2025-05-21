package com.projectoracle.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projectoracle.service.ModelDebugService;
import com.projectoracle.service.AIModelService;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for diagnostic endpoints
 */
@RestController
@RequestMapping("/api/v1/diagnostic")
public class DiagnosticController {

    @Autowired
    private ModelDebugService modelDebugService;
    
    @Autowired
    private AIModelService aiModelService;

    /**
     * Test model loading
     */
    @GetMapping("/test-model")
    public ResponseEntity<Map<String, Object>> testModel() {
        Map<String, Object> result = new HashMap<>();
        
        // Try normal model test
        boolean testResult = modelDebugService.testLoadModel();
        result.put("basicModelLoadTest", testResult);
        
        // Try direct model loading
        boolean directTestResult = modelDebugService.testLoadModelDirectly();
        result.put("directModelLoadTest", directTestResult);
        
        // Try a simple model test with text generation
        try {
            String generatedText = aiModelService.generateText("Hello", 5);
            result.put("textGenerationTest", generatedText);
            result.put("textGenerationSuccess", !generatedText.startsWith("Error"));
        } catch (Exception e) {
            result.put("textGenerationTest", "Error: " + e.getMessage());
            result.put("textGenerationSuccess", false);
        }
        
        return ResponseEntity.ok(result);
    }
}