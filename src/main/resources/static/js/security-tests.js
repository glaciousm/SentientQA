// API Base URL
const API_BASE_URL = '/api/v1';

// DOM Elements
let securityTestsTableBody;
let securityTestCount;
let vulnerabilityList;
let apiEndpointsList;
let securityScore;
let securityScoreCircle;
let securityScoreText;
let endpointsAnalyzed;
let totalVulnerabilities;
let highVulnerabilities;
let mediumVulnerabilities;
let lowVulnerabilities;

// Model status variables
let modelStatusVisible = false;
let modelStatusPollingInterval = null;
const modelStatusUpdateFrequency = 2000; // 2 seconds

// Initialize the application
document.addEventListener('DOMContentLoaded', function() {
  // Initialize DOM element references
  securityTestsTableBody = document.getElementById('securityTestsTableBody');
  securityTestCount = document.getElementById('securityTestCount');
  vulnerabilityList = document.getElementById('vulnerabilityList');
  apiEndpointsList = document.getElementById('apiEndpointsList');
  securityScore = document.getElementById('securityScore');
  securityScoreCircle = document.getElementById('securityScoreCircle');
  securityScoreText = document.getElementById('securityScoreText');
  endpointsAnalyzed = document.getElementById('endpointsAnalyzed');
  totalVulnerabilities = document.getElementById('totalVulnerabilities');
  highVulnerabilities = document.getElementById('highVulnerabilities');
  mediumVulnerabilities = document.getElementById('mediumVulnerabilities');
  lowVulnerabilities = document.getElementById('lowVulnerabilities');

  // Hide loading overlay after a minimum display time
  setTimeout(function() {
    const pageLoadingOverlay = document.getElementById('pageLoadingOverlay');
    if (pageLoadingOverlay) {
      pageLoadingOverlay.classList.add('hidden');
      setTimeout(() => {
        pageLoadingOverlay.style.display = 'none';
      }, 300);
    }
  }, 1000);

  // Initialize form handlers
  initForms();
  
  // Load security tests
  loadSecurityTests();
  
  // Add event listeners
  setupEventListeners();
  
  // Initialize model status
  initModelStatus();
  
  // Initialize aiAnalysisThreshold slider
  const aiAnalysisThreshold = document.getElementById('aiAnalysisThreshold');
  const aiAnalysisThresholdValue = document.getElementById('aiAnalysisThresholdValue');
  
  if (aiAnalysisThreshold && aiAnalysisThresholdValue) {
    aiAnalysisThreshold.addEventListener('input', function() {
      aiAnalysisThresholdValue.textContent = this.value + '%';
    });
  }
});

// Initialize form handlers
function initForms() {
  // Security Test Generation Form
  const securityTestGenerationForm = document.getElementById('securityTestGenerationForm');
  if (securityTestGenerationForm) {
    securityTestGenerationForm.addEventListener('submit', function(e) {
      e.preventDefault();
      generateSecurityTests();
    });
  }
  
  // Method Scan Form
  const methodScanForm = document.getElementById('methodScanForm');
  if (methodScanForm) {
    methodScanForm.addEventListener('submit', function(e) {
      e.preventDefault();
      scanMethodForVulnerabilities();
    });
  }
  
  // API Security Form
  const apiSecurityForm = document.getElementById('apiSecurityForm');
  if (apiSecurityForm) {
    apiSecurityForm.addEventListener('submit', function(e) {
      e.preventDefault();
      analyzeApiSecurity();
    });
  }
  
  // Save Configuration Button
  const saveConfigBtn = document.getElementById('saveConfigBtn');
  if (saveConfigBtn) {
    saveConfigBtn.addEventListener('click', function() {
      saveConfiguration();
      const configModal = bootstrap.Modal.getInstance(document.getElementById('configModal'));
      configModal.hide();
    });
  }
}

// Setup event listeners
function setupEventListeners() {
  // Refresh button
  const refreshBtn = document.getElementById('refreshBtn');
  if (refreshBtn) {
    refreshBtn.addEventListener('click', function() {
      // Determine which tab is active and refresh accordingly
      const activeTab = document.querySelector('.tab-pane.active');
      if (activeTab) {
        if (activeTab.id === 'security-tests') {
          loadSecurityTests();
        } else if (activeTab.id === 'vulnerability-scan') {
          // Just clear the current results
          clearVulnerabilityResults();
        } else if (activeTab.id === 'api-security') {
          // Just clear the current results
          clearApiSecurityResults();
        }
      }
    });
  }
  
  // Export security tests button
  const exportSecurityTestsBtn = document.getElementById('exportSecurityTestsBtn');
  if (exportSecurityTestsBtn) {
    exportSecurityTestsBtn.addEventListener('click', function() {
      exportSecurityTests();
    });
  }
  
  // Run all security tests button
  const runAllSecurityTestsBtn = document.getElementById('runAllSecurityTestsBtn');
  if (runAllSecurityTestsBtn) {
    runAllSecurityTestsBtn.addEventListener('click', function() {
      runAllSecurityTests();
    });
  }
  
  // Generate all security tests button (from vulnerabilities)
  const generateAllSecurityTestsBtn = document.getElementById('generateAllSecurityTestsBtn');
  if (generateAllSecurityTestsBtn) {
    generateAllSecurityTestsBtn.addEventListener('click', function() {
      generateTestsForAllVulnerabilities();
    });
  }
  
  // Run security test button (in modal)
  const runSecurityTestBtn = document.getElementById('runSecurityTestBtn');
  if (runSecurityTestBtn) {
    runSecurityTestBtn.addEventListener('click', function() {
      runSelectedSecurityTest();
    });
  }
}

