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
 * Service for building prompts for different test types.
 * Part of the UITestGenerationService with prompt-building functionality.
 */
@Service
public class UITestPromptBuilder {

    private static final Logger logger = LoggerFactory.getLogger(UITestPromptBuilder.class);

    /**
     * Build a prompt for a login test
     */
    public String buildLoginTestPrompt(Page page, UIComponent usernameField,
            UIComponent passwordField, UIComponent submitButton) {
        return "Generate a Selenium Java test for a login page with the following details:\n\n" +
                "Page URL: " + page.getUrl() + "\n" +
                "Page Title: " + page.getTitle() + "\n\n" +

                "Username field: " + usernameField.getDescription() + "\n" +
                "Username field locator: " + usernameField.getElementLocator() + "\n\n" +

                "Password field: " + passwordField.getDescription() + "\n" +
                "Password field locator: " + passwordField.getElementLocator() + "\n\n" +

                "Submit button: " + submitButton.getDescription() + "\n" +
                "Submit button locator: " + submitButton.getElementLocator() + "\n\n" +

                "The test should:\n" +
                "1. Navigate to the login page\n" +
                "2. Enter test credentials (use test@example.com and Password123)\n" +
                "3. Click the submit button\n" +
                "4. Verify successful login\n\n" +

                "Use JUnit 5 and WebDriver. Include proper assertions and error handling.";
    }

    /**
     * Build a prompt for a form submission test
     */
    public String buildFormTestPrompt(Page page, List<UIComponent> formComponents, UIComponent submitButton) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a Selenium Java test for a form submission with these details:\n\n")
              .append("Page URL: ").append(page.getUrl()).append("\n")
              .append("Page Title: ").append(page.getTitle()).append("\n\n")
              .append("Form components:\n");

        // Add details for each form component
        for (UIComponent component : formComponents) {
            prompt.append("- ").append(component.getDescription()).append("\n")
                  .append("  Locator: ").append(component.getElementLocator()).append("\n")
                  .append("  Test value: ").append(component.generateTestValue()).append("\n\n");
        }

        prompt.append("Submit button: ").append(submitButton.getDescription()).append("\n")
              .append("Submit button locator: ").append(submitButton.getElementLocator()).append("\n\n")

              .append("The test should:\n")
              .append("1. Navigate to the form page\n")
              .append("2. Fill all form fields with appropriate test values\n")
              .append("3. Submit the form\n")
              .append("4. Verify successful submission\n\n")

              .append("Use JUnit 5 and WebDriver. Include proper assertions and error handling.");

