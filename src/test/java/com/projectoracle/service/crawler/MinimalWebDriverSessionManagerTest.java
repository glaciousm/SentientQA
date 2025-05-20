package com.projectoracle.service.crawler;

import com.projectoracle.config.CrawlerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import io.github.bonigarcia.wdm.WebDriverManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Minimal test class for WebDriverSessionManager that doesn't use reflection
 */
@ExtendWith(MockitoExtension.class)
public class MinimalWebDriverSessionManagerTest {

    @Mock
    private CrawlerConfig crawlerConfig;

    @Mock
    private ChromeDriver mockChromeDriver;

    @InjectMocks
    private WebDriverSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        // Mock crawler config
        when(crawlerConfig.getUserAgent()).thenReturn("Project Oracle Crawler/1.0");
        when(crawlerConfig.getPageLoadTimeoutMs()).thenReturn(30000);
    }

    @Test
    void testInitialize() {
        try (MockedStatic<WebDriverManager> mockedStatic = Mockito.mockStatic(WebDriverManager.class)) {
            // Mock WebDriverManager setup methods
            WebDriverManager chromeManager = mock(WebDriverManager.class);
            WebDriverManager firefoxManager = mock(WebDriverManager.class);
            WebDriverManager edgeManager = mock(WebDriverManager.class);
            
            mockedStatic.when(WebDriverManager::chromedriver).thenReturn(chromeManager);
            mockedStatic.when(WebDriverManager::firefoxdriver).thenReturn(firefoxManager);
            mockedStatic.when(WebDriverManager::edgedriver).thenReturn(edgeManager);
            
            // Call the method
            sessionManager.initialize();
            
            // Verify WebDriverManager calls
            mockedStatic.verify(WebDriverManager::chromedriver);
            mockedStatic.verify(WebDriverManager::firefoxdriver);
            mockedStatic.verify(WebDriverManager::edgedriver);
            
            verify(chromeManager).setup();
            verify(firefoxManager).setup();
            verify(edgeManager).setup();
        }
    }
}