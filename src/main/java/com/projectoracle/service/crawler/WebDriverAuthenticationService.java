package com.projectoracle.service.crawler;

import java.util.List;
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
        if (!crawlerConfig.isHandleAuthentication() || username.isEmpty() || password.isEmpty()) {
            logger.info("Authentication is disabled or missing credentials");
            return false;
        }

        try {
            String currentUrl = driver.getCurrentUrl();
            logger.info("Current URL: {}, Login URL: {}", currentUrl, loginUrl);
            
            // Check if we need to navigate to login page
            boolean needsNavigation = true;
            
            // If loginUrl is empty, check if we're already on a login page
            if (loginUrl == null || loginUrl.isEmpty()) {
                logger.info("No login URL provided, checking if current page is a login page");
                if (isLoginPage(driver)) {
                    logger.info("Already on a login page, no navigation needed");
                    needsNavigation = false;
                } else {
                    logger.error("Not on a login page and no login URL provided");
                    return false;
                }
            } else {
                // Check if we're already on the login page
                if (currentUrl.equals(loginUrl) || isLoginPage(driver)) {
                    logger.info("Already on login page, no navigation needed");
                    needsNavigation = false;
                }
            }
            
            if (needsNavigation) {
                logger.info("Navigating to login page: {}", loginUrl);
                driver.get(loginUrl);
                
                // Wait for page to load
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            }

            // Find username field - try multiple strategies
            WebElement usernameField = findUsernameField(driver);
            if (usernameField == null) {
                logger.error("Could not find username field");
                return false;
            }

            // Find password field - try multiple strategies
            WebElement passwordField = findPasswordField(driver);
            if (passwordField == null) {
                logger.error("Could not find password field");
                return false;
            }

            // Find login button - try multiple strategies
            WebElement loginButton = findLoginButton(driver);
            if (loginButton == null) {
                logger.error("Could not find login button");
                return false;
            }

            // Clear fields and enter credentials
            logger.info("Entering credentials for user: {}", username);
            usernameField.clear();
            usernameField.sendKeys(username);
            
            passwordField.clear();
            passwordField.sendKeys(password);

            // Store current URL before clicking login
            String preLoginUrl = driver.getCurrentUrl();
            
            // Click login button
            logger.info("Clicking login button");
            loginButton.click();

            // Wait for login to process - either URL change or page reload
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            try {
                wait.until(driver1 -> {
                    String newUrl = driver1.getCurrentUrl();
                    // Check if URL changed or if we're no longer on a login page
                    return !newUrl.equals(preLoginUrl) || !isLoginPage(driver1);
                });
            } catch (Exception e) {
                logger.warn("Timeout waiting for login to complete");
            }

            // Verify login success
            String postLoginUrl = driver.getCurrentUrl();
            boolean urlChanged = !postLoginUrl.equals(preLoginUrl);
            boolean noLongerOnLoginPage = !isLoginPage(driver);
            boolean loginSuccessful = urlChanged || noLongerOnLoginPage;
            
            logger.info("Login attempt {} - URL changed: {}, Still on login page: {}", 
                loginSuccessful ? "successful" : "failed", urlChanged, !noLongerOnLoginPage);
            
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
    
    /**
     * Check if the current page is a login page
     * 
     * @param driver WebDriver instance
     * @return true if the page appears to be a login page
     */
    private boolean isLoginPage(WebDriver driver) {
        try {
            // Check for password field - most reliable indicator
            List<WebElement> passwordFields = driver.findElements(By.cssSelector("input[type='password']"));
            if (!passwordFields.isEmpty()) {
                // Also check for username/email field
                List<WebElement> userFields = driver.findElements(By.cssSelector(
                    "input[type='text'], input[type='email']"
                ));
                return !userFields.isEmpty();
            }
            
            // Check URL for login indicators
            String url = driver.getCurrentUrl().toLowerCase();
            return url.contains("login") || url.contains("signin") || 
                   url.contains("sign-in") || url.contains("auth");
        } catch (Exception e) {
            logger.error("Error checking if page is login page", e);
            return false;
        }
    }
    
    /**
     * Find username field using multiple strategies
     * 
     * @param driver WebDriver instance
     * @return username field element or null
     */
    private WebElement findUsernameField(WebDriver driver) {
        // First try the configured selector
        WebElement field = findElement(driver, crawlerConfig.getUsernameSelector());
        if (field != null && field.isDisplayed()) {
            return field;
        }
        
        // Try common username field selectors
        String[] selectors = {
            "#user-name", // saucedemo.com specific
            "#username",
            "#user",
            "#email",
            "#login",
            "input[name='user-name']", // saucedemo.com specific
            "input[name='username']",
            "input[name='user']",
            "input[name='email']",
            "input[name='login']",
            "input[type='email']",
            "input[placeholder*='user' i]",
            "input[placeholder*='email' i]",
            "input[placeholder*='login' i]"
        };
        
        for (String selector : selectors) {
            try {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                for (WebElement element : elements) {
                    if (element.isDisplayed()) {
                        logger.info("Found username field with selector: {}", selector);
                        return element;
                    }
                }
            } catch (Exception e) {
                // Continue trying other selectors
            }
        }
        
        // Try finding by proximity to password field
        try {
            WebElement passwordField = findPasswordField(driver);
            if (passwordField != null) {
                // Look for text/email input fields before the password field
                List<WebElement> inputs = driver.findElements(By.cssSelector("input[type='text'], input[type='email']"));
                for (WebElement input : inputs) {
                    if (input.isDisplayed()) {
                        return input;
                    }
                }
            }
        } catch (Exception e) {
            // Continue
        }
        
        return null;
    }
    
    /**
     * Find password field using multiple strategies
     * 
     * @param driver WebDriver instance
     * @return password field element or null
     */
    private WebElement findPasswordField(WebDriver driver) {
        // First try the configured selector
        WebElement field = findElement(driver, crawlerConfig.getPasswordSelector());
        if (field != null && field.isDisplayed()) {
            return field;
        }
        
        // Try common password field selectors
        String[] selectors = {
            "#password", // saucedemo.com specific
            "#pass",
            "#pwd",
            "input[name='password']",
            "input[name='pass']",
            "input[name='pwd']",
            "input[type='password']"
        };
        
        for (String selector : selectors) {
            try {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                for (WebElement element : elements) {
                    if (element.isDisplayed()) {
                        logger.info("Found password field with selector: {}", selector);
                        return element;
                    }
                }
            } catch (Exception e) {
                // Continue trying other selectors
            }
        }
        
        return null;
    }
    
    /**
     * Find login button using multiple strategies
     * 
     * @param driver WebDriver instance
     * @return login button element or null
     */
    private WebElement findLoginButton(WebDriver driver) {
        // First try the configured selector
        WebElement button = findElement(driver, crawlerConfig.getLoginButtonSelector());
        if (button != null && button.isDisplayed()) {
            return button;
        }
        
        // Try common login button selectors
        String[] selectors = {
            "#login-button", // saucedemo.com specific
            "button[type='submit']",
            "input[type='submit']",
            "button#login",
            "button#signin",
            "button.login",
            "button.signin",
            "input[value*='login' i]",
            "input[value*='sign' i]",
            "button:contains('Login')",
            "button:contains('Sign')"
        };
        
        for (String selector : selectors) {
            try {
                List<WebElement> elements;
                if (selector.contains(":contains")) {
                    // Use XPath for text content
                    String text = selector.substring(selector.indexOf("('") + 1, selector.indexOf("')"));
                    elements = driver.findElements(By.xpath(
                        "//button[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '" + 
                        text.toLowerCase() + "')]"
                    ));
                } else {
                    elements = driver.findElements(By.cssSelector(selector));
                }
                
                for (WebElement element : elements) {
                    if (element.isDisplayed()) {
                        logger.info("Found login button with selector: {}", selector);
                        return element;
                    }
                }
            } catch (Exception e) {
                // Continue trying other selectors
            }
        }
        
        // Try finding submit button in a form with password field
        try {
            WebElement passwordField = findPasswordField(driver);
            if (passwordField != null) {
                WebElement form = passwordField.findElement(By.xpath("./ancestor::form"));
                if (form != null) {
                    List<WebElement> submitButtons = form.findElements(By.cssSelector(
                        "button[type='submit'], input[type='submit'], button:not([type])"
                    ));
                    for (WebElement submitButton : submitButtons) {
                        if (submitButton.isDisplayed()) {
                            return submitButton;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Continue
        }
        
        return null;
    }
}