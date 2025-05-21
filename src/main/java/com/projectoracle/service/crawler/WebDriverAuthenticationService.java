package com.projectoracle.service.crawler;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.projectoracle.config.CrawlerConfig;

import java.time.Duration;

/**
 * Service for handling authentication operations with WebDriver.
 * Manages login procedures for web application crawling.
 */
@Service
public class WebDriverAuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(WebDriverAuthenticationService.class);

    @Autowired
    private CrawlerConfig crawlerConfig;

    /**
     * Performs login to a web application using the provided WebDriver
     *
     * @param driver WebDriver instance to use for login
     * @return true if login was successful, false otherwise
     */
    public boolean performLogin(WebDriver driver) {
        return performLogin(driver, 
                crawlerConfig.getUsername(), 
                crawlerConfig.getPassword(), 
                crawlerConfig.getLoginUrl());
    }

    /**
     * Performs login to a web application using the provided WebDriver and credentials
     *
     * @param driver   WebDriver instance to use for login
     * @param username The username to use
     * @param password The password to use
     * @param loginUrl The URL of the login page
     * @return true if login was successful, false otherwise
     */
    public boolean performLogin(WebDriver driver, String username, String password, String loginUrl) {
        if (!crawlerConfig.isHandleAuthentication() || username.isEmpty() || password.isEmpty() || loginUrl.isEmpty()) {
            logger.info("Authentication is disabled or missing credentials/login URL");
            return false;
        }

        try {
            logger.info("Attempting to log in at URL: {}", loginUrl);

            // Navigate to login page
            driver.get(loginUrl);

            // Wait for page to load
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // Find username field
            WebElement usernameField = findElement(driver, crawlerConfig.getUsernameSelector());
            if (usernameField == null) {
                logger.error("Could not find username field with selector: {}", crawlerConfig.getUsernameSelector());
                return false;
            }

            // Find password field
            WebElement passwordField = findElement(driver, crawlerConfig.getPasswordSelector());
            if (passwordField == null) {
                logger.error("Could not find password field with selector: {}", crawlerConfig.getPasswordSelector());
                return false;
            }

            // Find login button
            WebElement loginButton = findElement(driver, crawlerConfig.getLoginButtonSelector());
            if (loginButton == null) {
                logger.error("Could not find login button with selector: {}", crawlerConfig.getLoginButtonSelector());
                return false;
            }

            // Clear fields and enter credentials
            usernameField.clear();
            usernameField.sendKeys(username);
            
            passwordField.clear();
            passwordField.sendKeys(password);

            // Click login button
            loginButton.click();

            // Wait a moment for login to process
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Verify login success - this is application specific and might need customization
            // For now, we'll just check if we're no longer on the login page
            boolean loginSuccessful = !driver.getCurrentUrl().equals(loginUrl);
            logger.info("Login attempt {}", loginSuccessful ? "successful" : "failed");
            
            return loginSuccessful;
        } catch (Exception e) {
            logger.error("Error during login process", e);
            return false;
        }
    }

    /**
     * Helper method to find an element using multiple strategies
     *
     * @param driver   WebDriver instance
     * @param selector CSS or XPath selector
     * @return WebElement if found, null otherwise
     */
    private WebElement findElement(WebDriver driver, String selector) {
        try {
            // Try as CSS selector first
            return driver.findElement(By.cssSelector(selector));
        } catch (Exception e) {
            try {
                // Try as XPath if CSS fails
                return driver.findElement(By.xpath(selector));
            } catch (Exception ex) {
                try {
                    // Try as ID if XPath fails
                    return driver.findElement(By.id(selector));
                } catch (Exception ex2) {
                    try {
                        // Try as name if ID fails
                        return driver.findElement(By.name(selector));
                    } catch (Exception ex3) {
                        // Element not found with any strategy
                        return null;
                    }
                }
            }
        }
    }
}