        return prompt.toString();
    }

    /**
     * Build a prompt for a listing navigation test
     */
    public String buildListingTestPrompt(Page page, UIComponent tableComponent, List<UIComponent> linkComponents) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a Selenium Java test for a listing page with these details:\n\n")
              .append("Page URL: ").append(page.getUrl()).append("\n")
              .append("Page Title: ").append(page.getTitle()).append("\n\n");

        if (tableComponent != null) {
            prompt.append("Table component: ").append(tableComponent.getDescription()).append("\n")
                  .append("Table locator: ").append(tableComponent.getElementLocator()).append("\n\n");
        }

        if (!linkComponents.isEmpty()) {
            prompt.append("Link components:\n");
            for (int i = 0; i < Math.min(3, linkComponents.size()); i++) {
                UIComponent link = linkComponents.get(i);
                prompt.append("- ").append(link.getDescription()).append("\n")
                      .append("  Locator: ").append(link.getElementLocator()).append("\n\n");
            }
        }

        prompt.append("The test should:\n")
              .append("1. Navigate to the listing page\n")
              .append("2. Verify the presence of the table/list\n")
              .append("3. Verify there are entries in the table/list\n");

        if (!linkComponents.isEmpty()) {
            prompt.append("4. Click on the first link and navigate to a detail page\n");
        }

        prompt.append("\nUse JUnit 5 and WebDriver. Include proper assertions and error handling.");

        return prompt.toString();
    }

    /**
     * Build a prompt for a detail view test
     */
    public String buildDetailViewTestPrompt(Page page, List<UIComponent> visibleComponents) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a Selenium Java test for a detail view page with these details:\n\n")
              .append("Page URL: ").append(page.getUrl()).append("\n")
              .append("Page Title: ").append(page.getTitle()).append("\n\n")
              .append("Components to verify:\n");

        for (UIComponent component : visibleComponents) {
            prompt.append("- ").append(component.getDescription()).append("\n")
                  .append("  Locator: ").append(component.getElementLocator()).append("\n\n");
        }

        prompt.append("The test should:\n")
              .append("1. Navigate to the detail page\n")
              .append("2. Verify the page title\n")
              .append("3. Verify the presence of all key components\n")
              .append("4. Verify any visible data/content in components\n\n")

              .append("Use JUnit 5 and WebDriver. Include proper assertions and error handling.");

        return prompt.toString();
    }

    /**
     * Build a prompt for a dashboard test
     */
    public String buildDashboardTestPrompt(Page page, List<UIComponent> dashboardComponents) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a Selenium Java test for a dashboard page with these details:\n\n")
              .append("Page URL: ").append(page.getUrl()).append("\n")
              .append("Page Title: ").append(page.getTitle()).append("\n\n");

        if (!dashboardComponents.isEmpty()) {
            prompt.append("Dashboard components to verify:\n");
            for (UIComponent component : dashboardComponents) {
                prompt.append("- ").append(component.getDescription()).append("\n")
                      .append("  Locator: ").append(component.getElementLocator()).append("\n\n");
            }
        }

        prompt.append("The test should:\n")
              .append("1. Navigate to the dashboard page\n")
              .append("2. Verify the page title\n")
              .append("3. Verify that all dashboard components are present and visible\n")
              .append("4. Take a screenshot of the dashboard\n\n")

              .append("Use JUnit 5 and WebDriver. Include proper assertions and error handling.");

        return prompt.toString();
    }

    /**
     * Build a prompt for a navigation test
     */
    public String buildNavigationTestPrompt(Page page) {
        return "Generate a Selenium Java test for navigating to a page with these details:\n\n" +
                "Page URL: " + page.getUrl() + "\n" +
                "Page Title: " + page.getTitle() + "\n\n" +

                "The test should:\n" +
                "1. Navigate to the page URL\n" +
                "2. Verify that the page loaded successfully (title, URL)\n" +
                "3. Verify basic page structure (presence of header, footer, etc.)\n\n" +

                "Use JUnit 5 and WebDriver. Include proper assertions and error handling.";
    }

    /**
     * Build a prompt for an interaction test
     */
    public String buildInteractionTestPrompt(Page page, List<UIComponent> interactiveComponents) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a Selenium Java test for component interactions with these details:\n\n")
              .append("Page URL: ").append(page.getUrl()).append("\n")
              .append("Page Title: ").append(page.getTitle()).append("\n\n")
              .append("Interactive components:\n");

        for (UIComponent component : interactiveComponents) {
            prompt.append("- ").append(component.getDescription()).append("\n")
                  .append("  Locator: ").append(component.getElementLocator()).append("\n")
                  .append("  Type: ").append(component.getType())
                  .append(component.getSubtype() != null ? " (" + component.getSubtype() + ")" : "")
                  .append("\n\n");
        }

        prompt.append("The test should:\n")
              .append("1. Navigate to the page\n")
              .append("2. Interact with each component appropriately (click buttons, fill inputs, etc.)\n")
              .append("3. Verify the expected behavior or response for each interaction\n\n")

              .append("Use JUnit 5 and WebDriver. Include proper assertions and error handling.");

        return prompt.toString();
    }

    /**
     * Build a prompt for a login-to-dashboard flow test
     */
    public String buildLoginToDashboardTestPrompt(Page loginPage, UIComponent usernameField,
            UIComponent passwordField, UIComponent loginButton,
            Page dashboardPage, List<UIComponent> dashboardComponents) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a Selenium Java E2E test for login to dashboard flow with these details:\n\n")
              .append("Login Page URL: ").append(loginPage.getUrl()).append("\n")
              .append("Login Page Title: ").append(loginPage.getTitle()).append("\n\n")

              .append("Username field: ").append(usernameField.getDescription()).append("\n")
              .append("Username field locator: ").append(usernameField.getElementLocator()).append("\n\n")

              .append("Password field: ").append(passwordField.getDescription()).append("\n")
              .append("Password field locator: ").append(passwordField.getElementLocator()).append("\n\n")

              .append("Login button: ").append(loginButton.getDescription()).append("\n")
              .append("Login button locator: ").append(loginButton.getElementLocator()).append("\n\n")

              .append("Dashboard Page URL: ").append(dashboardPage.getUrl()).append("\n")
              .append("Dashboard Page Title: ").append(dashboardPage.getTitle()).append("\n\n");

        if (!dashboardComponents.isEmpty()) {
            prompt.append("Dashboard components to verify:\n");
            for (UIComponent component : dashboardComponents) {
                prompt.append("- ").append(component.getDescription()).append("\n")
                      .append("  Locator: ").append(component.getElementLocator()).append("\n\n");
            }
        }

        prompt.append("The test should:\n")
              .append("1. Navigate to the login page\n")
              .append("2. Enter test credentials (use test@example.com and Password123)\n")
              .append("3. Click the login button\n")
              .append("4. Verify successful redirect to dashboard\n")
              .append("5. Verify dashboard components are present and visible\n\n")

              .append("Use JUnit 5 and WebDriver. Include proper assertions and error handling.");

        return prompt.toString();
    }

    /**
     * Build a prompt for a form submission flow test
     */
    public String buildFormSubmissionFlowTestPrompt(Page formPage, List<UIComponent> formComponents,
            UIComponent submitButton) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a Selenium Java E2E test for form submission flow with these details:\n\n")
              .append("Form Page URL: ").append(formPage.getUrl()).append("\n")
              .append("Form Page Title: ").append(formPage.getTitle()).append("\n\n")
              .append("Form components:\n");

        // Add details for each form component
        for (UIComponent component : formComponents) {
            prompt.append("- ").append(component.getDescription()).append("\n")
                  .append("  Locator: ").append(component.getElementLocator()).append("\n")
                  .append("  Test value: ").append(component.generateTestValue()).append("\n\n");
        }

        prompt.append("Submit button: ").append(submitButton.getDescription()).append("\n")
              .append("Submit button locator: ").append(submitButton.getElementLocator()).append("\n\n")

              .append("The test should:\n")
              .append("1. Navigate to the form page\n")
              .append("2. Fill all form fields with appropriate test values\n")
              .append("3. Submit the form\n")
              .append("4. Verify successful submission (confirmation page or message)\n")
              .append("5. Test any post-submission actions or navigation\n\n")

              .append("Use JUnit 5 and WebDriver. Include proper assertions and error handling.");

        return prompt.toString();
    }

    /**
     * Build a prompt for a listing-to-detail flow test
     */
    public String buildListingToDetailTestPrompt(Page listingPage, UIComponent tableComponent,
            List<UIComponent> linkComponents, Page detailPage,
            List<UIComponent> detailComponents) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a Selenium Java E2E test for listing-to-detail flow with these details:\n\n")
              .append("Listing Page URL: ").append(listingPage.getUrl()).append("\n")
              .append("Listing Page Title: ").append(listingPage.getTitle()).append("\n\n");

        if (tableComponent != null) {
            prompt.append("Table component: ").append(tableComponent.getDescription()).append("\n")
                  .append("Table locator: ").append(tableComponent.getElementLocator()).append("\n\n");
        }

        if (!linkComponents.isEmpty()) {
            prompt.append("Link components (to click on):\n");
            UIComponent linkToClick = linkComponents.get(0);
            prompt.append("- ").append(linkToClick.getDescription()).append("\n")
                  .append("  Locator: ").append(linkToClick.getElementLocator()).append("\n\n");
        }

        prompt.append("Detail Page URL pattern: ").append(detailPage.getUrl()).append("\n")
              .append("Detail Page Title pattern: ").append(detailPage.getTitle()).append("\n\n")
              .append("Detail components to verify:\n");

        for (UIComponent component : detailComponents) {
            prompt.append("- ").append(component.getDescription()).append("\n")
                  .append("  Locator: ").append(component.getElementLocator()).append("\n\n");
        }

        prompt.append("The test should:\n")
              .append("1. Navigate to the listing page\n")
              .append("2. Verify the list/table is present and contains items\n")
              .append("3. Click on the first item/link\n")
              .append("4. Verify navigation to the detail page\n")
              .append("5. Verify the detail components are present and contain expected data\n\n")

              .append("Use JUnit 5 and WebDriver. Include proper assertions and error handling.");

        return prompt.toString();
    }
}