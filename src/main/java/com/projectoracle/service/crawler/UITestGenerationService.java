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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for generating UI tests based on crawled web applications.
 * Leverages the UI crawler to create end-to-end tests for web interfaces.
 */
@Service
public class UITestGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(UITestGenerationService.class);
    UITestPromptBuilder builder = new UITestPromptBuilder();

    @Autowired
    private AIModelService aiModelService;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private UICrawlerService crawlerService;

    /**
     * Generate tests for a crawled application
     *
     * @param pages the list of pages from the crawl
     * @return the list of generated test cases
     */
    public List<TestCase> generateTestsForApplication(List<Page> pages) {
        List<TestCase> testCases = new ArrayList<>();

        logger.info("Generating tests for {} pages", pages.size());

        // Generate tests for important pages
        for (Page page : pages) {
            // Skip pages without components or error pages
            if (page.getComponents() == null || page.getComponents().isEmpty() ||
                    page.getPageType() == Page.PageType.ERROR) {
                continue;
            }

            // Generate tests based on page type
            switch (page.getPageType()) {
                case LOGIN:
                    testCases.add(generateLoginTest(page));
                    break;
                case FORM:
                    testCases.add(generateFormSubmissionTest(page));
                    break;
                case LISTING:
                    testCases.add(generateListingNavigationTest(page));
                    break;
                case DETAIL:
                    testCases.add(generateDetailViewTest(page));
                    break;
                case DASHBOARD:
                    testCases.add(generateDashboardElementsTest(page));
                    break;
                default:
                    // For all other pages, generate a simple navigation test
                    testCases.add(generateNavigationTest(page));
                    break;
            }

            // If the page has many interactive components, generate additional tests
            if (countInteractiveComponents(page) > 5) {
                TestCase interactionTest = generateComponentInteractionTest(page);
                if (interactionTest != null) {
                    testCases.add(interactionTest);
                }
            }
        }

        // Generate end-to-end flows that span multiple pages
        testCases.addAll(generateEndToEndFlows(pages));

        // Save all generated tests
        testCases.forEach(testCaseRepository::save);

        logger.info("Generated {} test cases", testCases.size());

        return testCases;
    }

    /**
     * Count the number of interactive components on a page
     */
    private int countInteractiveComponents(Page page) {
        return (int) page.getComponents().stream()
                         .filter(UIComponent::isInteractive)
                         .count();
    }

    /**
     * Generate a login test for a login page
     */
    private TestCase generateLoginTest(Page page) {
        logger.info("Generating login test for page: {}", page.getUrl());

        // Find username and password fields
        UIComponent usernameField = findComponentBySubtype(page, "input", "text");
        UIComponent passwordField = findComponentBySubtype(page, "input", "password");
        UIComponent submitButton = findComponentByType(page, "button");

        if (usernameField == null || passwordField == null || submitButton == null) {
            logger.warn("Could not find required components for login test");
            return generateNavigationTest(page); // Fallback to simple navigation
        }

        // Generate test code with AI
        String prompt = builder.buildLoginTestPrompt(page, usernameField, passwordField, submitButton);
        String testCode = aiModelService.generateText(prompt, 1000);

        // Create test case
        TestCase testCase = TestCase.builder()
                                    .id(UUID.randomUUID())
                                    .name("Test_Login_" + sanitizeForMethodName(page.getTitle()))
                                    .description("Test login functionality on " + page.getTitle())
                                    .type(TestCase.TestType.UI)
                                    .priority(TestCase.TestPriority.CRITICAL)
                                    .status(TestCase.TestStatus.GENERATED)
                                    .packageName("com.projectoracle.test.ui")
                                    .className("LoginTest")
                                    .methodName("testLogin_" + sanitizeForMethodName(page.getTitle()))
                                    .sourceCode(testCode)
                                    .createdAt(LocalDateTime.now())
                                    .modifiedAt(LocalDateTime.now())
                                    .confidenceScore(0.9)
                                    .generationPrompt(prompt)
                                    .build();

        return testCase;
    }

    /**
     * Generate a form submission test
     */
    private TestCase generateFormSubmissionTest(Page page) {
        logger.info("Generating form submission test for page: {}", page.getUrl());

        // Get all form inputs
        List<UIComponent> formComponents = page.getComponents().stream()
                                               .filter(c -> "input".equals(c.getType()) ||
                                                       "select".equals(c.getType()) ||
                                                       "textarea".equals(c.getType()))
                                               .collect(Collectors.toList());

        // Find submit button
        UIComponent submitButton = findComponentByType(page, "button");

        if (formComponents.isEmpty() || submitButton == null) {
            logger.warn("Could not find required components for form test");
            return generateNavigationTest(page); // Fallback to simple navigation
        }

        // Generate test code with AI
        String prompt = builder.buildFormTestPrompt(page, formComponents, submitButton);
        String testCode = aiModelService.generateText(prompt, 1200);

        // Create test case
        TestCase testCase = TestCase.builder()
                                    .id(UUID.randomUUID())
                                    .name("Test_FormSubmission_" + sanitizeForMethodName(page.getTitle()))
                                    .description("Test form submission on " + page.getTitle())
                                    .type(TestCase.TestType.UI)
                                    .priority(TestCase.TestPriority.HIGH)
                                    .status(TestCase.TestStatus.GENERATED)
                                    .packageName("com.projectoracle.test.ui")
                                    .className("FormSubmissionTest")
                                    .methodName("testFormSubmission_" + sanitizeForMethodName(page.getTitle()))
                                    .sourceCode(testCode)
                                    .createdAt(LocalDateTime.now())
                                    .modifiedAt(LocalDateTime.now())
                                    .confidenceScore(0.85)
                                    .generationPrompt(prompt)
                                    .build();

        return testCase;
    }

    /**
     * Generate a test for navigating through a listing page
     */
    private TestCase generateListingNavigationTest(Page page) {
        logger.info("Generating listing navigation test for page: {}", page.getUrl());

        // Find table or list elements
        UIComponent tableComponent = findComponentByType(page, "table");
        List<UIComponent> linkComponents = page.getComponents().stream()
                                               .filter(c -> "link".equals(c.getType()))
                                               .collect(Collectors.toList());

        if ((tableComponent == null && linkComponents.isEmpty())) {
            logger.warn("Could not find required components for listing test");
            return generateNavigationTest(page); // Fallback to simple navigation
        }

        // Generate test code with AI
        String prompt = builder.buildListingTestPrompt(page, tableComponent, linkComponents);
        String testCode = aiModelService.generateText(prompt, 1000);

        // Create test case
        TestCase testCase = TestCase.builder()
                                    .id(UUID.randomUUID())
                                    .name("Test_ListingNavigation_" + sanitizeForMethodName(page.getTitle()))
                                    .description("Test listing navigation on " + page.getTitle())
                                    .type(TestCase.TestType.UI)
                                    .priority(TestCase.TestPriority.MEDIUM)
                                    .status(TestCase.TestStatus.GENERATED)
                                    .packageName("com.projectoracle.test.ui")
                                    .className("ListingNavigationTest")
                                    .methodName("testListingNavigation_" + sanitizeForMethodName(page.getTitle()))
                                    .sourceCode(testCode)
                                    .createdAt(LocalDateTime.now())
                                    .modifiedAt(LocalDateTime.now())
                                    .confidenceScore(0.8)
                                    .generationPrompt(prompt)
                                    .build();

        return testCase;
    }

    /**
     * Generate a test for a detail view page
     */
    private TestCase generateDetailViewTest(Page page) {
        logger.info("Generating detail view test for page: {}", page.getUrl());

        // Get all visible components to verify
        List<UIComponent> visibleComponents = page.getComponents().stream()
                                                  .filter(c -> !"button".equals(c.getType()))
                                                  .limit(5) // Limit to prevent overly complex tests
                                                  .collect(Collectors.toList());

        // Generate test code with AI
        String prompt = builder.buildDetailViewTestPrompt(page, visibleComponents);
        String testCode = aiModelService.generateText(prompt, 1000);

        // Create test case
        TestCase testCase = TestCase.builder()
                                    .id(UUID.randomUUID())
                                    .name("Test_DetailView_" + sanitizeForMethodName(page.getTitle()))
                                    .description("Test detail view verification on " + page.getTitle())
                                    .type(TestCase.TestType.UI)
                                    .priority(TestCase.TestPriority.MEDIUM)
                                    .status(TestCase.TestStatus.GENERATED)
                                    .packageName("com.projectoracle.test.ui")
                                    .className("DetailViewTest")
                                    .methodName("testDetailView_" + sanitizeForMethodName(page.getTitle()))
                                    .sourceCode(testCode)
                                    .createdAt(LocalDateTime.now())
                                    .modifiedAt(LocalDateTime.now())
                                    .confidenceScore(0.8)
                                    .generationPrompt(prompt)
                                    .build();

        return testCase;
    }

    /**
     * Generate a test for a dashboard page
     */
    private TestCase generateDashboardElementsTest(Page page) {
        logger.info("Generating dashboard elements test for page: {}", page.getUrl());

        // Find dashboard components to verify (charts, stats)
        List<UIComponent> dashboardComponents = page.getComponents().stream()
                                                    .filter(c -> "chart".equals(c.getType()) ||
                                                            (c.getClassName() != null &&
                                                                    (c.getClassName().contains("chart") ||
                                                                            c.getClassName().contains("stat") ||
                                                                            c.getClassName().contains("dashboard"))))
                                                    .collect(Collectors.toList());

        // Generate test code with AI
        String prompt = builder.buildDashboardTestPrompt(page, dashboardComponents);
        String testCode = aiModelService.generateText(prompt, 1000);

        // Create test case
        TestCase testCase = TestCase.builder()
                                    .id(UUID.randomUUID())
                                    .name("Test_Dashboard_" + sanitizeForMethodName(page.getTitle()))
                                    .description("Test dashboard elements on " + page.getTitle())
                                    .type(TestCase.TestType.UI)
                                    .priority(TestCase.TestPriority.MEDIUM)
                                    .status(TestCase.TestStatus.GENERATED)
                                    .packageName("com.projectoracle.test.ui")
                                    .className("DashboardTest")
                                    .methodName("testDashboard_" + sanitizeForMethodName(page.getTitle()))
                                    .sourceCode(testCode)
                                    .createdAt(LocalDateTime.now())
                                    .modifiedAt(LocalDateTime.now())
                                    .confidenceScore(0.8)
                                    .generationPrompt(prompt)
                                    .build();

        return testCase;
    }

    /**
     * Generate a simple navigation test
     */
    private TestCase generateNavigationTest(Page page) {
        logger.info("Generating navigation test for page: {}", page.getUrl());

        // Generate test code with AI
        String prompt = builder.buildNavigationTestPrompt(page);
        String testCode = aiModelService.generateText(prompt, 800);

        // Create test case
        TestCase testCase = TestCase.builder()
                                    .id(UUID.randomUUID())
                                    .name("Test_Navigation_" + sanitizeForMethodName(page.getTitle()))
                                    .description("Test navigation to " + page.getTitle())
                                    .type(TestCase.TestType.UI)
                                    .priority(TestCase.TestPriority.LOW)
                                    .status(TestCase.TestStatus.GENERATED)
                                    .packageName("com.projectoracle.test.ui")
                                    .className("NavigationTest")
                                    .methodName("testNavigation_" + sanitizeForMethodName(page.getTitle()))
                                    .sourceCode(testCode)
                                    .createdAt(LocalDateTime.now())
                                    .modifiedAt(LocalDateTime.now())
                                    .confidenceScore(0.75)
                                    .generationPrompt(prompt)
                                    .build();

        return testCase;
    }

    /**
     * Generate a test for component interactions
     */
    private TestCase generateComponentInteractionTest(Page page) {
        logger.info("Generating component interaction test for page: {}", page.getUrl());

        // Find interactive components
        List<UIComponent> interactiveComponents = page.getComponents().stream()
                                                      .filter(UIComponent::isInteractive)
                                                      .limit(5) // Limit to prevent overly complex tests
                                                      .collect(Collectors.toList());

        if (interactiveComponents.isEmpty()) {
            logger.warn("No interactive components found for interaction test");
            return null;
        }

        // Generate test code with AI
        String prompt = builder.buildInteractionTestPrompt(page, interactiveComponents);
        String testCode = aiModelService.generateText(prompt, 1000);

        // Create test case
        TestCase testCase = TestCase.builder()
                                    .id(UUID.randomUUID())
                                    .name("Test_Interaction_" + sanitizeForMethodName(page.getTitle()))
                                    .description("Test component interactions on " + page.getTitle())
                                    .type(TestCase.TestType.UI)
                                    .priority(TestCase.TestPriority.MEDIUM)
                                    .status(TestCase.TestStatus.GENERATED)
                                    .packageName("com.projectoracle.test.ui")
                                    .className("InteractionTest")
                                    .methodName("testInteraction_" + sanitizeForMethodName(page.getTitle()))
                                    .sourceCode(testCode)
                                    .createdAt(LocalDateTime.now())
                                    .modifiedAt(LocalDateTime.now())
                                    .confidenceScore(0.8)
                                    .generationPrompt(prompt)
                                    .build();

        return testCase;
    }

    /**
     * Generate end-to-end flow tests across multiple pages
     */
    private List<TestCase> generateEndToEndFlows(List<Page> pages) {
        logger.info("Generating end-to-end flow tests");
        List<TestCase> flowTests = new ArrayList<>();

        // Find login page
        Page loginPage = pages.stream()
                              .filter(p -> p.getPageType() == Page.PageType.LOGIN)
                              .findFirst()
                              .orElse(null);

        // Find form pages
        List<Page> formPages = pages.stream()
                                    .filter(p -> p.getPageType() == Page.PageType.FORM)
                                    .collect(Collectors.toList());

        // Generate login-to-dashboard flow if possible
        if (loginPage != null) {
            // Find dashboard page
            Page dashboardPage = pages.stream()
                                      .filter(p -> p.getPageType() == Page.PageType.DASHBOARD)
                                      .findFirst()
                                      .orElse(null);

            if (dashboardPage != null) {
                flowTests.add(generateLoginToDashboardTest(loginPage, dashboardPage));
            }
        }

        // Generate form submission flow if possible
        if (!formPages.isEmpty()) {
            // Take first form page for simplicity
            Page formPage = formPages.get(0);
            flowTests.add(generateFormSubmissionFlowTest(formPage, pages));
        }

        // Generate listing-to-detail flow if possible
        Page listingPage = pages.stream()
                                .filter(p -> p.getPageType() == Page.PageType.LISTING)
                                .findFirst()
                                .orElse(null);

        Page detailPage = pages.stream()
                               .filter(p -> p.getPageType() == Page.PageType.DETAIL)
                               .findFirst()
                               .orElse(null);

        if (listingPage != null && detailPage != null) {
            flowTests.add(generateListingToDetailTest(listingPage, detailPage));
        }

        return flowTests;
    }

    /**
     * Generate a login-to-dashboard flow test
     */
    private TestCase generateLoginToDashboardTest(Page loginPage, Page dashboardPage) {
        logger.info("Generating login-to-dashboard flow test");

        // Find login components
        UIComponent usernameField = findComponentBySubtype(loginPage, "input", "text");
        UIComponent passwordField = findComponentBySubtype(loginPage, "input", "password");
        UIComponent loginButton = findComponentByType(loginPage, "button");

        // Find dashboard components to verify
        List<UIComponent> dashboardComponents = dashboardPage.getComponents().stream()
                                                             .limit(3)
                                                             .collect(Collectors.toList());

        // Generate test code with AI
        String prompt = builder.buildLoginToDashboardTestPrompt(loginPage, usernameField,
                passwordField, loginButton,
                dashboardPage, dashboardComponents);
        String testCode = aiModelService.generateText(prompt, 1500);

        // Create test case
        TestCase testCase = TestCase.builder()
                                    .id(UUID.randomUUID())
                                    .name("Test_LoginToDashboard_Flow")
                                    .description("End-to-end test from login to dashboard")
                                    .type(TestCase.TestType.UI)
                                    .priority(TestCase.TestPriority.CRITICAL)
                                    .status(TestCase.TestStatus.GENERATED)
                                    .packageName("com.projectoracle.test.ui.flow")
                                    .className("LoginToDashboardFlowTest")
                                    .methodName("testLoginToDashboardFlow")
                                    .sourceCode(testCode)
                                    .createdAt(LocalDateTime.now())
                                    .modifiedAt(LocalDateTime.now())
                                    .confidenceScore(0.9)
                                    .generationPrompt(prompt)
                                    .build();

        return testCase;
    }

    /**
     * Generate a form submission flow test
     */
    private TestCase generateFormSubmissionFlowTest(Page formPage, List<Page> allPages) {
        logger.info("Generating form submission flow test");

        // Get form components
        List<UIComponent> formComponents = formPage.getComponents().stream()
                                                   .filter(c -> "input".equals(c.getType()) ||
                                                           "select".equals(c.getType()) ||
                                                           "textarea".equals(c.getType()))
                                                   .collect(Collectors.toList());

        UIComponent submitButton = findComponentByType(formPage, "button");

        // Generate test code with AI
        String prompt = builder.buildFormSubmissionFlowTestPrompt(formPage, formComponents, submitButton);
        String testCode = aiModelService.generateText(prompt, 1500);

        // Create test case
        TestCase testCase = TestCase.builder()
                                    .id(UUID.randomUUID())
                                    .name("Test_FormSubmission_Flow")
                                    .description("End-to-end test for form submission flow")
                                    .type(TestCase.TestType.UI)
                                    .priority(TestCase.TestPriority.HIGH)
                                    .status(TestCase.TestStatus.GENERATED)
                                    .packageName("com.projectoracle.test.ui.flow")
                                    .className("FormSubmissionFlowTest")
                                    .methodName("testFormSubmissionFlow")
                                    .sourceCode(testCode)
                                    .createdAt(LocalDateTime.now())
                                    .modifiedAt(LocalDateTime.now())
                                    .confidenceScore(0.85)
                                    .generationPrompt(prompt)
                                    .build();

        return testCase;
    }

    /**
     * Generate a listing-to-detail flow test
     */
    private TestCase generateListingToDetailTest(Page listingPage, Page detailPage) {
        logger.info("Generating listing-to-detail flow test");

        // Find table or list elements
        UIComponent tableComponent = findComponentByType(listingPage, "table");
        List<UIComponent> linkComponents = listingPage.getComponents().stream()
                                                      .filter(c -> "link".equals(c.getType()))
                                                      .collect(Collectors.toList());

        // Find detail components to verify
        List<UIComponent> detailComponents = detailPage.getComponents().stream()
                                                       .limit(3)
                                                       .collect(Collectors.toList());

        // Generate test code with AI
        String prompt = builder.buildListingToDetailTestPrompt(listingPage, tableComponent,
                linkComponents, detailPage,
                detailComponents);
        String testCode = aiModelService.generateText(prompt, 1500);

        // Create test case
        TestCase testCase = TestCase.builder()
                                    .id(UUID.randomUUID())
                                    .name("Test_ListingToDetail_Flow")
                                    .description("End-to-end test from listing to detail view")
                                    .type(TestCase.TestType.UI)
                                    .priority(TestCase.TestPriority.HIGH)
                                    .status(TestCase.TestStatus.GENERATED)
                                    .packageName("com.projectoracle.test.ui.flow")
                                    .className("ListingToDetailFlowTest")
                                    .methodName("testListingToDetailFlow")
                                    .sourceCode(testCode)
                                    .createdAt(LocalDateTime.now())
                                    .modifiedAt(LocalDateTime.now())
                                    .confidenceScore(0.85)
                                    .generationPrompt(prompt)
                                    .build();

        return testCase;
    }

    /* Helper methods */

    /**
     * Find a component by type
     */
    private UIComponent findComponentByType(Page page, String type) {
        return page.getComponents().stream()
                   .filter(c -> type.equals(c.getType()))
                   .findFirst()
                   .orElse(null);
    }

    /**
     * Find a component by type and subtype
     */
    private UIComponent findComponentBySubtype(Page page, String type, String subtype) {
        return page.getComponents().stream()
                   .filter(c -> type.equals(c.getType()) && subtype.equals(c.getSubtype()))
                   .findFirst()
                   .orElse(null);
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
            sanitized = "Page_" + sanitized;
        }

        // Limit length
        if (sanitized.length() > 30) {
            sanitized = sanitized.substring(0, 30);
        }

        return sanitized;
    }
}