package com.projectoracle.service.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.projectoracle.model.ElementFingerprint;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for creating and comparing element fingerprints.
 * Used for reliable element identification during test healing.
 */
@Service
public class ElementFingerprintService {

    private static final Logger logger = LoggerFactory.getLogger(ElementFingerprintService.class);

    /**
     * Create a fingerprint for an element
     *
     * @param driver WebDriver instance
     * @param elementLocator XPath or CSS selector to locate the element
     * @return fingerprint for the element
     */
    public ElementFingerprint createFingerprint(WebDriver driver, String elementLocator) {
        try {
            // Find the element
            WebElement element = findElement(driver, elementLocator);
            if (element == null) {
                logger.warn("Element not found with locator: {}", elementLocator);
                return null;
            }

            // Create fingerprint
            ElementFingerprint fingerprint = new ElementFingerprint();

            // Set basic properties
            fingerprint.setXpath(elementLocator);
            fingerprint.setElementType(element.getTagName());
            fingerprint.setElementId(element.getAttribute("id"));
            fingerprint.setElementName(element.getAttribute("name"));
            fingerprint.setElementText(element.getText());

            // Extract attributes
            extractAttributes(element, fingerprint);

            // Extract properties
            extractProperties(element, fingerprint);

            // Get parent fingerprint
            extractParentInfo(driver, element, fingerprint);

            // Generate visual signature if possible
            generateVisualSignature(driver, element, fingerprint);

            return fingerprint;
        } catch (Exception e) {
            logger.error("Error creating element fingerprint for locator: {}", elementLocator, e);
            return null;
        }
    }

    /**
     * Find an element using the provided locator
     *
     * @param driver WebDriver instance
     * @param elementLocator XPath or CSS selector
     * @return WebElement instance or null if not found
     */
    private WebElement findElement(WebDriver driver, String elementLocator) {
        try {
            if (elementLocator.startsWith("/")) {
                // XPath
                return driver.findElement(By.xpath(elementLocator));
            } else {
                // CSS Selector
                return driver.findElement(By.cssSelector(elementLocator));
            }
        } catch (Exception e) {
            logger.warn("Element not found with locator: {}", elementLocator);
            return null;
        }
    }

    /**
     * Extract element attributes into the fingerprint
     *
     * @param element WebElement to extract from
     * @param fingerprint ElementFingerprint to update
     */
    private void extractAttributes(WebElement element, ElementFingerprint fingerprint) {
        try {
            // Extract common attributes
            String[] attributeNames = {
                    "id", "name", "class", "type", "value", "href", "src", "alt",
                    "placeholder", "title", "role", "aria-label", "data-test-id"
            };

            for (String attrName : attributeNames) {
                String attrValue = element.getAttribute(attrName);
                if (attrValue != null && !attrValue.isEmpty()) {
                    fingerprint.addAttribute(attrName, attrValue);
                }
            }
        } catch (Exception e) {
            logger.warn("Error extracting element attributes", e);
        }
    }

    /**
     * Extract element properties into the fingerprint
     *
     * @param element WebElement to extract from
     * @param fingerprint ElementFingerprint to update
     */
    private void extractProperties(WebElement element, ElementFingerprint fingerprint) {
        try {
            // Extract position and size
            Rectangle rect = element.getRect();
            fingerprint.addProperty("x", rect.getX());
            fingerprint.addProperty("y", rect.getY());
            fingerprint.addProperty("width", rect.getWidth());
            fingerprint.addProperty("height", rect.getHeight());

            // Extract z-index if available
            try {
                String zIndex = element.getCssValue("z-index");
                if (zIndex != null && !zIndex.equals("auto")) {
                    fingerprint.addProperty("z-index", Double.parseDouble(zIndex));
                }
            } catch (Exception e) {
                // Ignore errors getting z-index
            }

            // Extract other CSS values that might be helpful
            String[] cssProperties = {
                    "font-size", "margin-left", "margin-top", "padding-left", "padding-top"
            };

            for (String propName : cssProperties) {
                try {
                    String propValue = element.getCssValue(propName);
                    if (propValue != null && propValue.endsWith("px")) {
                        // Extract px value
                        double value = Double.parseDouble(
                                propValue.substring(0, propValue.length() - 2)
                        );
                        fingerprint.addProperty(propName, value);
                    }
                } catch (Exception e) {
                    // Ignore errors getting CSS values
                }
            }
        } catch (Exception e) {
            logger.warn("Error extracting element properties", e);
        }
    }

