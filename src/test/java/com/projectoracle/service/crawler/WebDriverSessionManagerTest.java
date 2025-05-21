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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

import io.github.bonigarcia.wdm.WebDriverManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WebDriverSessionManagerTest {

    @Mock
    private CrawlerConfig crawlerConfig;

    @Mock
    private WebDriver mockDriver;

    @Mock
    private ChromeDriver mockChromeDriver;

    @Mock
    private FirefoxDriver mockFirefoxDriver;

    @Mock
    private EdgeDriver mockEdgeDriver;

    @InjectMocks
    private WebDriverSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        // Mock crawler config
        when(crawlerConfig.getUserAgent()).thenReturn("Sentinel Crawler/1.0");
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

    @Test
    void testCreateSession() {
        try (MockedStatic<WebDriverManager> mockedStatic = Mockito.mockStatic(WebDriverManager.class)) {
            // Mock WebDriverManager
            WebDriverManager chromeManager = mock(WebDriverManager.class);
            mockedStatic.when(WebDriverManager::chromedriver).thenReturn(chromeManager);
            
            // Mock ChromeDriver constructor
            try (MockedStatic<ChromeDriver> mockedChromeDriver = Mockito.mockStatic(ChromeDriver.class)) {
                mockedChromeDriver.when(() -> new ChromeDriver(any(ChromeOptions.class))).thenReturn(mockChromeDriver);
                
                // Call the method
                String sessionId = sessionManager.createSession(WebDriverSessionManager.BrowserType.CHROME, true);
                
                // Verify session creation
                assertNotNull(sessionId);
                assertTrue(sessionId.length() > 0);
                
                // Get the session and verify
                WebDriver driver = sessionManager.getDriver(sessionId);
                assertNotNull(driver);
                assertEquals(mockChromeDriver, driver);
            }
        }
    }

    @Test
    void testReleaseSession() {
        // Create a session to release
        try (MockedStatic<WebDriverManager> mockedStatic = Mockito.mockStatic(WebDriverManager.class)) {
            // Mock WebDriverManager
            WebDriverManager chromeManager = mock(WebDriverManager.class);
            mockedStatic.when(WebDriverManager::chromedriver).thenReturn(chromeManager);
            
            // Mock ChromeDriver constructor
            try (MockedStatic<ChromeDriver> mockedChromeDriver = Mockito.mockStatic(ChromeDriver.class)) {
                mockedChromeDriver.when(() -> new ChromeDriver(any(ChromeOptions.class))).thenReturn(mockChromeDriver);
                
                // Create a session
                String sessionId = sessionManager.createSession(WebDriverSessionManager.BrowserType.CHROME, true);
                
                // Release the session
                sessionManager.releaseSession(sessionId);
                
                // Verify session is not in use but still exists
                WebDriver driver = sessionManager.getDriver(sessionId);
                assertNotNull(driver);
            }
        }
    }

    @Test
    void testCloseSession() {
        // Create a session to close
        try (MockedStatic<WebDriverManager> mockedStatic = Mockito.mockStatic(WebDriverManager.class)) {
            // Mock WebDriverManager
            WebDriverManager chromeManager = mock(WebDriverManager.class);
            mockedStatic.when(WebDriverManager::chromedriver).thenReturn(chromeManager);
            
            // Mock ChromeDriver constructor
            try (MockedStatic<ChromeDriver> mockedChromeDriver = Mockito.mockStatic(ChromeDriver.class)) {
                mockedChromeDriver.when(() -> new ChromeDriver(any(ChromeOptions.class))).thenReturn(mockChromeDriver);
                
                // Create a session
                String sessionId = sessionManager.createSession(WebDriverSessionManager.BrowserType.CHROME, true);
                assertNotNull(sessionManager.getDriver(sessionId));
                
                // Close the session
                sessionManager.closeSession(sessionId);
                
                // Verify session is closed (driver.quit was called)
                verify(mockChromeDriver).quit();
                
                // Verify session is removed
                assertNull(sessionManager.getDriver(sessionId));
            }
        }
    }
}