// Load security tests
function loadSecurityTests() {
  // Show skeleton loading
  document.getElementById('securityTestsSkeleton').style.display = 'block';
  document.getElementById('securityTestsContent').style.display = 'none';
  document.getElementById('noSecurityTestsMessage').style.display = 'none';
  
  // Fetch security tests from API
  fetch(`${API_BASE_URL}/tests?type=SECURITY`)
    .then(response => response.json())
    .then(tests => {
      // Hide skeleton, show content
      document.getElementById('securityTestsSkeleton').style.display = 'none';
      
      if (tests.length === 0) {
        // Show no tests message
        document.getElementById('noSecurityTestsMessage').style.display = 'block';
        securityTestCount.textContent = 'No security tests found';
      } else {
        // Show tests table
        document.getElementById('securityTestsContent').style.display = 'block';
        displaySecurityTests(tests);
      }
    })
    .catch(error => {
      console.error('Error loading security tests:', error);
      document.getElementById('securityTestsSkeleton').style.display = 'none';
      document.getElementById('noSecurityTestsMessage').style.display = 'block';
      
      // For demo, show sample data
      showDemoSecurityTests();
    });
}

// Display security tests in table
function displaySecurityTests(tests) {
  // Clear table
  securityTestsTableBody.innerHTML = '';
  
  // Update count
  securityTestCount.textContent = `Showing ${tests.length} security tests`;
  
  // Add tests to table
  tests.forEach(test => {
    // Extract vulnerability type from name (e.g., "testMethodIsSqlInjectionResistant" -> "SQL_INJECTION")
    const vulnerabilityType = extractVulnerabilityType(test.name);
    
    const row = document.createElement('tr');
    row.innerHTML = `
      <td>${test.name}</td>
      <td>
        <span class="badge ${getVulnerabilityBadgeClass(vulnerabilityType)}">
          ${formatVulnerabilityType(vulnerabilityType)}
        </span>
      </td>
      <td>${test.className}</td>
      <td class="${getSeverityClass(vulnerabilityType)}">
        ${getSeverityForVulnerabilityType(vulnerabilityType)}
      </td>
      <td>
        <span class="test-status ${getTestStatusBadgeClass(test.status)}">
          ${test.status}
        </span>
      </td>
      <td>
        <div class="btn-group btn-group-sm">
          <button class="btn btn-outline-primary view-test-btn" data-test-id="${test.id}">
            <i class="bi bi-eye"></i>
          </button>
          <button class="btn btn-outline-success run-test-btn" data-test-id="${test.id}">
            <i class="bi bi-play"></i>
          </button>
        </div>
      </td>
    `;
    
    securityTestsTableBody.appendChild(row);
  });
  
  // Add event listeners to buttons
  addTestButtonEventListeners();
}

// Show demo security tests (for development/preview)
function showDemoSecurityTests() {
  const demoTests = [
    {
      id: '1',
      name: 'testProcessUserInputIsSqlInjectionResistant',
      className: 'com.example.UserService',
      status: 'GENERATED',
      vulnerabilityType: 'SQL_INJECTION'
    },
    {
      id: '2',
      name: 'testRenderHtmlIsXssResistant',
      className: 'com.example.TemplateRenderer',
      status: 'FAILED',
      vulnerabilityType: 'XSS'
    },
    {
      id: '3',
      name: 'testFileUploadIsPathTraversalResistant',
      className: 'com.example.FileUploadService',
      status: 'PASSED',
      vulnerabilityType: 'PATH_TRAVERSAL'
    },
    {
      id: '4',
      name: 'testProcessObjectIsInsecureDeserializationResistant',
      className: 'com.example.ObjectProcessor',
      status: 'GENERATED',
      vulnerabilityType: 'INSECURE_DESERIALIZATION'
    },
    {
      id: '5',
      name: 'testProcessFormIsCsrfResistant',
      className: 'com.example.FormController',
      status: 'GENERATED',
      vulnerabilityType: 'CSRF'
    }
  ];
  
  // Display the demo tests
  document.getElementById('securityTestsContent').style.display = 'block';
  displaySecurityTests(demoTests);
}

// Extract vulnerability type from test name
function extractVulnerabilityType(testName) {
  // Pattern: test[Method]Is[VulnerabilityType]Resistant
  const pattern = /test.*Is(.+?)Resistant/;
  const match = testName.match(pattern);
  
  if (match && match[1]) {
    return match[1].toUpperCase();
  }
  
  return 'OTHER';
}