    /**
     * Extract parent element information
     *
     * @param driver WebDriver instance
     * @param element WebElement to extract from
     * @param fingerprint ElementFingerprint to update
     */
    private void extractParentInfo(WebDriver driver, WebElement element, ElementFingerprint fingerprint) {
        try {
            // Get parent element using JavaScript
            JavascriptExecutor executor = (JavascriptExecutor) driver;
            WebElement parentElement = (WebElement) executor.executeScript(
                    "return arguments[0].parentNode;", element
            );

            if (parentElement != null) {
                // Create a simplified fingerprint for the parent
                StringBuilder parentFp = new StringBuilder();

                String parentTag = parentElement.getTagName();
                String parentId = parentElement.getAttribute("id");
                String parentClass = parentElement.getAttribute("class");

                parentFp.append(parentTag);

                if (parentId != null && !parentId.isEmpty()) {
                    parentFp.append("#").append(parentId);
                }

                if (parentClass != null && !parentClass.isEmpty()) {
                    parentFp.append(".").append(parentClass);
                }

                fingerprint.setParentFingerprint(parentFp.toString());
            }
        } catch (Exception e) {
            logger.warn("Error extracting parent information", e);
        }
    }

    /**
     * Generate a visual signature for the element
     *
     * @param driver WebDriver instance
     * @param element WebElement to capture
     * @param fingerprint ElementFingerprint to update
     */
    private void generateVisualSignature(WebDriver driver, WebElement element, ElementFingerprint fingerprint) {
        try {
            // Check if element is visible
            if (!element.isDisplayed()) {
                return;
            }

            // Take screenshot of page
            TakesScreenshot screenshotDriver = (TakesScreenshot) driver;
            byte[] screenshot = screenshotDriver.getScreenshotAs(OutputType.BYTES);

            // Get element location and size
            Point location = element.getLocation();
            int width = element.getSize().getWidth();
            int height = element.getSize().getHeight();

            // Crop screenshot to element
            BufferedImage fullImg = ImageIO.read(new ByteArrayInputStream(screenshot));
            if (location.getX() + width <= fullImg.getWidth() &&
                    location.getY() + height <= fullImg.getHeight()) {

                BufferedImage elementImg = fullImg.getSubimage(
                        location.getX(), location.getY(), width, height
                );

                // Generate simple image hash
                String imageHash = generateImageHash(elementImg);
                fingerprint.setVisualSignature(imageHash);
            }
        } catch (Exception e) {
            logger.warn("Error generating visual signature", e);
        }
    }

