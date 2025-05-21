package com.projectoracle.service.crawler;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.projectoracle.config.CrawlerConfig;

@Configuration
public class WebDriverConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(WebDriverConfig.class);
    
    @Autowired
    private CrawlerConfig crawlerConfig;

    @Bean
    @Primary
    @Lazy // Lazy load to avoid startup issues
    public WebDriver chromeDriver() {
        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--user-agent=" + crawlerConfig.getUserAgent());
            return new ChromeDriver(options);
        } catch (Exception e) {
            logger.error("Failed to initialize Chrome driver. Will be created on demand.", e);
            // Return null and let the WebDriverSessionManager handle it
            return null;
        }
    }

    @Bean
    @Lazy // Lazy load to avoid startup issues
    public WebDriver firefoxDriver() {
        try {
            WebDriverManager.firefoxdriver().setup();
            FirefoxOptions options = new FirefoxOptions();
            options.addArguments("-headless");
            options.addPreference("general.useragent.override", crawlerConfig.getUserAgent());
            return new FirefoxDriver(options);
        } catch (Exception e) {
            logger.error("Failed to initialize Firefox driver. Will be created on demand.", e);
            // Return null and let the WebDriverSessionManager handle it
            return null;
        }
    }

    @Bean
    @Lazy // Lazy load to avoid startup issues
    public WebDriver edgeDriver() {
        try {
            WebDriverManager.edgedriver().setup();
            EdgeOptions options = new EdgeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--user-agent=" + crawlerConfig.getUserAgent());
            return new EdgeDriver(options);
        } catch (Exception e) {
            logger.error("Failed to initialize Edge driver. Will be created on demand.", e);
            // Return null and let the WebDriverSessionManager handle it
            return null;
        }
    }
}