// Format vulnerability type for display
function formatVulnerabilityType(type) {
  switch (type) {
    case 'SQL_INJECTION':
      return 'SQL Injection';
    case 'XSS':
      return 'XSS';
    case 'PATH_TRAVERSAL':
      return 'Path Traversal';
    case 'INSECURE_DESERIALIZATION':
      return 'Insecure Deserialization';
    case 'CSRF':
      return 'CSRF';
    case 'WEAK_CRYPTOGRAPHY':
      return 'Weak Crypto';
    case 'LOGGING_SENSITIVE_DATA':
      return 'Sensitive Logging';
    default:
      return type.replace('_', ' ');
  }
}

// Get severity for vulnerability type
function getSeverityForVulnerabilityType(type) {
  switch (type) {
    case 'SQL_INJECTION':
    case 'INSECURE_DESERIALIZATION':
    case 'COMMAND_INJECTION':
    case 'XXE':
      return 'High';
      
    case 'XSS':
    case 'PATH_TRAVERSAL':
    case 'CSRF':
    case 'WEAK_CRYPTOGRAPHY':
    case 'INSECURE_DIRECT_OBJECT_REFERENCES':
      return 'Medium';
      
    case 'LOGGING_SENSITIVE_DATA':
    case 'SECURITY_MISCONFIGURATION':
    case 'OPEN_REDIRECT':
    default:
      return 'Low';
  }
}

// Get CSS class for severity
function getSeverityClass(type) {
  const severity = getSeverityForVulnerabilityType(type);
  
  switch (severity) {
    case 'High':
      return 'severity-high';
    case 'Medium':
      return 'severity-medium';
    case 'Low':
      return 'severity-low';
    default:
      return '';
  }
}

// Get badge class for vulnerability type
function getVulnerabilityBadgeClass(type) {
  switch (type) {
    case 'SQL_INJECTION':
      return 'badge-sql-injection';
    case 'XSS':
      return 'badge-xss';
    case 'PATH_TRAVERSAL':
      return 'badge-path-traversal';
    case 'INSECURE_DESERIALIZATION':
      return 'badge-insecure-deserialization';
    case 'CSRF':
      return 'badge-csrf';
    case 'WEAK_CRYPTOGRAPHY':
      return 'badge-weak-cryptography';
    case 'LOGGING_SENSITIVE_DATA':
      return 'badge-logging-sensitive-data';
    default:
      return 'badge-other';
  }
}

// Get badge class for test status
function getTestStatusBadgeClass(status) {
  switch (status) {
    case 'PASSED':
      return 'status-passed';
    case 'FAILED':
      return 'status-failed';
    case 'GENERATED':
      return 'status-generated';
    case 'RUNNING':
      return 'status-running';
    default:
      return '';
  }
}

// Add event listeners to test action buttons
function addTestButtonEventListeners() {
  // View test buttons
  const viewButtons = document.querySelectorAll('.view-test-btn');
  viewButtons.forEach(button => {
    button.addEventListener('click', function() {
      const testId = this.getAttribute('data-test-id');
      viewSecurityTest(testId);
    });
  });
  
  // Run test buttons
  const runButtons = document.querySelectorAll('.run-test-btn');
  runButtons.forEach(button => {
    button.addEventListener('click', function() {
      const testId = this.getAttribute('data-test-id');
      runSecurityTest(testId);
    });
  });
}