    /**
     * Generate a simple perceptual hash for an image
     *
     * @param image BufferedImage to hash
     * @return hash string
     */
    private String generateImageHash(BufferedImage image) throws IOException, NoSuchAlgorithmException {
        // Resize to a small fixed size
        int width = 8;
        int height = 8;

        // Create a grayscale image
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Sample from original image
                int sampleX = x * image.getWidth() / width;
                int sampleY = y * image.getHeight() / height;

                int rgb = image.getRGB(sampleX, sampleY);

                // Convert to grayscale
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (r + g + b) / 3;

                pixels[y * width + x] = gray;
            }
        }

        // Calculate average
        int sum = 0;
        for (int pixel : pixels) {
            sum += pixel;
        }
        int avg = sum / pixels.length;

        // Generate hash - each bit is 1 if pixel is above average
        StringBuilder hash = new StringBuilder();
        for (int pixel : pixels) {
            hash.append(pixel > avg ? "1" : "0");
        }

        return hash.toString();
    }

    /**
     * Find an element using its fingerprint
     *
     * @param driver WebDriver instance
     * @param fingerprint ElementFingerprint to match
     * @return WebElement instance or null if not found
     */
    public WebElement findElementByFingerprint(WebDriver driver, ElementFingerprint fingerprint) {
        // Try finding by original XPath
        if (fingerprint.getXpath() != null) {
            try {
                WebElement element = driver.findElement(By.xpath(fingerprint.getXpath()));

                // Validate it's the right element with high confidence
                ElementFingerprint currentFingerprint = createFingerprint(driver, fingerprint.getXpath());
                if (currentFingerprint != null && fingerprint.matches(currentFingerprint)) {
                    return element;
                }
            } catch (Exception e) {
                // Original XPath not found, continue with healing
                logger.debug("Original element XPath not found, trying healing strategies");
            }
        }

        // Try ID-based strategy
        if (fingerprint.getElementId() != null && !fingerprint.getElementId().isEmpty()) {
            try {
                WebElement element = driver.findElement(By.id(fingerprint.getElementId()));

                // Validate it's the right element
                ElementFingerprint currentFingerprint = createFingerprint(
                        driver, "//*[@id='" + fingerprint.getElementId() + "']"
                );
                if (currentFingerprint != null && fingerprint.matches(currentFingerprint)) {
                    return element;
                }
            } catch (Exception e) {
                // ID not found, continue with next strategy
            }
        }

        // Try searching by type and text content
        if (fingerprint.getElementType() != null && fingerprint.getElementText() != null
                && !fingerprint.getElementText().isEmpty()) {
            try {
                // Find all elements of this type
                for (WebElement element : driver.findElements(By.tagName(fingerprint.getElementType()))) {
                    String text = element.getText();
                    if (text != null && text.equals(fingerprint.getElementText())) {
                        // Create fingerprint for this candidate
                        String xpath = generateXPathForElement(driver, element);
                        ElementFingerprint currentFingerprint = createFingerprint(driver, xpath);

                        if (currentFingerprint != null && fingerprint.matches(currentFingerprint)) {
                            return element;
                        }
                    }
                }
            } catch (Exception e) {
                // Strategy failed, continue with next one
            }
        }

        // Try searching by attributes
        if (fingerprint.getAttributes() != null && !fingerprint.getAttributes().isEmpty()) {
            try {
                // Build a complex XPath with multiple attribute conditions
                StringBuilder xpathBuilder = new StringBuilder("//*[");
                boolean firstCondition = true;

                for (Map.Entry<String, String> entry : fingerprint.getAttributes().entrySet()) {
                    if (!firstCondition) {
                        xpathBuilder.append(" and ");
                    }
                    xpathBuilder.append("@")
                                .append(entry.getKey())
                                .append("='")
                                .append(entry.getValue().replace("'", "\\'"))
                                .append("'");
                    firstCondition = false;
                }

                xpathBuilder.append("]");
                String xpath = xpathBuilder.toString();

                // Try to find element with these attributes
                WebElement element = driver.findElement(By.xpath(xpath));

                // Validate it's the right element
                ElementFingerprint currentFingerprint = createFingerprint(driver, xpath);
                if (currentFingerprint != null && fingerprint.matches(currentFingerprint)) {
                    return element;
                }
            } catch (Exception e) {
                // Strategy failed, continue with next one
            }
        }

        // More advanced strategies could be implemented here

        // No matching element found
        return null;
    }

    /**
     * Generate an XPath for a WebElement
     *
     * @param driver WebDriver instance
     * @param element WebElement to generate XPath for
     * @return XPath string
     */
    private String generateXPathForElement(WebDriver driver, WebElement element) {
        // Use JavaScript to generate an XPath
        try {
            JavascriptExecutor executor = (JavascriptExecutor) driver;

            String script =
                    "function getPathTo(element) {" +
                            "   if (element.id !== '') {" +
                            "       return '//*[@id=\"' + element.id + '\"]';" +
                            "   }" +
                            "   if (element === document.body) {" +
                            "       return '/html/body';" +
                            "   }" +
                            "   var index = 1;" +
                            "   var siblings = element.parentNode.childNodes;" +
                            "   for (var i = 0; i < siblings.length; i++) {" +
                            "       var sibling = siblings[i];" +
                            "       if (sibling === element) {" +
                            "           return getPathTo(element.parentNode) + '/' + element.tagName.toLowerCase() + '[' + index + ']';" +
                            "       }" +
                            "       if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {" +
                            "           index++;" +
                            "       }" +
                            "   }" +
                            "}" +
                            "return getPathTo(arguments[0]);";

            return (String) executor.executeScript(script, element);
        } catch (Exception e) {
            logger.warn("Error generating XPath for element", e);
            return null;
        }
    }
}