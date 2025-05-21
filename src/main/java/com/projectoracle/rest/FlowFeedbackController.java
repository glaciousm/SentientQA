package com.projectoracle.rest;

import com.projectoracle.model.Page;
import com.projectoracle.model.UserFlow;
import com.projectoracle.model.TestCase;
import com.projectoracle.repository.UserFlowRepository;
import com.projectoracle.service.crawler.FlowAnalysisService;
import com.projectoracle.service.crawler.UICrawlerService;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.service.TestGenerationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for managing discovered user flows and feedback.
 * Provides endpoints for viewing flows, requesting deeper exploration,
 * and generating tests from the discovered flows.
 */
@RestController
@RequestMapping("/api/v1/flows")
public class FlowFeedbackController {

    private static final Logger logger = LoggerFactory.getLogger(FlowFeedbackController.class);

    @Autowired
    private UserFlowRepository userFlowRepository;

    @Autowired
    private FlowAnalysisService flowAnalysisService;

    @Autowired
    private UICrawlerService crawlerService;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private TestGenerationService testGenerationService;

    /**
     * Get all discovered user flows
     */
    @GetMapping("/all")
    public ResponseEntity<List<UserFlow>> getAllFlows() {
        logger.info("Retrieving all user flows");
        List<UserFlow> flows = userFlowRepository.getAllFlows();
        return ResponseEntity.ok(flows);
    }

    /**
     * Get flows categorized by importance
     */
    @GetMapping("/categorized")
    public ResponseEntity<Map<String, List<UserFlow>>> getCategorizedFlows() {
        logger.info("Retrieving categorized user flows");
        
        List<UserFlow> allFlows = userFlowRepository.getAllFlows();
        Map<String, List<UserFlow>> categorized = new HashMap<>();
        
        // Critical flows (high priority score)
        categorized.put("critical", allFlows.stream()
                .filter(flow -> flow.getPriorityScore() >= 80)
                .collect(Collectors.toList()));
        
        // Form submission flows
        categorized.put("forms", allFlows.stream()
                .filter(UserFlow::isFormSubmission)
                .collect(Collectors.toList()));
        
        // Navigation flows
        categorized.put("navigation", allFlows.stream()
                .filter(flow -> "navigation".equals(flow.getFlowType()))
                .collect(Collectors.toList()));
        
        // State change flows
        categorized.put("stateChanges", allFlows.stream()
                .filter(flow -> "state_change".equals(flow.getFlowType()))
                .collect(Collectors.toList()));
        
        return ResponseEntity.ok(categorized);
    }

    /**
     * Get user journeys (sequences of flows)
     */
    @GetMapping("/journeys")
    public ResponseEntity<Map<String, List<UserFlow>>> getUserJourneys() {
        logger.info("Retrieving user journeys");
        
        int minFlowsInJourney = 2;  // At least 2 flows to be a journey
        int maxJourneys = 10;       // Up to 10 journeys
        
        Map<String, List<UserFlow>> journeys = 
                flowAnalysisService.findCommonUserJourneys(minFlowsInJourney, maxJourneys);
        
        return ResponseEntity.ok(journeys);
    }

    /**
     * Verify a flow (mark as human verified)
     */
    @PostMapping("/{flowId}/verify")
    public ResponseEntity<UserFlow> verifyFlow(@PathVariable UUID flowId) {
        logger.info("Verifying flow with ID: {}", flowId);
        
        UserFlow flow = userFlowRepository.getFlow(flowId);
        if (flow == null) {
            return ResponseEntity.notFound().build();
        }
        
        flow.setVerified(true);
        userFlowRepository.saveFlow(flow);
        
        return ResponseEntity.ok(flow);
    }

    /**
     * Request deeper exploration of a specific page
     */
    @PostMapping("/explore")
    public ResponseEntity<ExplorationResponse> requestDeepExploration(@RequestBody ExplorationRequest request) {
        logger.info("Received request for deeper exploration of page: {}", request.getPageUrl());
        
        if (request.getPageUrl() == null || request.getPageUrl().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new ExplorationResponse("error", "Page URL is required", null));
        }
        
        // Parse exploration level
        int explorationDepth = 2; // Default
        if (request.getExplorationLevel() != null) {
            switch (request.getExplorationLevel()) {
                case "deep":
                    explorationDepth = 5;
                    break;
                case "medium":
                    explorationDepth = 3;
                    break;
                case "shallow":
                    explorationDepth = 1;
                    break;
            }
        }
        
        // Start exploration in a background thread
        String explorationId = UUID.randomUUID().toString();

        int finalExplorationDepth = explorationDepth;
        new Thread(() -> {
            try {
                // Perform deeper crawl of the specified page
                List<Page> discoveredPages = crawlerService.explorePageInteractively(
                        request.getPageUrl(),
                        finalExplorationDepth,
                        request.isIncludeForms(),
                        request.getMaxInteractions());
                
                logger.info("Completed deep exploration. Discovered {} pages.", discoveredPages.size());
            } catch (Exception e) {
                logger.error("Error during deep exploration", e);
            }
        }).start();
        
