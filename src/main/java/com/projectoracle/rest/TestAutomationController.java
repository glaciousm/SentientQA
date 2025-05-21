package com.projectoracle.rest;

import com.projectoracle.model.AutomationSetupOption;
import com.projectoracle.service.TestAutomationOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for managing test automation setup and generation.
 */
@RestController
@RequestMapping("/api/v1/automation")
public class TestAutomationController {

    private static final Logger logger = LoggerFactory.getLogger(TestAutomationController.class);

    @Autowired
    private TestAutomationOrchestrator testAutomationOrchestrator;

    /**
     * Check if the user wants to create automation tests after crawling
     *
     * @param crawlId The ID of the completed crawl
     * @return True if the user wants to proceed
     */
    @GetMapping("/prompt/{crawlId}")
    public ResponseEntity<Boolean> promptForAutomation(@PathVariable UUID crawlId) {
        logger.info("Received prompt for automation setup for crawl: {}", crawlId);
        boolean wantsAutomation = testAutomationOrchestrator.promptForAutomationSetup(crawlId);
        return ResponseEntity.ok(wantsAutomation);
    }

    /**
     * Setup an automation project
     *
     * @param setupRequest The setup configuration
     * @return Path to the created project
     */
    @PostMapping("/setup")
    public ResponseEntity<String> setupAutomationProject(@RequestBody AutomationSetupRequest setupRequest) {
        logger.info("Received request to setup automation project: {}", setupRequest.getProjectName());
        
        String projectPath = testAutomationOrchestrator.setupAutomationProject(
            setupRequest.getProjectName(),
            setupRequest.getBasePackage(),
            setupRequest.getTargetDir(),
            setupRequest.getSetupOption(),
            setupRequest.getTestCaseIds()
        );
        
        if (projectPath != null) {
            return ResponseEntity.ok(projectPath);
        } else {
            return ResponseEntity.badRequest().body("Failed to setup automation project");
        }
    }

    /**
     * Request body for automation setup
     */
    public static class AutomationSetupRequest {
        private String projectName;
        private String basePackage;
        private String targetDir;
        private AutomationSetupOption setupOption;
        private List<UUID> testCaseIds;

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public String getBasePackage() {
            return basePackage;
        }

        public void setBasePackage(String basePackage) {
            this.basePackage = basePackage;
        }

        public String getTargetDir() {
            return targetDir;
        }

        public void setTargetDir(String targetDir) {
            this.targetDir = targetDir;
        }

        public AutomationSetupOption getSetupOption() {
            return setupOption;
        }

        public void setSetupOption(AutomationSetupOption setupOption) {
            this.setupOption = setupOption;
        }

        public List<UUID> getTestCaseIds() {
            return testCaseIds;
        }

        public void setTestCaseIds(List<UUID> testCaseIds) {
            this.testCaseIds = testCaseIds;
        }
    }
}