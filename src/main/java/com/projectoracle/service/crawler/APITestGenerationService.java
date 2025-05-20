package com.projectoracle.service.crawler;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.projectoracle.model.Page;
import com.projectoracle.model.TestCase;
import com.projectoracle.service.AIModelService;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.repository.APIEndpointRepository;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for analyzing and generating tests for REST APIs.
 * Complements UI testing by identifying and testing backend APIs.
 * Part of the API Analysis System described in the roadmap.
 */
@Service
public class APITestGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(APITestGenerationService.class);

    @Autowired
    private AIModelService aiModelService;

    @Autowired
    private TestCaseRepository testCaseRepository;
    
    @Autowired
    private APIEndpointRepository apiEndpointRepository;

    /**
     * Represents a detected API endpoint
     */
    @Data
    public static class APIEndpoint {
        private String url;
        private String method;
        private Map<String, String> parameters;
        private Map<String, String> headers;
        private String requestBody;
        private String responseExample;
        private int statusCode;

        public APIEndpoint() {
            parameters = new HashMap<>();
            headers = new HashMap<>();
        }
    }

    /**
     * Extract API endpoints from page source code
     *
     * @param pages the list of pages from the crawl
     * @return list of detected API endpoints
     */
    public List<APIEndpoint> extractAPIEndpoints(List<Page> pages) {
        List<APIEndpoint> endpoints = new ArrayList<>();

        logger.info("Extracting API endpoints from {} pages", pages.size());

        for (Page page : pages) {
            // Execute a HEAD request to detect API endpoints
            if (isAPIEndpoint(page.getUrl())) {
                APIEndpoint endpoint = analyzeAPIEndpoint(page.getUrl());
                if (endpoint != null) {
                    endpoints.add(endpoint);
                }
            }

            // Extract API URLs from page's linked URLs
            if (page.getLinkedUrls() != null) {
                for (String url : page.getLinkedUrls()) {
                    if (looksLikeAPIUrl(url) && !endpointExists(endpoints, url)) {
                        APIEndpoint endpoint = analyzeAPIEndpoint(url);
                        if (endpoint != null) {
                            endpoints.add(endpoint);
                        }
                    }
                }
            }
        }

        // Persist discovered endpoints
        apiEndpointRepository.saveEndpoints(endpoints);

        logger.info("Extracted and persisted {} API endpoints", endpoints.size());
        return endpoints;
    }

    /**
     * Check if a URL looks like an API endpoint
     */
    private boolean looksLikeAPIUrl(String url) {
        // Common API patterns
        return url.contains("/api/") ||
                url.contains("/rest/") ||
                url.contains("/graphql") ||
                url.contains("/v1/") ||
                url.contains("/v2/");
    }

    /**
     * Check if the URL responds like an API endpoint
     */
    private boolean isAPIEndpoint(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            // Check response code
            int responseCode = connection.getResponseCode();

            // Check content type
            String contentType = connection.getContentType();
            if (contentType != null) {
                return contentType.contains("application/json") ||
                        contentType.contains("application/xml") ||
                        contentType.contains("application/graphql");
            }

            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Check if an endpoint with the same URL already exists in the list
     */
    private boolean endpointExists(List<APIEndpoint> endpoints, String url) {
        return endpoints.stream().anyMatch(e -> e.getUrl().equals(url));
    }

    /**
     * Analyze an API endpoint to gather more information
     */
    private APIEndpoint analyzeAPIEndpoint(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            APIEndpoint endpoint = new APIEndpoint();
            endpoint.setUrl(url);
            endpoint.setMethod("GET"); // Default method

            // Extract parameters from URL
            extractUrlParameters(url, endpoint);

            // Get response information
            int responseCode = connection.getResponseCode();
            endpoint.setStatusCode(responseCode);

            // Get response headers
            Map<String, List<String>> headers = connection.getHeaderFields();
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null) {
                    endpoint.getHeaders().put(entry.getKey(), String.join(", ", entry.getValue()));
                }
            }

            // Get content type
            String contentType = connection.getContentType();
            if (contentType != null) {
                endpoint.getHeaders().put("Content-Type", contentType);
            }

            // For successful responses, try to get response body
            if (responseCode >= 200 && responseCode < 300) {
                try (Scanner scanner = new Scanner(connection.getInputStream(), "UTF-8")) {
                    endpoint.setResponseExample(scanner.useDelimiter("\\A").next());
                } catch (Exception e) {
                    // Failed to read response body
                }
            }

            return endpoint;
        } catch (IOException e) {
            logger.warn("Failed to analyze API endpoint: {}", url, e);
            return null;
        }
    }

    /**
     * Extract parameters from a URL
     */
    private void extractUrlParameters(String url, APIEndpoint endpoint) {
        // Extract query parameters
        int queryIndex = url.indexOf('?');
        if (queryIndex > 0 && queryIndex < url.length() - 1) {
            String query = url.substring(queryIndex + 1);
            String[] pairs = query.split("&");

            for (String pair : pairs) {
                int idx = pair.indexOf('=');
                if (idx > 0) {
                    String key = pair.substring(0, idx);
                    String value = idx < pair.length() - 1 ? pair.substring(idx + 1) : "";
                    endpoint.getParameters().put(key, value);
                }
            }
        }

        // Look for path parameters using common patterns
        String path = url.indexOf('?') > 0 ? url.substring(0, url.indexOf('?')) : url;

        // This is a simple heuristic and might need refinement
        Pattern pattern = Pattern.compile("/([a-f0-9]{24}|\\d+)(?:/|$)");
        Matcher matcher = pattern.matcher(path);

        int index = 1;
        while (matcher.find()) {
            String value = matcher.group(1);
            // Check if this looks like an ID
            if (isNumeric(value) || value.matches("[a-f0-9]{24}")) {
                endpoint.getParameters().put("pathParam" + index++, value);
            }
        }
    }

    /**
     * Check if a string is numeric
     */
    private boolean isNumeric(String str) {
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Generate tests for API endpoints
     *
     * @param endpoints the list of API endpoints
     * @return list of generated test cases
     */
    public List<TestCase> generateAPITests(List<APIEndpoint> endpoints) {
        List<TestCase> testCases = new ArrayList<>();

        logger.info("Generating tests for {} API endpoints", endpoints.size());

        for (APIEndpoint endpoint : endpoints) {
            String testCode = generateAPITestCode(endpoint);

            TestCase testCase = TestCase.builder()
                                        .id(UUID.randomUUID())
                                        .name("Test_API_" + getEndpointName(endpoint))
                                        .description("Test for API endpoint: " + endpoint.getMethod() + " " + endpoint.getUrl())
                                        .type(TestCase.TestType.API)
                                        .priority(TestCase.TestPriority.HIGH)
                                        .status(TestCase.TestStatus.GENERATED)
                                        .packageName("com.projectoracle.test.api")
                                        .className("APITest_" + getEndpointName(endpoint))
                                        .methodName("test" + getEndpointName(endpoint))
                                        .sourceCode(testCode)
                                        .createdAt(LocalDateTime.now())
                                        .modifiedAt(LocalDateTime.now())
                                        .confidenceScore(0.9)
                                        .build();

            testCases.add(testCase);
            testCaseRepository.save(testCase);
        }

        logger.info("Generated {} API tests", testCases.size());
        return testCases;
    }
    
    /**
     * Get all API endpoints from repository
     * 
     * @return List of all stored API endpoints
     */
    public List<APIEndpoint> getAllAPIEndpoints() {
        return apiEndpointRepository.findAll();
    }
    
    /**
     * Find API endpoints by URL pattern
     * 
     * @param urlPattern Pattern to search for in URL
     * @return List of matching API endpoints
     */
    public List<APIEndpoint> findEndpointsByUrlPattern(String urlPattern) {
        return apiEndpointRepository.findByUrlPattern(urlPattern);
    }
    
    /**
     * Find API endpoints by HTTP method
     * 
     * @param method HTTP method to filter by
     * @return List of matching API endpoints
     */
    public List<APIEndpoint> findEndpointsByMethod(String method) {
        return apiEndpointRepository.findByMethod(method);
    }

    /**
     * Generate test code for an API endpoint
     */
    private String generateAPITestCode(APIEndpoint endpoint) {
        // Build a prompt for the AI model
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a JUnit 5 test for the following REST API endpoint:\n\n")
              .append("URL: ").append(endpoint.getUrl()).append("\n")
              .append("Method: ").append(endpoint.getMethod()).append("\n");

        if (!endpoint.getParameters().isEmpty()) {
            prompt.append("Parameters:\n");
            for (Map.Entry<String, String> param : endpoint.getParameters().entrySet()) {
                prompt.append("- ").append(param.getKey()).append(": ").append(param.getValue()).append("\n");
            }
        }

        if (!endpoint.getHeaders().isEmpty()) {
            prompt.append("Headers:\n");
            for (Map.Entry<String, String> header : endpoint.getHeaders().entrySet()) {
                prompt.append("- ").append(header.getKey()).append(": ").append(header.getValue()).append("\n");
            }
        }

        if (endpoint.getRequestBody() != null) {
            prompt.append("Request Body Example:\n").append(endpoint.getRequestBody()).append("\n\n");
        }

        if (endpoint.getResponseExample() != null) {
            prompt.append("Response Example:\n").append(endpoint.getResponseExample()).append("\n\n");
        }

        prompt.append("The test should:\n")
              .append("1. Make a HTTP request to the endpoint\n")
              .append("2. Validate the response status code\n")
              .append("3. Validate the response structure (headers, content type)\n")
              .append("4. Validate the response body content if applicable\n")
              .append("5. Include appropriate assertions\n\n")
              .append("Use RestAssured for making HTTP requests and JUnit 5 for assertions.\n");

        // Generate test code with AI
        return aiModelService.generateText(prompt.toString(), 1000);
    }

    /**
     * Get a sanitized endpoint name suitable for method/class names
     */
    private String getEndpointName(APIEndpoint endpoint) {
        String url = endpoint.getUrl();

        // Remove protocol and domain
        url = url.replaceAll("https?://[^/]+", "");

        // Remove query parameters
        int queryIndex = url.indexOf('?');
        if (queryIndex > 0) {
            url = url.substring(0, queryIndex);
        }

        // Split into segments and join with underscores
        String[] segments = url.split("/");
        StringBuilder nameBuilder = new StringBuilder();

        nameBuilder.append(endpoint.getMethod());

        for (String segment : segments) {
            // Skip empty segments and replace non-alphanumeric chars
            if (!segment.isEmpty()) {
                segment = segment.replaceAll("[^a-zA-Z0-9]", "");
                if (!segment.isEmpty()) {
                    nameBuilder.append("_").append(segment);
                }
            }
        }

        // Ensure valid Java identifier
        String name = nameBuilder.toString();
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            name = "Endpoint_" + name;
        }

        // Limit length
        if (name.length() > 30) {
            name = name.substring(0, 30);
        }

        return name;
    }
}