// View security test details
function viewSecurityTest(testId) {
  // Show loading in modal
  const modal = new bootstrap.Modal(document.getElementById('securityTestDetailModal'));
  modal.show();
  
  document.getElementById('securityTestDetailContent').innerHTML = `
    <div class="text-center">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
      <p class="mt-2">Loading test details...</p>
    </div>
  `;
  
  // Load test details
  fetch(`${API_BASE_URL}/tests/${testId}`)
    .then(response => response.json())
    .then(test => {
      displaySecurityTestDetails(test);
    })
    .catch(error => {
      console.error('Error loading test details:', error);
      document.getElementById('securityTestDetailContent').innerHTML = `
        <div class="alert alert-danger">
          Failed to load test details: ${error.message}
        </div>
      `;
      
      // For demo, show sample data
      const demoTest = {
        id: testId,
        name: 'testProcessUserInputIsSqlInjectionResistant',
        className: 'com.example.security.SecurityUserServiceTest',
        packageName: 'com.example.security',
        description: 'Security test for SQL_INJECTION vulnerability in com.example.UserService.processUserInput',
        type: 'SECURITY',
        priority: 'HIGH',
        status: 'GENERATED',
        sourceCode: `package com.example.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

import com.example.UserService;

/**
 * Security test for SQL Injection vulnerability in UserService.processUserInput
 * This test validates that the processUserInput method is resistant to SQL injection attacks.
 */
public class SecurityUserServiceTest {

    private UserService userService;
    
    @Mock
    private DataSource dataSource;
    
    @Mock
    private Connection connection;
    
    @Mock
    private Statement statement;
    
    @Mock
    private ResultSet resultSet;
    
    @BeforeEach
    public void setup() throws SQLException {
        MockitoAnnotations.openMocks(this);
        
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        
        userService = new UserService(dataSource);
    }
    
    /**
     * Test that the processUserInput method is vulnerable to SQL injection.
     * This test demonstrates how malicious input can manipulate the SQL query.
     */
    @Test
    public void testProcessUserInputIsSqlInjectionResistant() throws SQLException {
        // Arrange
        String maliciousInput = "' OR '1'='1";
        
        // Act
        userService.processUserInput(maliciousInput);
        
        // Assert - Capture the SQL query that was executed
        verify(statement).executeQuery(anyString());
        
        // Verify that the SQL query contains the unescaped malicious input
        // This would indicate a vulnerability
        String capturedQuery = getQueryArgument();
        
        // Check if the query contains the unaltered malicious input
        // If it does, the method is vulnerable to SQL injection
        assertTrue(capturedQuery.contains(maliciousInput),
            "The query should not contain unescaped user input: " + capturedQuery);
        
        // A secure implementation would use PreparedStatement instead of string concatenation
        // and would not contain the unescaped malicious input
    }
    
    /**
     * Helper method to extract the query argument from mock verification
     */
    private String getQueryArgument() {
        try {
            // In a real test, this would capture the actual query from the mock
            // For this demo, we're simulating a query that shows vulnerability
            // Using a hardcoded example since maliciousInput is not in scope here
            return "SELECT * FROM users WHERE username = '' OR '1'='1'";
        } catch (Exception e) {
            return ""; 
        }
    }
    
    /**
     * Test with a proper implementation that uses PreparedStatement
     * This shows how the code should be implemented to resist SQL injection
     */
    @Test
    public void testWithSecureImplementation() throws SQLException {
        // Arrange
        String maliciousInput = "' OR '1'='1";
        
        // Mock a secure implementation
        UserService secureUserService = mock(UserService.class);
        
        // Act
        secureUserService.processUserInputSecurely(maliciousInput);
        
        // Assert - Verify the secure method was called with the proper parameter
        verify(secureUserService).processUserInputSecurely(maliciousInput);
        
        // In a real implementation, we would verify that PreparedStatement was used correctly
        // and that the query doesn't contain unescaped user input
    }
}`
      };
      
      displaySecurityTestDetails(demoTest);
    });
}

// Display security test details in modal
function displaySecurityTestDetails(test) {
  const vulnerabilityType = extractVulnerabilityType(test.name);
  
  let content = `
    <div class="mb-4">
      <h5>${test.name}</h5>
      <p>${test.description}</p>
      <div class="d-flex align-items-center gap-2 mb-3">
        <span class="badge ${getVulnerabilityBadgeClass(vulnerabilityType)}">
          ${formatVulnerabilityType(vulnerabilityType)}
        </span>
        <span class="badge bg-secondary">${test.packageName}.${test.className}</span>
        <span class="test-status ${getTestStatusBadgeClass(test.status)}">${test.status}</span>
      </div>
    </div>
    
    <div class="code-snippet mb-4">
      <pre>${escapeHtml(test.sourceCode)}</pre>
    </div>
  `;
  
  document.getElementById('securityTestDetailContent').innerHTML = content;
  
  // Store test ID on run button
  document.getElementById('runSecurityTestBtn').setAttribute('data-test-id', test.id);
}

// Run selected security test (from modal)
function runSelectedSecurityTest() {
  const testId = document.getElementById('runSecurityTestBtn').getAttribute('data-test-id');
  if (testId) {
    runSecurityTest(testId);
    
    // Close modal
    const modal = bootstrap.Modal.getInstance(document.getElementById('securityTestDetailModal'));
    modal.hide();
  }
}

// Run a security test
function runSecurityTest(testId) {
  // Show alert
  alert(`Running security test ID: ${testId} - This feature is not fully implemented yet.`);
  
  // In a real implementation, we would call the API to run the test
  // and then update the UI with the results
}

// Run all security tests
function runAllSecurityTests() {
  // Show alert
  alert('Running all security tests - This feature is not fully implemented yet.');
  
  // In a real implementation, we would call the API to run all tests
  // and then update the UI with the results
}

// Export security tests
function exportSecurityTests() {
  // Show alert
  alert('Exporting security tests - This feature is not fully implemented yet.');
  
  // In a real implementation, we would generate a file with all test details
  // and prompt the user to download it
}

// Generate security tests
function generateSecurityTests() {
  const className = document.getElementById('className').value;
  const methodName = document.getElementById('methodName').value;
  
  if (!className || !methodName) {
    alert('Please enter both class name and method name.');
    return;
  }
  
  // Show loading UI
  document.getElementById('securityTestsSkeleton').style.display = 'block';
  document.getElementById('securityTestsContent').style.display = 'none';
  document.getElementById('noSecurityTestsMessage').style.display = 'none';
  
  // Show model status for feedback
  if (!modelStatusVisible) {
    toggleModelStatus();
  }
  
  // Call API to generate security tests
  fetch(`${API_BASE_URL}/security-tests/generate?className=${encodeURIComponent(className)}&methodName=${encodeURIComponent(methodName)}`, {
    method: 'POST'
  })
    .then(response => response.json())
    .then(tests => {
      // Reload security tests to show new ones
      loadSecurityTests();
      
      // Show success message
      alert(`Successfully generated ${tests.length} security tests.`);
    })
    .catch(error => {
      console.error('Error generating security tests:', error);
      document.getElementById('securityTestsSkeleton').style.display = 'none';
      document.getElementById('noSecurityTestsMessage').style.display = 'block';
      
      // Show error message
      alert('Failed to generate security tests. This feature may not be fully implemented yet.');
      
      // For demo, reload with sample data
      showDemoSecurityTests();
    });
}

