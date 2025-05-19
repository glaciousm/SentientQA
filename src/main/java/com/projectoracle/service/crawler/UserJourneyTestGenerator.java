package com.projectoracle.service.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.projectoracle.model.Page;
import com.projectoracle.model.TestCase;
import com.projectoracle.model.UIComponent;
import com.projectoracle.service.AIModelService;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.service.crawler.FlowAnalysisService.UserJourney;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Enhanced test generation for user journeys.
 * Extends the existing UITestGenerationService with flow analysis capabilities.
 */
@Service
public class UserJourneyTestGenerator {

    private static final Logger logger = LoggerFactory.getLogger(UserJourneyTestGenerator.class);

    @Autowired
    private AIModelService aiModelService;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private FlowAnalysisService flowAnalysisService;

    @Autowired
    private UITestPromptBuilder promptBuilder;

    /**
     * Generate tests for identified user journeys
     *
     * @param pages the list of pages from the crawl
     * @return list of generated test cases
     */
    public List<TestCase> generateUserJourneyTests(List<Page> pages) {
        List<TestCase> testCases = new ArrayList<>();

        logger.info("Generating tests for user journeys across {} pages", pages.size());

        // Analyze user journeys
        List<UserJourney> journeys = flowAnalysisService.identifyUserJourneys(pages);

        // Generate tests for each journey
        for (UserJourney journey : journeys) {
            TestCase testCase = generateUserJourneyTest(journey);
            if (testCase != null) {
                testCases.add(testCase);
                testCaseRepository.save(testCase);
            }
        }

        logger.info("Generated {} user journey tests", testCases.size());
        return testCases;
    }

    /**
     * Generate a test for a specific user journey
     */
    private TestCase generateUserJourneyTest(UserJourney journey) {
        logger.info("Generating test for user journey: {}", journey.getName());

        // Build a complex prompt for this journey
        String prompt = buildUserJourneyPrompt(journey);

        // Generate test code with AI
        String testCode = aiModelService.generateText(prompt, 2000);

        // Create test case
        String sanitizedName = sanitizeForMethodName(journey.getName());
        TestCase testCase = TestCase.builder()
                                    .id(UUID.randomUUID())
                                    .name("Test_" + sanitizedName)
                                    .description(journey.getDescription())
                                    .type(TestCase.TestType.UI)
                                    .priority(TestCase.TestPriority.HIGH)
                                    .status(TestCase.TestStatus.GENERATED)
                                    .packageName("com.projectoracle.test.ui.journey")
                                    .className(sanitizedName + "Test")
                                    .methodName("test" + sanitizedName)
                                    .sourceCode(testCode)
                                    .createdAt(LocalDateTime.now())
                                    .modifiedAt(LocalDateTime.now())
                                    .confidenceScore(0.9)
                                    .generationPrompt(prompt)
                                    .build();

        return testCase;
    }

    /**
     * Build a prompt for a user journey test
     */
    private String buildUserJourneyPrompt(UserJourney journey) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a Selenium Java E2E test for a complex user journey with these details:\n\n")
              .append("Journey name: ").append(journey.getName()).append("\n")
              .append("Description: ").append(journey.getDescription()).append("\n\n")
              .append("The journey consists of the following steps:\n\n");

        // Add each step in the journey
        int stepNumber = 1;
        for (FlowAnalysisService.PageTransition transition : journey.getTransitions()) {
            prompt.append("Step ").append(stepNumber++).append(": ")
                  .append(getTransitionDescription(transition)).append("\n\n");

            // Source page details
            prompt.append("Source page: ").append(transition.getSourcePage().getTitle())
                  .append(" (").append(transition.getSourcePage().getUrl()).append(")\n");

            // Action component details
            UIComponent component = transition.getActionComponent();
            prompt.append("Action: Interact with ").append(component.getDescription()).append("\n")
                  .append("Component locator: ").append(component.getElementLocator()).append("\n");

            // Action details based on component type
            if ("input".equals(component.getType())) {
                prompt.append("Enter value: ").append(component.generateTestValue()).append("\n");
            }

            // Destination page details
            prompt.append("Destination page: ").append(transition.getDestinationPage().getTitle())
                  .append(" (").append(transition.getDestinationPage().getUrl()).append(")\n\n");

            // Add verification points
            prompt.append("Verification points on destination page:\n");
            List<UIComponent> verificationComponents = transition.getDestinationPage().getComponents().stream()
                                                                 .limit(3)
                                                                 .toList();

            for (UIComponent verifyComponent : verificationComponents) {
                prompt.append("- Verify ").append(verifyComponent.getDescription())
                      .append(" (Locator: ").append(verifyComponent.getElementLocator()).append(")\n");
            }

            prompt.append("\n");
        }

        prompt.append("The test should:\n")
              .append("1. Use proper WebDriver setup with appropriate waits\n")
              .append("2. Include setup and teardown methods\n")
              .append("3. Handle potential errors gracefully\n")
              .append("4. Take screenshots at key points\n")
              .append("5. Use Page Objects pattern for better maintainability\n\n")
              .append("Use JUnit 5 and WebDriver. Include proper assertions and error handling.\n");

        return prompt.toString();
    }

    /**
     * Get a human-readable description of a transition
     */
    private String getTransitionDescription(FlowAnalysisService.PageTransition transition) {
        switch (transition.getType()) {
            case LINK_NAVIGATION:
                return "Click on link to navigate from " +
                        transition.getSourcePage().getTitle() + " to " +
                        transition.getDestinationPage().getTitle();

            case FORM_SUBMISSION:
                return "Fill form on " + transition.getSourcePage().getTitle() +
                        " and submit to " + transition.getDestinationPage().getTitle();

            case BUTTON_CLICK:
                return "Click button on " + transition.getSourcePage().getTitle() +
                        " to navigate to " + transition.getDestinationPage().getTitle();

            default:
                return "Navigate from " + transition.getSourcePage().getTitle() +
                        " to " + transition.getDestinationPage().getTitle();
        }
    }

    /**
     * Sanitize a string for use in a Java method name
     */
    private String sanitizeForMethodName(String input) {
        if (input == null || input.isEmpty()) {
            return "Unknown";
        }

        // Replace non-alphanumeric characters with underscores
        String sanitized = input.replaceAll("[^a-zA-Z0-9]", "_");

        // Ensure it starts with a letter
        if (!Character.isLetter(sanitized.charAt(0))) {
            sanitized = "Journey_" + sanitized;
        }

        // Limit length
        if (sanitized.length() > 30) {
            sanitized = sanitized.substring(0, 30);
        }

        return sanitized;
    }
}