        // Return response with exploration ID
        ExplorationResponse response = new ExplorationResponse(
                "started",
                "Exploration started for page: " + request.getPageUrl(),
                explorationId);
        
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Generate test cases from discovered flows
     */
    @PostMapping("/generate-tests")
    public ResponseEntity<List<TestCase>> generateTests(@RequestBody TestGenerationRequest request) {
        logger.info("Generating tests from flows with parameters: {}", request);
        
        List<TestCase> testCases = new ArrayList<>();
        
        if (request.isUseJourneys()) {
            // Generate tests from user journeys
            Map<String, List<UserFlow>> journeys = 
                    flowAnalysisService.findCommonUserJourneys(2, request.getMaxTests());
            
            int index = 1;
            for (Map.Entry<String, List<UserFlow>> entry : journeys.entrySet()) {
                String journeyName = entry.getKey();
                List<UserFlow> flows = entry.getValue();
                
                // Generate a test case for the entire journey
                TestCase journeyTest = testGenerationService.generateFlowTest(
                        flows, 
                        "Journey" + index++, 
                        journeyName,
                        request.isIncludeSetup());
                
                testCases.add(journeyTest);
            }
        } else {
            // Generate tests from individual important flows
            List<UserFlow> flows = request.isOnlyVerified() ?
                    userFlowRepository.getAllFlows().stream()
                            .filter(UserFlow::isVerified)
                            .sorted((f1, f2) -> Integer.compare(f2.getPriorityScore(), f1.getPriorityScore()))
                            .limit(request.getMaxTests())
                            .collect(Collectors.toList()) :
                    userFlowRepository.findImportantFlows(request.getMaxTests());
            
            // Convert flows to test cases
            for (int i = 0; i < flows.size(); i++) {
                UserFlow flow = flows.get(i);
                
                // Generate test case for single flow
                TestCase flowTest = flowAnalysisService.convertFlowToTestCase(
                        flow, 
                        "Flow" + (i + 1));
                
                // Generate code if requested
                if (request.isGenerateCode()) {
                    String testCode = testGenerationService.generateFlowTestCode(
                            flow, 
                            flowTest.getMethodName(),
                            request.isIncludeSetup());
                    
                    flowTest.setSourceCode(testCode);
                }
                
                testCases.add(flowTest);
            }
        }
        
        // Save the generated test cases if requested
        if (request.isSaveTestCases()) {
            testCases.forEach(testCaseRepository::save);
        }
        
        return ResponseEntity.ok(testCases);
    }

    /**
     * Request object for exploration requests
     */
    public static class ExplorationRequest {
        private String pageUrl;
        private String explorationLevel;
        private boolean includeForms;
        private int maxInteractions = 20;

        public String getPageUrl() {
            return pageUrl;
        }

        public void setPageUrl(String pageUrl) {
            this.pageUrl = pageUrl;
        }

        public String getExplorationLevel() {
            return explorationLevel;
        }

        public void setExplorationLevel(String explorationLevel) {
            this.explorationLevel = explorationLevel;
        }

        public boolean isIncludeForms() {
            return includeForms;
        }

        public void setIncludeForms(boolean includeForms) {
            this.includeForms = includeForms;
        }

        public int getMaxInteractions() {
            return maxInteractions;
        }

        public void setMaxInteractions(int maxInteractions) {
            this.maxInteractions = maxInteractions;
        }
    }

    /**
     * Response object for exploration requests
     */
    public static class ExplorationResponse {
        private String status;
        private String message;
        private String explorationId;

        public ExplorationResponse(String status, String message, String explorationId) {
            this.status = status;
            this.message = message;
            this.explorationId = explorationId;
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

        public String getExplorationId() {
            return explorationId;
        }

        public void setExplorationId(String explorationId) {
            this.explorationId = explorationId;
        }
    }

    /**
     * Request object for test generation
     */
    public static class TestGenerationRequest {
        private int maxTests = 10;
        private boolean onlyVerified = false;
        private boolean useJourneys = true;
        private boolean generateCode = true;
        private boolean saveTestCases = true;
        private boolean includeSetup = true;

        public int getMaxTests() {
            return maxTests;
        }

        public void setMaxTests(int maxTests) {
            this.maxTests = maxTests;
        }

        public boolean isOnlyVerified() {
            return onlyVerified;
        }

        public void setOnlyVerified(boolean onlyVerified) {
            this.onlyVerified = onlyVerified;
        }

        public boolean isUseJourneys() {
            return useJourneys;
        }

        public void setUseJourneys(boolean useJourneys) {
            this.useJourneys = useJourneys;
        }

        public boolean isGenerateCode() {
            return generateCode;
        }

        public void setGenerateCode(boolean generateCode) {
            this.generateCode = generateCode;
        }

        public boolean isSaveTestCases() {
            return saveTestCases;
        }

        public void setSaveTestCases(boolean saveTestCases) {
            this.saveTestCases = saveTestCases;
        }

        public boolean isIncludeSetup() {
            return includeSetup;
        }

        public void setIncludeSetup(boolean includeSetup) {
            this.includeSetup = includeSetup;
        }

        @Override
        public String toString() {
            return "TestGenerationRequest{" +
                    "maxTests=" + maxTests +
                    ", onlyVerified=" + onlyVerified +
                    ", useJourneys=" + useJourneys +
                    ", generateCode=" + generateCode +
                    ", saveTestCases=" + saveTestCases +
                    ", includeSetup=" + includeSetup +
                    '}';
        }
    }
}