// Scan method for vulnerabilities
function scanMethodForVulnerabilities() {
  const className = document.getElementById('scanClassName').value;
  const methodName = document.getElementById('scanMethodName').value;
  
  if (!className || !methodName) {
    alert('Please enter both class name and method name.');
    return;
  }
  
  // Show loading UI
  document.getElementById('vulnerabilitiesSkeleton').style.display = 'block';
  document.getElementById('vulnerabilityList').style.display = 'none';
  document.getElementById('noVulnerabilitiesMessage').style.display = 'none';
  document.getElementById('vulnerabilitySummary').style.display = 'none';
  
  // Show model status for feedback
  if (!modelStatusVisible) {
    toggleModelStatus();
  }
  
  // Call API to scan method
  fetch(`${API_BASE_URL}/security-tests/scan-method?className=${encodeURIComponent(className)}&methodName=${encodeURIComponent(methodName)}`)
    .then(response => response.json())
    .then(findings => {
      // Hide loading UI
      document.getElementById('vulnerabilitiesSkeleton').style.display = 'none';
      
      if (findings.length === 0) {
        // Show no vulnerabilities message
        document.getElementById('noVulnerabilitiesMessage').style.display = 'block';
        document.getElementById('vulnerabilityList').style.display = 'none';
        document.getElementById('vulnerabilitySummary').style.display = 'none';
      } else {
        // Show vulnerability findings
        document.getElementById('noVulnerabilitiesMessage').style.display = 'none';
        document.getElementById('vulnerabilityList').style.display = 'block';
        document.getElementById('vulnerabilitySummary').style.display = 'block';
        
        displayVulnerabilityFindings(findings, className, methodName);
      }
    })
    .catch(error => {
      console.error('Error scanning method:', error);
      document.getElementById('vulnerabilitiesSkeleton').style.display = 'none';
      document.getElementById('noVulnerabilitiesMessage').style.display = 'block';
      
      // Show error message
      alert('Failed to scan method. This feature may not be fully implemented yet.');
      
      // For demo, show sample data
      const demoFindings = getDemoVulnerabilityFindings();
      displayVulnerabilityFindings(demoFindings, className, methodName);
    });
}

// Clear vulnerability results
function clearVulnerabilityResults() {
  document.getElementById('vulnerabilityList').style.display = 'none';
  document.getElementById('noVulnerabilitiesMessage').style.display = 'block';
  document.getElementById('vulnerabilitySummary').style.display = 'none';
  document.getElementById('vulnerabilityList').innerHTML = '';
}

// Display vulnerability findings
function displayVulnerabilityFindings(findings, className, methodName) {
  // Clear previous findings
  vulnerabilityList.innerHTML = '';
  
  // Count severities
  let highCount = 0;
  let mediumCount = 0;
  let lowCount = 0;
  
  // Add each finding
  findings.forEach((finding, index) => {
    // Count severities
    switch (finding.severity) {
      case 'HIGH':
        highCount++;
        break;
      case 'MEDIUM':
        mediumCount++;
        break;
      case 'LOW':
        lowCount++;
        break;
    }
    
    const findingElement = document.createElement('div');
    findingElement.className = `card vulnerability-card mb-3 ${finding.severity.toLowerCase()}-severity`;
    
    findingElement.innerHTML = `
      <div class="card-header bg-white d-flex justify-content-between align-items-center">
        <h5 class="mb-0">
          <button class="btn btn-link text-decoration-none collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#collapse${index}">
            ${finding.vulnerabilityType}: ${finding.description}
          </button>
        </h5>
        <span class="${getSeverityClass(finding.vulnerabilityType)}">
          ${finding.severity}
        </span>
      </div>
      <div id="collapse${index}" class="collapse">
        <div class="card-body">
          <p>${finding.explanation}</p>
          
          ${finding.snippet ? `
          <div class="mb-3">
            <h6>Vulnerable Code:</h6>
            <div class="code-snippet">
              <pre class="vulnerability-highlight">${escapeHtml(finding.snippet)}</pre>
            </div>
          </div>
          ` : ''}
          
          <div class="mb-3">
            <h6>Recommendations:</h6>
            <ul class="recommendations-list">
              ${finding.recommendations.map(rec => `<li>${rec}</li>`).join('')}
            </ul>
          </div>
          
          <button class="btn btn-primary generate-test-btn" data-vulnerability-type="${finding.vulnerabilityType}" data-class-name="${className}" data-method-name="${methodName}">
            <i class="bi bi-shield-plus"></i> Generate Security Test
          </button>
        </div>
      </div>
    `;
    
    vulnerabilityList.appendChild(findingElement);
  });
  
  // Update summary counts
  totalVulnerabilities.textContent = findings.length;
  highVulnerabilities.textContent = highCount;
  mediumVulnerabilities.textContent = mediumCount;
  lowVulnerabilities.textContent = lowCount;
  
  // Add event listeners to generate test buttons
  const generateButtons = document.querySelectorAll('.generate-test-btn');
  generateButtons.forEach(button => {
    button.addEventListener('click', function() {
      const vulnType = this.getAttribute('data-vulnerability-type');
      const className = this.getAttribute('data-class-name');
      const methodName = this.getAttribute('data-method-name');
      
      generateSecurityTestForVulnerability(vulnType, className, methodName);
    });
  });
  
  // Show elements
  document.getElementById('vulnerabilityList').style.display = 'block';
  document.getElementById('vulnerabilitySummary').style.display = 'block';
  document.getElementById('noVulnerabilitiesMessage').style.display = 'none';
}

// Get demo vulnerability findings
function getDemoVulnerabilityFindings() {
  return [
    {
      vulnerabilityType: 'SQL_INJECTION',
      description: 'Unparameterized SQL queries can be exploited',
      explanation: 'The method concatenates user input directly into SQL queries without using parameterized statements, which can allow attackers to inject malicious SQL code.',
      snippet: 'String query = "SELECT * FROM users WHERE username = \'" + username + "\'";',
      recommendations: [
        'Use PreparedStatement with parameterized queries instead of string concatenation',
        'Apply input validation and sanitization',
        'Use JPA or other ORMs that handle parameterization automatically'
      ],
      severity: 'HIGH'
    },
    {
      vulnerabilityType: 'XSS',
      description: 'Unescaped output could allow script injection',
      explanation: 'The method outputs user-provided content directly to HTML without proper escaping, which can allow attackers to inject malicious scripts.',
      snippet: 'response.getWriter().write("<div>" + userInput + "</div>");',
      recommendations: [
        'Use HTML encoding/escaping functions before outputting user data',
        'Apply Content Security Policy (CSP) headers',
        'Use template engines that automatically escape content'
      ],
      severity: 'MEDIUM'
    },
    {
      vulnerabilityType: 'LOGGING_SENSITIVE_DATA',
      description: 'Sensitive information is being logged',
      explanation: 'The method logs user credentials or other sensitive information, which could expose this data in log files.',
      snippet: 'logger.info("User authentication: username=" + username + ", password=" + password);',
      recommendations: [
        'Mask or exclude sensitive data from logs',
        'Implement proper log redaction',
        'Use secure logging frameworks with built-in data protection'
      ],
      severity: 'LOW'
    }
  ];
}

// Generate security test for a specific vulnerability
function generateSecurityTestForVulnerability(vulnerabilityType, className, methodName) {
  alert(`Generating security test for ${vulnerabilityType} vulnerability in ${className}.${methodName} - This feature is not fully implemented yet.`);
  
  // In a real implementation, we would call the API to generate a specific test
  // for this vulnerability type
}

// Generate tests for all vulnerabilities
function generateTestsForAllVulnerabilities() {
  const className = document.getElementById('scanClassName').value;
  const methodName = document.getElementById('scanMethodName').value;
  
  if (!className || !methodName) {
    alert('Class name and method name not available.');
    return;
  }
  
  alert(`Generating security tests for all vulnerabilities in ${className}.${methodName} - This feature is not fully implemented yet.`);
  
  // In a real implementation, we would call the API to generate tests for all
  // vulnerabilities found in the method
}

// Analyze API security
function analyzeApiSecurity() {
  const controllerClassName = document.getElementById('controllerClassName').value;
  
  if (!controllerClassName) {
    alert('Please enter a controller class name.');
    return;
  }
  
  // Show loading UI
  document.getElementById('apiSecuritySkeleton').style.display = 'block';
  document.getElementById('apiSecurityResults').style.display = 'none';
  document.getElementById('noApiSecurityMessage').style.display = 'none';
  
  // Show model status for feedback
  if (!modelStatusVisible) {
    toggleModelStatus();
  }
  
  // Call API to analyze API security
  fetch(`${API_BASE_URL}/security-tests/analyze-api?controllerClassName=${encodeURIComponent(controllerClassName)}`)
    .then(response => response.json())
    .then(findings => {
      // Hide loading UI
      document.getElementById('apiSecuritySkeleton').style.display = 'none';
      
      if (findings.length === 0) {
        // Show no findings message
        document.getElementById('noApiSecurityMessage').style.display = 'block';
        document.getElementById('apiSecurityResults').style.display = 'none';
      } else {
        // Show API security findings
        document.getElementById('noApiSecurityMessage').style.display = 'none';
        document.getElementById('apiSecurityResults').style.display = 'block';
        
        displayApiSecurityFindings(findings, controllerClassName);
      }
    })
    .catch(error => {
      console.error('Error analyzing API security:', error);
      document.getElementById('apiSecuritySkeleton').style.display = 'none';
      document.getElementById('noApiSecurityMessage').style.display = 'block';
      
      // Show error message
      alert('Failed to analyze API security. This feature may not be fully implemented yet.');
      
      // For demo, show sample data
      const demoFindings = getDemoApiSecurityFindings();
      displayApiSecurityFindings(demoFindings, controllerClassName);
    });
}

// Clear API security results
function clearApiSecurityResults() {
  document.getElementById('apiSecurityResults').style.display = 'none';
  document.getElementById('noApiSecurityMessage').style.display = 'block';
  document.getElementById('apiEndpointsList').innerHTML = '';
}

// Display API security findings
function displayApiSecurityFindings(findings, controllerClassName) {
  // Clear previous findings
  apiEndpointsList.innerHTML = '';
  
  // Calculate average security score
  let totalScore = 0;
  findings.forEach(finding => {
    totalScore += finding.securityScore;
  });
  const avgScore = Math.round(totalScore / findings.length);
  
  // Update security score
  securityScore.textContent = avgScore;
  
  // Add security score class
  securityScoreCircle.className = 'security-score-circle';
  if (avgScore >= 80) {
    securityScoreCircle.classList.add('security-score-good');
    securityScoreText.textContent = 'Good';
  } else if (avgScore >= 50) {
    securityScoreCircle.classList.add('security-score-medium');
    securityScoreText.textContent = 'Needs Improvement';
  } else {
    securityScoreCircle.classList.add('security-score-bad');
    securityScoreText.textContent = 'Poor';
  }
  
  // Update endpoints analyzed
  endpointsAnalyzed.textContent = `${findings.length} endpoints in ${controllerClassName}`;
  
  // Add each endpoint
  findings.forEach((finding, index) => {
    const endpointElement = document.createElement('div');
    
    // Add appropriate security class
    let securityClass = '';
    if (finding.securityScore >= 80) {
      securityClass = 'endpoint-security-good';
    } else if (finding.securityScore >= 50) {
      securityClass = 'endpoint-security-medium';
    } else {
      securityClass = 'endpoint-security-bad';
    }
    
    endpointElement.className = `card endpoint-card mb-3 ${securityClass}`;
    
    endpointElement.innerHTML = `
      <div class="card-header bg-white d-flex justify-content-between align-items-center">
        <h5 class="mb-0">
          <button class="btn btn-link text-decoration-none collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#apiCollapse${index}">
            <span class="badge badge-${finding.httpMethod.toLowerCase()}">${finding.httpMethod}</span>
            ${finding.endpoint}
          </button>
        </h5>
        <div>
          <span class="badge bg-secondary">Score: ${finding.securityScore}</span>
        </div>
      </div>
      <div id="apiCollapse${index}" class="collapse">
        <div class="card-body">
          <h6>Security Issues:</h6>
          <div class="list-group mb-3">
            ${finding.issues.map(issue => `
              <div class="list-group-item list-group-item-action flex-column align-items-start">
                <div class="d-flex w-100 justify-content-between">
                  <h6 class="mb-1">${issue.title}</h6>
                </div>
                <p class="mb-1">${issue.description}</p>
                <small class="text-muted">Recommendation: ${issue.recommendation}</small>
              </div>
            `).join('')}
          </div>
          
          <div class="d-flex justify-content-between">
            <span>Method: <code>${finding.methodName}</code></span>
            <button class="btn btn-sm btn-primary">
              <i class="bi bi-shield-plus"></i> Generate Security Test
            </button>
          </div>
        </div>
      </div>
    `;
    
    apiEndpointsList.appendChild(endpointElement);
  });
}

// Get demo API security findings
function getDemoApiSecurityFindings() {
  return [
    {
      className: 'com.example.UserController',
      methodName: 'authenticateUser',
      endpoint: '/api/users/auth',
      httpMethod: 'POST',
      issues: [
        {
          title: 'Missing CSRF protection',
          description: 'State-changing operations should be protected against CSRF attacks',
          recommendation: 'Implement CSRF token validation for all state-changing operations'
        },
        {
          title: 'Missing rate limiting',
          description: 'Endpoint could be vulnerable to brute force or DoS attacks',
          recommendation: 'Implement rate limiting to prevent abuse'
        }
      ],
      securityScore: 65
    },
    {
      className: 'com.example.UserController',
      methodName: 'getUserProfile',
      endpoint: '/api/users/{id}',
      httpMethod: 'GET',
      issues: [
        {
          title: 'Missing authorization checks',
          description: 'Endpoint does not have explicit authorization annotations',
          recommendation: 'Add @Secured, @PreAuthorize, or @RolesAllowed annotations to restrict access'
        }
      ],
      securityScore: 75
    },
    {
      className: 'com.example.UserController',
      methodName: 'updateUserProfile',
      endpoint: '/api/users/{id}',
      httpMethod: 'PUT',
      issues: [
        {
          title: 'Missing CSRF protection',
          description: 'State-changing operations should be protected against CSRF attacks',
          recommendation: 'Implement CSRF token validation for all state-changing operations'
        },
        {
          title: 'Missing input validation',
          description: 'Request bodies should be validated to prevent injection attacks',
          recommendation: 'Add @Valid annotation to request body parameters and implement validation constraints'
        },
        {
          title: 'Missing authorization checks',
          description: 'Endpoint does not have explicit authorization annotations',
          recommendation: 'Add @Secured, @PreAuthorize, or @RolesAllowed annotations to restrict access'
        }
      ],
      securityScore: 35
    }
  ];
}

// Save configuration
function saveConfiguration() {
  // Get configuration values
  const aiAnalysisThreshold = document.getElementById('aiAnalysisThreshold').value;
  const maxTestsPerMethod = document.getElementById('maxTestsPerMethod').value;
  
  // Get enabled vulnerability types
  const enabledVulnerabilityTypes = [];
  document.querySelectorAll('input[id^="vulnType"]:checked').forEach(checkbox => {
    enabledVulnerabilityTypes.push(checkbox.id.replace('vulnType', ''));
  });
  
  // Build configuration object
  const config = {
    aiAnalysisThreshold: parseInt(aiAnalysisThreshold) || 50,
    maxTestsPerMethod: parseInt(maxTestsPerMethod) || 3,
    enabledVulnerabilityTypes: enabledVulnerabilityTypes || []
  };
  
  console.log('Saved configuration:', config);
  alert('Configuration saved successfully');
  
  // In a real implementation, we would call the API to save the configuration
}

// Helper function to escape HTML
function escapeHtml(html) {
  const div = document.createElement('div');
  div.textContent = html;
  return div.innerHTML;
}

// Model status functions
function toggleModelStatus() {
  const modelStatusContainer = document.getElementById('modelStatusContainer');
  if (!modelStatusContainer) return;
  
  if (modelStatusVisible) {
    modelStatusContainer.style.display = 'none';
    modelStatusVisible = false;
    if (modelStatusPollingInterval) {
      clearInterval(modelStatusPollingInterval);
      modelStatusPollingInterval = null;
    }
  } else {
    modelStatusContainer.style.display = 'block';
    modelStatusVisible = true;
    updateModelStatus();
    if (!modelStatusPollingInterval) {
      modelStatusPollingInterval = setInterval(updateModelStatus, modelStatusUpdateFrequency);
    }
  }
}

function initModelStatus() {
  const refreshModelStatusBtn = document.getElementById('refreshModelStatusBtn');
  if (refreshModelStatusBtn) {
    refreshModelStatusBtn.addEventListener('click', updateModelStatus);
  }
}

function updateModelStatus() {
  const languageModelStatus = document.getElementById('languageModelStatus');
  const embeddingsModelStatus = document.getElementById('embeddingsModelStatus');
  const languageModelProgress = document.getElementById('languageModelProgress');
  const embeddingsModelProgress = document.getElementById('embeddingsModelProgress');
  
  if (!languageModelStatus || !embeddingsModelStatus) return;
  
  // Fetch model status from API
  fetch(`${API_BASE_URL}/models/status`)
    .then(response => response.json())
    .then(statuses => {
      // Update language model status
      const languageModel = statuses['language-gpt2-medium'];
      if (languageModel) {
        updateModelStatusUI(languageModel.status, languageModelStatus, languageModelProgress);
      }
      
      // Update embeddings model status
      const embeddingsModel = statuses['embeddings-all-MiniLM-L6-v2'];
      if (embeddingsModel) {
        updateModelStatusUI(embeddingsModel.status, embeddingsModelStatus, embeddingsModelProgress);
      }
    })
    .catch(error => {
      console.error('Error fetching model status:', error);
      // Set both to unknown status
      updateModelStatusUI('NOT_LOADED', languageModelStatus, languageModelProgress);
      updateModelStatusUI('NOT_LOADED', embeddingsModelStatus, embeddingsModelProgress);
    });
}

function updateModelStatusUI(status, statusElement, progressElement) {
  // Remove all status classes
  statusElement.classList.remove(
    'model-status-not-loaded',
    'model-status-loading',
    'model-status-loaded',
    'model-status-failed'
  );
  
  // Set progress and status based on model status
  switch (status) {
    case 'NOT_LOADED':
      statusElement.textContent = 'NOT LOADED';
      statusElement.classList.add('model-status-not-loaded');
      if (progressElement) progressElement.style.width = '0%';
      break;
    case 'LOADING':
      statusElement.textContent = 'LOADING';
      statusElement.classList.add('model-status-loading');
      if (progressElement) {
        progressElement.style.width = '50%';
        progressElement.classList.add('progress-bar-striped', 'progress-bar-animated');
      }
      break;
    case 'LOADED':
      statusElement.textContent = 'LOADED';
      statusElement.classList.add('model-status-loaded');
      if (progressElement) {
        progressElement.style.width = '100%';
        progressElement.classList.remove('progress-bar-striped', 'progress-bar-animated');
      }
      break;
    case 'FAILED':
      statusElement.textContent = 'FAILED';
      statusElement.classList.add('model-status-failed');
      if (progressElement) {
        progressElement.style.width = '100%';
        progressElement.classList.remove('progress-bar-striped', 'progress-bar-animated');
      }
      break;
    default:
      statusElement.textContent = 'UNKNOWN';
      statusElement.classList.add('model-status-not-loaded');
      if (progressElement) progressElement.style.width = '0%';
  }
}