// API Base URL
const API_BASE_URL = 'http://localhost:8080/api/v1';

// DOM Elements
let complianceScore;
let complianceScoreCircle;
let complianceStandard;
let criticalCount;
let highCount;
let mediumCount;
let lowCount;
let violationsList;
let complianceByStandard;
let complianceTestsList;

// Chart objects
let complianceRadarChart;

// Model status variables
let modelStatusVisible = false;
let modelStatusPollingInterval = null;
const modelStatusUpdateFrequency = 2000; // 2 seconds

// Initialize the application
document.addEventListener('DOMContentLoaded', function() {
  // Initialize DOM element references
  complianceScore = document.getElementById('complianceScore');
  complianceScoreCircle = document.getElementById('complianceScoreCircle');
  complianceStandard = document.getElementById('complianceStandard');
  criticalCount = document.getElementById('criticalCount');
  highCount = document.getElementById('highCount');
  mediumCount = document.getElementById('mediumCount');
  lowCount = document.getElementById('lowCount');
  violationsList = document.getElementById('violationsList');
  complianceByStandard = document.getElementById('complianceByStandard');
  complianceTestsList = document.getElementById('complianceTestsList');

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
  
  // Add event listeners
  setupEventListeners();
  
  // Initialize model status
  initModelStatus();
});

// Initialize form handlers
function initForms() {
  // Compliance Check Form
  const complianceCheckForm = document.getElementById('complianceCheckForm');
  if (complianceCheckForm) {
    complianceCheckForm.addEventListener('submit', function(e) {
      e.preventDefault();
      checkCompliance();
    });
  }
  
  // Compliance Audit Form
  const complianceAuditForm = document.getElementById('complianceAuditForm');
  if (complianceAuditForm) {
    complianceAuditForm.addEventListener('submit', function(e) {
      e.preventDefault();
      runComplianceAudit();
    });
  }
  
  // Generate Tests Form
  const generateTestsForm = document.getElementById('generateTestsForm');
  if (generateTestsForm) {
    generateTestsForm.addEventListener('submit', function(e) {
      e.preventDefault();
      generateComplianceTests();
    });
  }
  
  // Bulk Testing Form
  const bulkTestingForm = document.getElementById('bulkTestingForm');
  if (bulkTestingForm) {
    bulkTestingForm.addEventListener('submit', function(e) {
      e.preventDefault();
      runBulkOperation();
    });
    
    // Setup bulk testing form interactions
    setupBulkTestingForm();
  }
}

// Setup interactions for bulk testing form
function setupBulkTestingForm() {
  const bulkTestType = document.getElementById('bulkTestType');
  const bulkStandardSelector = document.getElementById('bulkStandardSelector');
  const bulkStandardsSelector = document.getElementById('bulkStandardsSelector');
  
  if (bulkTestType) {
    bulkTestType.addEventListener('change', function() {
      if (this.value === 'standard') {
        bulkStandardSelector.style.display = 'block';
        bulkStandardsSelector.style.display = 'none';
      } else { // audit
        bulkStandardSelector.style.display = 'none';
        bulkStandardsSelector.style.display = 'block';
      }
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
        if (activeTab.id === 'compliance-check') {
          clearComplianceReport();
        } else if (activeTab.id === 'compliance-audit') {
          clearAuditResults();
        } else if (activeTab.id === 'compliance-tests') {
          clearGeneratedTests();
        }
      }
    });
  }
  
  // Export report button
  const exportReportBtn = document.getElementById('exportReportBtn');
  if (exportReportBtn) {
    exportReportBtn.addEventListener('click', function() {
      exportComplianceReport();
    });
  }
  
  // Generate compliance tests button (from violations report)
  const generateComplianceTestsBtn = document.getElementById('generateComplianceTestsBtn');
  if (generateComplianceTestsBtn) {
    generateComplianceTestsBtn.addEventListener('click', function() {
      generateTestsFromReport();
    });
  }
  
  // Export audit button
  const exportAuditBtn = document.getElementById('exportAuditBtn');
  if (exportAuditBtn) {
    exportAuditBtn.addEventListener('click', function() {
      exportAuditReport();
    });
  }
  
  // Export tests button
  const exportTestsBtn = document.getElementById('exportTestsBtn');
  if (exportTestsBtn) {
    exportTestsBtn.addEventListener('click', function() {
      exportComplianceTests();
    });
  }
  
  // Run all tests button
  const runAllTestsBtn = document.getElementById('runAllTestsBtn');
  if (runAllTestsBtn) {
    runAllTestsBtn.addEventListener('click', function() {
      runAllComplianceTests();
    });
  }
}

// Run compliance check
function checkCompliance() {
  const className = document.getElementById('className').value;
  const standard = document.getElementById('standard').value;
  
  if (!className || !standard) {
    alert('Please enter both class name and standard.');
    return;
  }
  
  // Show loading UI
  document.getElementById('noReportMessage').style.display = 'none';
  document.getElementById('reportResults').style.display = 'none';
  document.getElementById('complianceReportSkeleton').style.display = 'block';
  
  // Show model status for feedback
  if (!modelStatusVisible) {
    toggleModelStatus();
  }
  
  // Enable buttons
  document.getElementById('exportReportBtn').disabled = false;
  document.getElementById('generateComplianceTestsBtn').disabled = false;
  
  // Call API to run compliance check
  fetch(`${API_BASE_URL}/compliance-tests/test?className=${encodeURIComponent(className)}&standard=${encodeURIComponent(standard)}`)
    .then(response => response.json())
    .then(report => {
      // Hide skeleton, show results
      document.getElementById('complianceReportSkeleton').style.display = 'none';
      document.getElementById('reportResults').style.display = 'block';
      
      displayComplianceReport(report);
    })
    .catch(error => {
      console.error('Error checking compliance:', error);
      document.getElementById('complianceReportSkeleton').style.display = 'none';
      document.getElementById('noReportMessage').style.display = 'block';
      
      // Show error message
      alert('Failed to check compliance. This feature may not be fully implemented yet.');
      
      // For demo, show sample data
      displayComplianceReport(getDemoComplianceReport(className, standard));
    });
}

// Clear compliance report
function clearComplianceReport() {
  document.getElementById('reportResults').style.display = 'none';
  document.getElementById('noReportMessage').style.display = 'block';
  document.getElementById('violationsList').innerHTML = '';
  document.getElementById('exportReportBtn').disabled = true;
  document.getElementById('generateComplianceTestsBtn').disabled = true;
}

// Display compliance report
function displayComplianceReport(report) {
  // Update compliance score
  complianceScore.textContent = `${report.compliancePercentage}%`;
  
  // Update score circle color based on percentage
  complianceScoreCircle.className = 'compliance-score';
  if (report.compliancePercentage >= 90) {
    complianceScoreCircle.classList.add('score-excellent');
  } else if (report.compliancePercentage >= 80) {
    complianceScoreCircle.classList.add('score-good');
  } else if (report.compliancePercentage >= 70) {
    complianceScoreCircle.classList.add('score-fair');
  } else if (report.compliancePercentage >= 50) {
    complianceScoreCircle.classList.add('score-poor');
  } else {
    complianceScoreCircle.classList.add('score-critical');
  }
  
  // Update standard name
  complianceStandard.textContent = `Standard: ${formatStandardName(report.standard)}`;
  
  // Count violations by severity
  let criticalViolations = 0;
  let highViolations = 0;
  let mediumViolations = 0;
  let lowViolations = 0;
  
  report.violations.forEach(violation => {
    switch (violation.severity) {
      case 'CRITICAL':
        criticalViolations++;
        break;
      case 'HIGH':
        highViolations++;
        break;
      case 'MEDIUM':
        mediumViolations++;
        break;
      case 'LOW':
      case 'INFORMATIONAL':
        lowViolations++;
        break;
    }
  });
  
  // Update count badges
  criticalCount.textContent = criticalViolations;
  highCount.textContent = highViolations;
  mediumCount.textContent = mediumViolations;
  lowCount.textContent = lowViolations;
  
  // Clear previous violations
  violationsList.innerHTML = '';
  
  // Add violations
  if (report.violations.length === 0) {
    violationsList.innerHTML = `
      <div class="alert alert-success">
        <i class="bi bi-check-circle"></i> No compliance violations found!
      </div>
    `;
  } else {
    // Sort violations by severity (most severe first)
    const sortedViolations = [...report.violations].sort((a, b) => {
      const severityOrder = { 'CRITICAL': 0, 'HIGH': 1, 'MEDIUM': 2, 'LOW': 3, 'INFORMATIONAL': 4 };
      return severityOrder[a.severity] - severityOrder[b.severity];
    });
    
    // Add each violation
    sortedViolations.forEach((violation, index) => {
      const violationItem = document.createElement('div');
      violationItem.className = `accordion-item violation-card severity-${violation.severity}`;
      
      violationItem.innerHTML = `
        <h2 class="accordion-header" id="violationHeading${index}">
          <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#violationCollapse${index}" aria-expanded="false" aria-controls="violationCollapse${index}">
            <div class="d-flex align-items-center w-100">
              <div class="violation-badge ${violation.severity.toLowerCase()} me-3">
                <i class="bi bi-exclamation"></i>
              </div>
              <div class="flex-grow-1">
                <div class="d-flex justify-content-between align-items-center">
                  <strong>${violation.ruleName}</strong>
                  <span class="badge bg-${getSeverityColorClass(violation.severity)}">${violation.severity}</span>
                </div>
                <small class="text-muted">${violation.methodName} in ${violation.className}</small>
              </div>
            </div>
          </button>
        </h2>
        <div id="violationCollapse${index}" class="accordion-collapse collapse" aria-labelledby="violationHeading${index}" data-bs-parent="#violationsList">
          <div class="accordion-body">
            <p>${violation.description}</p>
            ${violation.detectionPattern ? `
              <div class="mb-3">
                <h6>Detection Pattern:</h6>
                <code>${violation.detectionPattern}</code>
              </div>
            ` : ''}
            ${violation.aiAnalysis ? `
              <div class="ai-analysis">
                <h6><i class="bi bi-robot"></i> AI Analysis:</h6>
                <p>${violation.aiAnalysis}</p>
              </div>
            ` : ''}
            <div class="remediation-steps">
              <h6><i class="bi bi-wrench"></i> Remediation Steps:</h6>
              <p>${violation.remediationSteps}</p>
            </div>
            <div class="mt-3">
              <button class="btn btn-sm btn-primary generate-test-btn" data-rule-id="${violation.ruleId}" data-method-name="${violation.methodName}" data-class-name="${violation.className}">
                <i class="bi bi-shield-plus"></i> Generate Test for this Violation
              </button>
            </div>
          </div>
        </div>
      `;
      
      violationsList.appendChild(violationItem);
    });
  }
  
  // Add event listeners to generate test buttons
  const generateTestBtns = document.querySelectorAll('.generate-test-btn');
  generateTestBtns.forEach(btn => {
    btn.addEventListener('click', function() {
      const ruleId = this.getAttribute('data-rule-id');
      const methodName = this.getAttribute('data-method-name');
      const className = this.getAttribute('data-class-name');
      
      generateTestForViolation(ruleId, className, methodName, report.standard);
    });
  });
}

// Get demo compliance report
function getDemoComplianceReport(className, standard) {
  return {
    className: className,
    standard: standard,
    compliancePercentage: 75.5,
    testedAt: new Date().toISOString(),
    violations: [
      {
        ruleId: 'OWASP-A1-001',
        ruleName: 'SQL Injection Prevention',
        severity: 'CRITICAL',
        className: className,
        methodName: 'processUserInput',
        description: 'SQL injection occurs when untrusted data is sent to an interpreter as part of a command or query',
        detectionPattern: '.*Statement.*execute.*\\+.*',
        remediationSteps: 'Use parameterized queries or prepared statements instead of string concatenation',
        aiAnalysis: 'The method concatenates user input directly into an SQL query without sanitization, creating a severe SQL injection vulnerability. Consider using PreparedStatement with bound parameters.'
      },
      {
        ruleId: 'OWASP-A7-001',
        ruleName: 'Cross-Site Scripting Prevention',
        severity: 'HIGH',
        className: className,
        methodName: 'renderUserProfile',
        description: 'Applications must properly escape or sanitize user input to prevent XSS attacks',
        detectionPattern: '.*\\.html\\(.*',
        remediationSteps: 'Use context-aware escaping libraries for output encoding, implement Content Security Policy'
      },
      {
        ruleId: 'OWASP-A3-001',
        ruleName: 'Sensitive Data Encryption',
        severity: 'MEDIUM',
        className: className,
        methodName: 'storeUserProfile',
        description: 'Sensitive data must be encrypted in transit and at rest',
        detectionPattern: null,
        remediationSteps: 'Use strong encryption algorithms, secure protocols (TLS 1.2+), and implement proper key management',
        aiAnalysis: 'The method stores user profile data in plaintext, including potentially sensitive information. Implement field-level encryption for sensitive data.'
      }
    ]
  };
}

// Run compliance audit
function runComplianceAudit() {
  const className = document.getElementById('auditClassName').value;
  
  if (!className) {
    alert('Please enter a class name.');
    return;
  }
  
  // Get selected standards
  const selectedStandards = Array.from(document.querySelectorAll('input[type=checkbox][id^="standard"]:checked'))
    .map(cb => cb.value);
  
  if (selectedStandards.length === 0) {
    alert('Please select at least one compliance standard.');
    return;
  }
  
  // Show loading UI
  document.getElementById('noAuditMessage').style.display = 'none';
  document.getElementById('auditResults').style.display = 'none';
  document.getElementById('auditResultsSkeleton').style.display = 'block';
  
  // Show model status for feedback
  if (!modelStatusVisible) {
    toggleModelStatus();
  }
  
  // Enable export button
  document.getElementById('exportAuditBtn').disabled = false;
  
  // Call API to run compliance audit
  const queryParams = `className=${encodeURIComponent(className)}&${selectedStandards.map(s => `standards=${encodeURIComponent(s)}`).join('&')}`;
  
  fetch(`${API_BASE_URL}/compliance-tests/audit?${queryParams}`)
    .then(response => response.json())
    .then(reports => {
      // Hide skeleton, show results
      document.getElementById('auditResultsSkeleton').style.display = 'none';
      document.getElementById('auditResults').style.display = 'block';
      
      displayAuditResults(reports, className);
    })
    .catch(error => {
      console.error('Error running compliance audit:', error);
      document.getElementById('auditResultsSkeleton').style.display = 'none';
      document.getElementById('noAuditMessage').style.display = 'block';
      
      // Show error message
      alert('Failed to run compliance audit. This feature may not be fully implemented yet.');
      
      // For demo, show sample data
      const demoReports = {};
      selectedStandards.forEach(standard => {
        demoReports[standard] = getDemoComplianceReport(className, standard);
      });
      displayAuditResults(demoReports, className);
    });
}

// Clear audit results
function clearAuditResults() {
  document.getElementById('auditResults').style.display = 'none';
  document.getElementById('noAuditMessage').style.display = 'block';
  document.getElementById('complianceByStandard').innerHTML = '';
  document.getElementById('exportAuditBtn').disabled = true;
  
  // Destroy chart if it exists
  if (complianceRadarChart) {
    complianceRadarChart.destroy();
    complianceRadarChart = null;
  }
}

// Display audit results
function displayAuditResults(reports, className) {
  // Update audit summary
  const standardCount = Object.keys(reports).length;
  document.getElementById('auditSummary').textContent = 
    `Audit completed for ${className} against ${standardCount} compliance standards`;
  
  // Create radar chart data
  const standardLabels = [];
  const complianceScores = [];
  const chartColors = [];
  
  // Process each standard's report
  for (const [standard, report] of Object.entries(reports)) {
    standardLabels.push(formatStandardName(standard));
    complianceScores.push(report.compliancePercentage);
    chartColors.push(getStandardColor(standard));
  }
  
  // Create radar chart
  createComplianceRadarChart(standardLabels, complianceScores, chartColors);
  
  // Clear compliance by standard accordion
  complianceByStandard.innerHTML = '';
  
  // Add each standard's report
  Object.entries(reports).forEach(([standard, report], index) => {
    const standardCard = document.createElement('div');
    standardCard.className = `accordion-item standard-card standard-${standard}`;
    
    standardCard.innerHTML = `
      <h2 class="accordion-header" id="standardHeading${index}">
        <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#standardCollapse${index}" aria-expanded="false" aria-controls="standardCollapse${index}">
          <div class="d-flex align-items-center w-100">
            <div class="flex-grow-1">
              <div class="d-flex justify-content-between align-items-center">
                <span class="rule-badge ${getStandardClass(standard)}">${formatStandardName(standard)}</span>
                <strong class="${getComplianceScoreClass(report.compliancePercentage)}">${report.compliancePercentage}% Compliant</strong>
              </div>
              <small class="text-muted">${report.violations.length} violations found</small>
            </div>
          </div>
        </button>
      </h2>
      <div id="standardCollapse${index}" class="accordion-collapse collapse" aria-labelledby="standardHeading${index}" data-bs-parent="#complianceByStandard">
        <div class="accordion-body">
          ${report.violations.length === 0 ? `
            <div class="alert alert-success">
              <i class="bi bi-check-circle"></i> Fully compliant with ${formatStandardName(standard)} standard!
            </div>
          ` : `
            <h6>Violations:</h6>
            <ul class="list-group mb-3">
              ${report.violations.map(violation => `
                <li class="list-group-item d-flex justify-content-between align-items-center">
                  <div>
                    <strong>${violation.ruleName}</strong>
                    <div class="text-muted small">${violation.methodName} in ${violation.className}</div>
                  </div>
                  <span class="badge bg-${getSeverityColorClass(violation.severity)}">${violation.severity}</span>
                </li>
              `).join('')}
            </ul>
          `}
          <div class="d-grid">
            <button class="btn btn-primary generate-tests-for-standard-btn" data-standard="${standard}" data-class-name="${className}">
              <i class="bi bi-shield-plus"></i> Generate ${formatStandardName(standard)} Tests
            </button>
          </div>
        </div>
      </div>
    `;
    
    complianceByStandard.appendChild(standardCard);
  });
  
  // Add event listeners to generate test buttons
  const generateTestsForStandardBtns = document.querySelectorAll('.generate-tests-for-standard-btn');
  generateTestsForStandardBtns.forEach(btn => {
    btn.addEventListener('click', function() {
      const standard = this.getAttribute('data-standard');
      const className = this.getAttribute('data-class-name');
      
      generateComplianceTestsForStandard(className, standard);
    });
  });
}

// Create compliance radar chart
function createComplianceRadarChart(labels, data, backgroundColors) {
  // Destroy existing chart if it exists
  if (complianceRadarChart) {
    complianceRadarChart.destroy();
  }
  
  // Get canvas context
  const ctx = document.getElementById('complianceRadarChart').getContext('2d');
  
  // Create border colors (darker versions of background colors)
  const borderColors = backgroundColors.map(color => color);
  
  // Create chart
  complianceRadarChart = new Chart(ctx, {
    type: 'radar',
    data: {
      labels: labels,
      datasets: [{
        label: 'Compliance Score (%)',
        data: data,
        backgroundColor: backgroundColors.map(color => `${color}3A`), // Add transparency
        borderColor: borderColors,
        borderWidth: 2,
        pointBackgroundColor: borderColors,
        pointBorderColor: '#fff',
        pointHoverBackgroundColor: '#fff',
        pointHoverBorderColor: borderColors
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      elements: {
        line: {
          tension: 0.1
        }
      },
      scales: {
        r: {
          angleLines: {
            display: true
          },
          suggestedMin: 0,
          suggestedMax: 100
        }
      },
      plugins: {
        legend: {
          display: false
        },
        tooltip: {
          callbacks: {
            label: function(context) {
              return `${context.dataset.label}: ${context.raw}%`;
            }
          }
        }
      }
    }
  });
}

// Generate compliance tests
function generateComplianceTests() {
  const className = document.getElementById('testClassName').value;
  const standard = document.getElementById('testStandard').value;
  
  if (!className || !standard) {
    alert('Please enter both class name and standard.');
    return;
  }
  
  // Show loading UI
  document.getElementById('noTestsMessage').style.display = 'none';
  document.getElementById('testsList').style.display = 'none';
  document.getElementById('generatedTestsSkeleton').style.display = 'block';
  
  // Show model status for feedback
  if (!modelStatusVisible) {
    toggleModelStatus();
  }
  
  // Call API to generate compliance tests
  fetch(`${API_BASE_URL}/compliance-tests/generate?className=${encodeURIComponent(className)}&standard=${encodeURIComponent(standard)}`, {
    method: 'POST'
  })
    .then(response => response.json())
    .then(tests => {
      // Hide skeleton, show tests
      document.getElementById('generatedTestsSkeleton').style.display = 'none';
      document.getElementById('testsList').style.display = 'block';
      
      // Enable buttons
      document.getElementById('exportTestsBtn').disabled = false;
      document.getElementById('runAllTestsBtn').disabled = false;
      
      displayComplianceTests(tests);
    })
    .catch(error => {
      console.error('Error generating compliance tests:', error);
      document.getElementById('generatedTestsSkeleton').style.display = 'none';
      document.getElementById('noTestsMessage').style.display = 'block';
      
      // Show error message
      alert('Failed to generate compliance tests. This feature may not be fully implemented yet.');
      
      // For demo, show sample data
      displayComplianceTests(getDemoComplianceTests(className, standard));
    });
}

// Clear generated tests
function clearGeneratedTests() {
  document.getElementById('testsList').style.display = 'none';
  document.getElementById('noTestsMessage').style.display = 'block';
  document.getElementById('complianceTestsList').innerHTML = '';
  document.getElementById('exportTestsBtn').disabled = true;
  document.getElementById('runAllTestsBtn').disabled = true;
}

// Display compliance tests
function displayComplianceTests(tests) {
  // Clear previous tests
  complianceTestsList.innerHTML = '';
  
  // Check if we have tests
  if (tests.length === 0) {
    complianceTestsList.innerHTML = `
      <div class="alert alert-info mx-3 my-3">
        <i class="bi bi-info-circle"></i> No compliance tests were generated. Try a different class or standard.
      </div>
    `;
    return;
  }
  
  // Add each test
  tests.forEach(test => {
    // Extract standard from description
    const standardMatch = test.description.match(/against\s+(\w+)(?:_TOP_10)?\s+standard/);
    const standard = standardMatch ? standardMatch[1] : 'UNKNOWN';
    
    const testItem = document.createElement('div');
    testItem.className = `compliance-test-item list-group-item list-group-item-action standard-${standard}`;
    
    testItem.innerHTML = `
      <div class="d-flex w-100 justify-content-between">
        <h6 class="mb-1">${test.name}</h6>
        <span class="test-status ${getTestStatusBadgeClass(test.status)}">${test.status}</span>
      </div>
      <p class="mb-1 text-muted small">${test.className}</p>
      <div class="d-flex justify-content-between align-items-center">
        <small class="text-muted">${formatStandardName(standard)} compliance test</small>
        <div class="btn-group btn-group-sm">
          <button class="btn btn-outline-primary view-test-btn" data-test-id="${test.id}">
            <i class="bi bi-eye"></i>
          </button>
          <button class="btn btn-outline-success run-test-btn" data-test-id="${test.id}">
            <i class="bi bi-play"></i>
          </button>
        </div>
      </div>
    `;
    
    complianceTestsList.appendChild(testItem);
  });
  
  // Add event listeners to buttons
  addTestButtonEventListeners();
}

// Get demo compliance tests
function getDemoComplianceTests(className, standard) {
  return [
    {
      id: '1',
      name: 'testComplianceProcessUserInputForOWASP_TOP_10',
      className: 'compliance.' + className + 'Test',
      packageName: 'com.example.compliance',
      description: 'Compliance test for ' + className + '.processUserInput against OWASP_TOP_10 standard',
      type: 'SECURITY',
      status: 'GENERATED',
      methodName: 'testComplianceProcessUserInputForOWASP_TOP_10'
    },
    {
      id: '2',
      name: 'testComplianceRenderUserProfileForOWASP_TOP_10',
      className: 'compliance.' + className + 'Test',
      packageName: 'com.example.compliance',
      description: 'Compliance test for ' + className + '.renderUserProfile against OWASP_TOP_10 standard',
      type: 'SECURITY',
      status: 'GENERATED',
      methodName: 'testComplianceRenderUserProfileForOWASP_TOP_10'
    },
    {
      id: '3',
      name: 'testComplianceStoreUserProfileForOWASP_TOP_10',
      className: 'compliance.' + className + 'Test',
      packageName: 'com.example.compliance',
      description: 'Compliance test for ' + className + '.storeUserProfile against OWASP_TOP_10 standard',
      type: 'SECURITY',
      status: 'GENERATED',
      methodName: 'testComplianceStoreUserProfileForOWASP_TOP_10'
    }
  ];
}

// Add event listeners to test buttons
function addTestButtonEventListeners() {
  // View test buttons
  const viewButtons = document.querySelectorAll('.view-test-btn');
  viewButtons.forEach(button => {
    button.addEventListener('click', function() {
      const testId = this.getAttribute('data-test-id');
      viewTestDetails(testId);
    });
  });
  
  // Run test buttons
  const runButtons = document.querySelectorAll('.run-test-btn');
  runButtons.forEach(button => {
    button.addEventListener('click', function() {
      const testId = this.getAttribute('data-test-id');
      runComplianceTest(testId);
    });
  });
}

// View test details
function viewTestDetails(testId) {
  // Show modal
  const modal = new bootstrap.Modal(document.getElementById('testDetailsModal'));
  modal.show();
  
  // Show loading
  document.getElementById('testDetailsContent').innerHTML = `
    <div class="text-center">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
      <p class="mt-2">Loading test details...</p>
    </div>
  `;
  
  // Get test details from API
  fetch(`${API_BASE_URL}/tests/${testId}`)
    .then(response => response.json())
    .then(test => {
      displayTestDetails(test);
    })
    .catch(error => {
      console.error('Error loading test details:', error);
      document.getElementById('testDetailsContent').innerHTML = `
        <div class="alert alert-danger">
          Failed to load test details: ${error.message}
        </div>
      `;
      
      // For demo, show a sample
      const demoTest = {
        id: testId,
        name: 'testComplianceProcessUserInputForOWASP_TOP_10',
        className: 'compliance.UserServiceTest',
        description: 'Compliance test for UserService.processUserInput against OWASP_TOP_10 standard',
        type: 'SECURITY',
        status: 'GENERATED',
        sourceCode: `package com.example.compliance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.example.UserService;

/**
 * Compliance test for UserService.processUserInput against OWASP Top 10 standard.
 * This test specifically focuses on A1:2017 - Injection vulnerability.
 */
public class ComplianceUserServiceTest {

    private UserService userService;
    
    @Mock
    private DataSource dataSource;
    
    @Mock
    private Connection connection;
    
    @Mock
    private Statement statement;
    
    @Mock
    private PreparedStatement preparedStatement;
    
    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        
        userService = new UserService(dataSource);
    }
    
    @Test
    @DisplayName("SQL Injection Compliance: UserService.processUserInput should use parameterized queries")
    public void testComplianceProcessUserInputForOWASP_TOP_10() throws Exception {
        // Arrange
        String maliciousInput = "' OR '1'='1"; // Typical SQL injection attack
        
        // Act
        userService.processUserInput(maliciousInput);
        
        // Assert
        // Verify that createStatement (vulnerable) is NOT used
        verify(connection, never()).createStatement();
        
        // Verify that prepareStatement (secure) IS used
        verify(connection, atLeastOnce()).prepareStatement(anyString());
        
        // Verify that the parameter is set properly in the prepared statement
        verify(preparedStatement, atLeastOnce()).setString(anyInt(), eq(maliciousInput));
    }
    
    @Test
    @DisplayName("SQL Injection Compliance: Negative test to demonstrate vulnerability")
    public void testDemonstrateVulnerableImplementation() throws Exception {
        // This test demonstrates how a vulnerable implementation would behave
        // In an actual codebase, this would fail if the code is secure
        
        // Arrange
        String maliciousInput = "' OR '1'='1"; // Typical SQL injection attack
        UserService vulnerableService = new UserService(dataSource) {
            @Override
            protected void executeQuery(String input) throws Exception {
                // Simulate vulnerable implementation for demonstration
                String query = "SELECT * FROM users WHERE username = '" + input + "'";
                connection.createStatement().executeQuery(query);
            }
        };
        
        // Act
        vulnerableService.processUserInput(maliciousInput);
        
        // Assert
        // In a vulnerable implementation, createStatement would be used with string concatenation
        verify(connection).createStatement();
        
        // The statement would be executed with a query containing the unescaped malicious input
        verify(statement).executeQuery(contains(maliciousInput));
    }
}`
      };
      
      displayTestDetails(demoTest);
    });
}

// Display test details
function displayTestDetails(test) {
  // Extract standard from description
  const standardMatch = test.description.match(/against\s+(\w+)(?:_TOP_10)?\s+standard/);
  const standard = standardMatch ? standardMatch[1] : 'UNKNOWN';
  
  // Create test details content
  let content = `
    <h5>${test.name}</h5>
    <p class="text-muted">${test.description}</p>
    
    <div class="mb-3 d-flex align-items-center">
      <span class="rule-badge ${getStandardClass(standard)}">${formatStandardName(standard)}</span>
      <span class="ms-2 test-status ${getTestStatusBadgeClass(test.status)}">${test.status}</span>
    </div>
    
    <h6>Test Code:</h6>
    <div class="code-block mb-3">
      <pre>${escapeHtml(test.sourceCode)}</pre>
    </div>
  `;
  
  document.getElementById('testDetailsContent').innerHTML = content;
  
  // Store test ID on run button
  document.getElementById('runTestBtn').setAttribute('data-test-id', test.id);
}

// Run compliance test
function runComplianceTest(testId) {
  // Show loading state
  const runButton = document.getElementById('runTestBtn');
  runButton.disabled = true;
  runButton.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Running...';
  
  // Show test details content as loading
  document.getElementById('testDetailsContent').innerHTML = `
    <div class="text-center">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
      <p class="mt-2">Executing test...</p>
    </div>
  `;
  
  // Execute the test
  fetch(`${API_BASE_URL}/compliance-tests/execute/${testId}`, {
    method: 'POST'
  })
    .then(response => response.json())
    .then(updatedTest => {
      // Refresh test details with results
      displayTestExecutionResults(updatedTest);
      
      // Re-enable run button
      runButton.disabled = false;
      runButton.innerHTML = 'Run Test';
      
      // Refresh the tests list if it's visible
      if (document.getElementById('testsList').style.display !== 'none') {
        refreshComplianceTestsList();
      }
    })
    .catch(error => {
      console.error('Error executing test:', error);
      
      // Show error message
      document.getElementById('testDetailsContent').innerHTML = `
        <div class="alert alert-danger">
          <i class="bi bi-exclamation-triangle"></i> Failed to execute test: ${error.message || 'Unknown error'}
        </div>
        <div class="mt-3">
          <button class="btn btn-outline-secondary" onclick="viewTestDetails('${testId}')">
            <i class="bi bi-arrow-repeat"></i> Refresh Test Details
          </button>
        </div>
      `;
      
      // Re-enable run button
      runButton.disabled = false;
      runButton.innerHTML = 'Run Test';
    });
}

// Run all compliance tests
function runAllComplianceTests() {
  // Get all test IDs from the list
  const testItems = document.querySelectorAll('.compliance-test-item');
  if (testItems.length === 0) {
    alert('No tests available to run.');
    return;
  }
  
  // Extract test IDs
  const testIds = Array.from(testItems).map(item => {
    const viewButton = item.querySelector('.view-test-btn');
    return viewButton.getAttribute('data-test-id');
  });
  
  // Show loading UI
  const runAllButton = document.getElementById('runAllTestsBtn');
  runAllButton.disabled = true;
  runAllButton.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Running All...';
  
  // Add loading indicators to all test items
  testItems.forEach(item => {
    const statusBadge = item.querySelector('.test-status');
    statusBadge.className = 'test-status status-running';
    statusBadge.textContent = 'RUNNING';
  });
  
  // Execute tests in batch
  fetch(`${API_BASE_URL}/compliance-tests/execute-batch`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(testIds)
  })
    .then(response => response.json())
    .then(results => {
      // Process results
      refreshComplianceTestsList();
      
      // Re-enable run button
      runAllButton.disabled = false;
      runAllButton.innerHTML = '<i class="bi bi-play"></i> Run All';
      
      // Show summary alert
      const totalTests = Object.keys(results).length;
      const passedTests = Object.values(results).filter(test => test.status === 'PASSED').length;
      
      const alertClass = passedTests === totalTests ? 'alert-success' : 'alert-warning';
      const alertIcon = passedTests === totalTests ? 'check-circle' : 'exclamation-triangle';
      
      const summaryAlert = document.createElement('div');
      summaryAlert.className = `alert ${alertClass} alert-dismissible fade show mt-3`;
      summaryAlert.innerHTML = `
        <i class="bi bi-${alertIcon}"></i> Execution complete: ${passedTests} of ${totalTests} tests passed.
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
      `;
      
      document.getElementById('complianceTestsList').insertAdjacentElement('beforebegin', summaryAlert);
    })
    .catch(error => {
      console.error('Error executing test batch:', error);
      
      // Show error message
      const errorAlert = document.createElement('div');
      errorAlert.className = 'alert alert-danger alert-dismissible fade show mt-3';
      errorAlert.innerHTML = `
        <i class="bi bi-exclamation-triangle"></i> Failed to execute tests: ${error.message || 'Unknown error'}
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
      `;
      
      document.getElementById('complianceTestsList').insertAdjacentElement('beforebegin', errorAlert);
      
      // Re-enable run button
      runAllButton.disabled = false;
      runAllButton.innerHTML = '<i class="bi bi-play"></i> Run All';
      
      // Refresh the list to reset status indicators
      refreshComplianceTestsList();
    });
}

// Display test execution results
function displayTestExecutionResults(test) {
  // Extract standard from description
  const standardMatch = test.description.match(/against\s+(\w+)(?:_TOP_10)?\s+standard/);
  const standard = standardMatch ? standardMatch[1] : 'UNKNOWN';
  
  // Create test details content
  let content = `
    <h5>${test.name}</h5>
    <p class="text-muted">${test.description}</p>
    
    <div class="mb-3 d-flex align-items-center">
      <span class="rule-badge ${getStandardClass(standard)}">${formatStandardName(standard)}</span>
      <span class="ms-2 test-status ${getTestStatusBadgeClass(test.status)}">${test.status}</span>
      ${test.lastExecuted ? `<span class="ms-2 text-muted">Last executed: ${new Date(test.lastExecuted).toLocaleString()}</span>` : ''}
      ${test.executionTime ? `<span class="ms-2 text-muted">Execution time: ${test.executionTime}ms</span>` : ''}
    </div>
  `;
  
  // Add execution results if available
  if (test.status === 'PASSED' || test.status === 'FAILED') {
    content += `
      <div class="alert ${test.status === 'PASSED' ? 'alert-success' : 'alert-danger'} mb-3">
        <i class="bi bi-${test.status === 'PASSED' ? 'check-circle' : 'exclamation-triangle'}"></i> 
        ${test.status === 'PASSED' ? 'Test passed successfully!' : 'Test failed!'}
        ${test.errorMessage ? `<div class="mt-2"><strong>Error:</strong> ${test.errorMessage}</div>` : ''}
      </div>
    `;
    
    // Add execution output if available
    if (test.executionOutput) {
      content += `
        <h6>Execution Output:</h6>
        <div class="code-block mb-3" style="max-height: 300px; overflow-y: auto;">
          <pre>${escapeHtml(test.executionOutput)}</pre>
        </div>
      `;
    }
  }
  
  // Add test code
  content += `
    <h6>Test Code:</h6>
    <div class="code-block mb-3">
      <pre>${escapeHtml(test.sourceCode)}</pre>
    </div>
  `;
  
  document.getElementById('testDetailsContent').innerHTML = content;
  
  // Store test ID on run button
  document.getElementById('runTestBtn').setAttribute('data-test-id', test.id);
}

// Refresh compliance tests list
function refreshComplianceTestsList() {
  const className = document.getElementById('testClassName').value;
  const standard = document.getElementById('testStandard').value;
  
  if (!className || !standard) {
    return;
  }
  
  // Show loading UI
  document.getElementById('generatedTestsSkeleton').style.display = 'block';
  document.getElementById('testsList').style.display = 'none';
  
  // Fetch latest tests
  fetch(`${API_BASE_URL}/tests?className=${encodeURIComponent(className)}&description=${encodeURIComponent(standard)}`)
    .then(response => response.json())
    .then(tests => {
      // Hide skeleton, show tests
      document.getElementById('generatedTestsSkeleton').style.display = 'none';
      document.getElementById('testsList').style.display = 'block';
      
      // Display tests
      displayComplianceTests(tests);
    })
    .catch(error => {
      console.error('Error refreshing compliance tests list:', error);
      document.getElementById('generatedTestsSkeleton').style.display = 'none';
      
      // For demo, use existing tests if available
      if (document.getElementById('testsList').style.display !== 'none') {
        document.getElementById('testsList').style.display = 'block';
      } else {
        document.getElementById('noTestsMessage').style.display = 'block';
      }
    });
}

// Export compliance report
function exportComplianceReport() {
  const className = document.getElementById('className').value;
  const standard = document.getElementById('standard').value;
  
  if (!className || !standard) {
    alert('Please run a compliance check first.');
    return;
  }
  
  // Show loading indicator
  const exportBtn = document.getElementById('exportReportBtn');
  const originalText = exportBtn.innerHTML;
  exportBtn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Generating PDF...';
  exportBtn.disabled = true;
  
  // Create URL for report download
  const reportUrl = `${API_BASE_URL}/compliance-tests/report?className=${encodeURIComponent(className)}&standard=${encodeURIComponent(standard)}`;
  
  // Create hidden link and trigger download
  const link = document.createElement('a');
  link.href = reportUrl;
  link.target = '_blank';
  link.download = `compliance_report_${className.replace(/[^a-zA-Z0-9]/g, '_')}_${standard}.pdf`;
  
  // Append link, click it, and remove it
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  
  // Restore button after a delay
  setTimeout(() => {
    exportBtn.innerHTML = originalText;
    exportBtn.disabled = false;
  }, 2000);
}

// Export audit report
function exportAuditReport() {
  const className = document.getElementById('auditClassName').value;
  
  if (!className) {
    alert('Please run a compliance audit first.');
    return;
  }
  
  // Get selected standards
  const selectedStandards = Array.from(document.querySelectorAll('input[type=checkbox][id^="standard"]:checked'))
    .map(cb => cb.value);
  
  if (selectedStandards.length === 0) {
    alert('Please select at least one compliance standard.');
    return;
  }
  
  // Show loading indicator
  const exportBtn = document.getElementById('exportAuditBtn');
  const originalText = exportBtn.innerHTML;
  exportBtn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Generating PDF...';
  exportBtn.disabled = true;
  
  // Create URL for report download with selected standards
  let reportUrl = `${API_BASE_URL}/compliance-tests/audit-report?className=${encodeURIComponent(className)}`;
  selectedStandards.forEach(standard => {
    reportUrl += `&standards=${encodeURIComponent(standard)}`;
  });
  
  // Create hidden link and trigger download
  const link = document.createElement('a');
  link.href = reportUrl;
  link.target = '_blank';
  link.download = `compliance_audit_report_${className.replace(/[^a-zA-Z0-9]/g, '_')}.pdf`;
  
  // Append link, click it, and remove it
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  
  // Restore button after a delay
  setTimeout(() => {
    exportBtn.innerHTML = originalText;
    exportBtn.disabled = false;
  }, 2000);
}

// Export compliance tests
function exportComplianceTests() {
  alert('Exporting compliance tests - This feature is not fully implemented yet.');
}

// Run bulk operation
function runBulkOperation() {
  // Parse classes from text area (one per line)
  const classNamesText = document.getElementById('bulkClassNames').value;
  const classNames = classNamesText.split('\n')
    .map(name => name.trim())
    .filter(name => name.length > 0);
  
  if (classNames.length === 0) {
    alert('Please enter at least one class name.');
    return;
  }
  
  // Get operation parameters
  const testType = document.getElementById('bulkTestType').value;
  const action = document.getElementById('bulkAction').value;
  
  // Show loading UI
  document.getElementById('noBulkResultsMessage').style.display = 'none';
  document.getElementById('bulkResultsList').style.display = 'none';
  document.getElementById('bulkResultsSkeleton').style.display = 'block';
  
  // Enable export button
  document.getElementById('exportBulkResultsBtn').disabled = false;
  
  // Execute appropriate operation based on selections
  if (testType === 'standard') {
    const standard = document.getElementById('bulkStandard').value;
    
    if (action === 'test') {
      // Run bulk compliance test
      runBulkComplianceTest(classNames, standard);
    } else { // generate
      // Generate bulk compliance tests
      generateBulkComplianceTests(classNames, standard);
    }
  } else { // audit
    // Get selected standards
    const selectedStandards = Array.from(document.querySelectorAll('input[type=checkbox][id^="bulkStandard"]:checked'))
      .map(cb => cb.value);
    
    if (selectedStandards.length === 0) {
      alert('Please select at least one compliance standard.');
      document.getElementById('bulkResultsSkeleton').style.display = 'none';
      document.getElementById('noBulkResultsMessage').style.display = 'block';
      return;
    }
    
    // Run bulk compliance audit
    runBulkComplianceAudit(classNames, selectedStandards);
  }
}

// Run bulk compliance test
function runBulkComplianceTest(classNames, standard) {
  fetch(`${API_BASE_URL}/compliance-tests/bulk-test?standard=${encodeURIComponent(standard)}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(classNames)
  })
    .then(response => response.json())
    .then(results => {
      // Hide skeleton
      document.getElementById('bulkResultsSkeleton').style.display = 'none';
      document.getElementById('bulkResultsList').style.display = 'block';
      
      // Display results
      displayBulkTestResults(results, standard);
    })
    .catch(error => {
      console.error('Error running bulk compliance test:', error);
      document.getElementById('bulkResultsSkeleton').style.display = 'none';
      document.getElementById('noBulkResultsMessage').style.display = 'block';
      
      // Show error message
      alert('Failed to run bulk compliance test. This feature may not be fully implemented yet.');
      
      // For demo, show sample data
      const demoResults = {};
      classNames.forEach(className => {
        demoResults[className] = getDemoComplianceReport(className, standard);
      });
      displayBulkTestResults(demoResults, standard);
    });
}

// Run bulk compliance audit
function runBulkComplianceAudit(classNames, standards) {
  // Create URL with standards parameters
  let url = `${API_BASE_URL}/compliance-tests/bulk-audit?`;
  standards.forEach(standard => {
    url += `&standards=${encodeURIComponent(standard)}`;
  });
  
  fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(classNames)
  })
    .then(response => response.json())
    .then(results => {
      // Hide skeleton
      document.getElementById('bulkResultsSkeleton').style.display = 'none';
      document.getElementById('bulkResultsList').style.display = 'block';
      
      // Display results
      displayBulkAuditResults(results);
    })
    .catch(error => {
      console.error('Error running bulk compliance audit:', error);
      document.getElementById('bulkResultsSkeleton').style.display = 'none';
      document.getElementById('noBulkResultsMessage').style.display = 'block';
      
      // Show error message
      alert('Failed to run bulk compliance audit. This feature may not be fully implemented yet.');
      
      // For demo, show sample data
      const demoResults = {};
      classNames.forEach(className => {
        const standardReports = {};
        standards.forEach(standard => {
          standardReports[standard] = getDemoComplianceReport(className, standard);
        });
        demoResults[className] = standardReports;
      });
      displayBulkAuditResults(demoResults);
    });
}

// Generate bulk compliance tests
function generateBulkComplianceTests(classNames, standard) {
  fetch(`${API_BASE_URL}/compliance-tests/bulk-generate?standard=${encodeURIComponent(standard)}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(classNames)
  })
    .then(response => response.json())
    .then(results => {
      // Hide skeleton
      document.getElementById('bulkResultsSkeleton').style.display = 'none';
      document.getElementById('bulkResultsList').style.display = 'block';
      
      // Display results
      displayBulkGenerateResults(results, standard);
    })
    .catch(error => {
      console.error('Error generating bulk compliance tests:', error);
      document.getElementById('bulkResultsSkeleton').style.display = 'none';
      document.getElementById('noBulkResultsMessage').style.display = 'block';
      
      // Show error message
      alert('Failed to generate bulk compliance tests. This feature may not be fully implemented yet.');
      
      // For demo, show sample data
      const demoResults = {};
      classNames.forEach(className => {
        demoResults[className] = getDemoComplianceTests(className, standard);
      });
      displayBulkGenerateResults(demoResults, standard);
    });
}

// Display bulk test results
function displayBulkTestResults(results, standard) {
  const bulkResultsList = document.getElementById('bulkResultsList');
  bulkResultsList.innerHTML = '';
  
  // Add header
  const headerRow = document.createElement('div');
  headerRow.className = 'row mb-3';
  headerRow.innerHTML = `
    <div class="col-12">
      <h5>Compliance Test Results for ${formatStandardName(standard)}</h5>
      <p class="text-muted">Tested ${Object.keys(results).length} classes against ${formatStandardName(standard)}</p>
    </div>
  `;
  bulkResultsList.appendChild(headerRow);
  
  // Create table
  const table = document.createElement('table');
  table.className = 'table table-striped table-hover';
  
  // Table header
  const tableHeader = `
    <thead>
      <tr>
        <th>Class</th>
        <th>Compliance Score</th>
        <th>Violations</th>
        <th>Actions</th>
      </tr>
    </thead>
  `;
  
  // Table body
  let tableBody = '<tbody>';
  for (const [className, report] of Object.entries(results)) {
    const scoreClass = getComplianceScoreClass(report.compliancePercentage);
    const violationCount = report.violations ? report.violations.length : 0;
    
    tableBody += `
      <tr>
        <td>${className}</td>
        <td><span class="${scoreClass}">${report.compliancePercentage}%</span></td>
        <td>${violationCount}</td>
        <td>
          <button class="btn btn-sm btn-outline-primary view-details-btn" data-class-name="${className}" data-standard="${standard}">
            <i class="bi bi-eye"></i> Details
          </button>
        </td>
      </tr>
    `;
  }
  tableBody += '</tbody>';
  
  // Combine and add to list
  table.innerHTML = tableHeader + tableBody;
  bulkResultsList.appendChild(table);
  
  // Add event listeners to detail buttons
  const detailButtons = bulkResultsList.querySelectorAll('.view-details-btn');
  detailButtons.forEach(button => {
    button.addEventListener('click', function() {
      const className = this.getAttribute('data-class-name');
      const standard = this.getAttribute('data-standard');
      
      // Navigate to single test tab and run test for this class
      document.getElementById('compliance-check-tab').click();
      document.getElementById('className').value = className;
      document.getElementById('standard').value = standard;
      checkCompliance();
    });
  });
}

// Display bulk audit results
function displayBulkAuditResults(results) {
  const bulkResultsList = document.getElementById('bulkResultsList');
  bulkResultsList.innerHTML = '';
  
  // Add header
  const headerRow = document.createElement('div');
  headerRow.className = 'row mb-3';
  headerRow.innerHTML = `
    <div class="col-12">
      <h5>Compliance Audit Results</h5>
      <p class="text-muted">Audited ${Object.keys(results).length} classes against multiple standards</p>
    </div>
  `;
  bulkResultsList.appendChild(headerRow);
  
  // Create table
  const table = document.createElement('table');
  table.className = 'table table-striped table-hover';
  
  // Get list of all standards in results
  const allStandards = new Set();
  for (const standardReports of Object.values(results)) {
    for (const standard of Object.keys(standardReports)) {
      allStandards.add(standard);
    }
  }
  const standardsList = Array.from(allStandards);
  
  // Table header
  let tableHeader = '<thead><tr><th>Class</th>';
  standardsList.forEach(standard => {
    tableHeader += `<th>${formatStandardName(standard)}</th>`;
  });
  tableHeader += '<th>Actions</th></tr></thead>';
  
  // Table body
  let tableBody = '<tbody>';
  for (const [className, standardReports] of Object.entries(results)) {
    tableBody += '<tr><td>' + className + '</td>';
    
    standardsList.forEach(standard => {
      const report = standardReports[standard];
      if (report) {
        const scoreClass = getComplianceScoreClass(report.compliancePercentage);
        tableBody += `<td><span class="${scoreClass}">${report.compliancePercentage}%</span></td>`;
      } else {
        tableBody += '<td>N/A</td>';
      }
    });
    
    tableBody += `
      <td>
        <button class="btn btn-sm btn-outline-primary view-audit-btn" data-class-name="${className}">
          <i class="bi bi-eye"></i> View Audit
        </button>
      </td>
    </tr>`;
  }
  tableBody += '</tbody>';
  
  // Combine and add to list
  table.innerHTML = tableHeader + tableBody;
  bulkResultsList.appendChild(table);
  
  // Add event listeners to audit buttons
  const auditButtons = bulkResultsList.querySelectorAll('.view-audit-btn');
  auditButtons.forEach(button => {
    button.addEventListener('click', function() {
      const className = this.getAttribute('data-class-name');
      
      // Navigate to audit tab and run audit for this class
      document.getElementById('compliance-audit-tab').click();
      document.getElementById('auditClassName').value = className;
      
      // Ensure standards are checked
      standardsList.forEach(standard => {
        const checkboxId = 'standard' + standard.replace('_', '');
        const checkbox = document.getElementById(checkboxId);
        if (checkbox) {
          checkbox.checked = true;
        }
      });
      
      runComplianceAudit();
    });
  });
}

// Display bulk generate results
function displayBulkGenerateResults(results, standard) {
  const bulkResultsList = document.getElementById('bulkResultsList');
  bulkResultsList.innerHTML = '';
  
  // Add header
  const headerRow = document.createElement('div');
  headerRow.className = 'row mb-3';
  headerRow.innerHTML = `
    <div class="col-12">
      <h5>Generated Compliance Tests for ${formatStandardName(standard)}</h5>
      <p class="text-muted">Generated tests for ${Object.keys(results).length} classes</p>
    </div>
  `;
  bulkResultsList.appendChild(headerRow);
  
  // Create table
  const table = document.createElement('table');
  table.className = 'table table-striped table-hover';
  
  // Table header
  const tableHeader = `
    <thead>
      <tr>
        <th>Class</th>
        <th>Tests Generated</th>
        <th>Actions</th>
      </tr>
    </thead>
  `;
  
  // Table body
  let tableBody = '<tbody>';
  for (const [className, tests] of Object.entries(results)) {
    const testCount = tests.length;
    
    tableBody += `
      <tr>
        <td>${className}</td>
        <td>${testCount}</td>
        <td>
          <button class="btn btn-sm btn-outline-primary view-tests-btn" data-class-name="${className}" data-standard="${standard}">
            <i class="bi bi-eye"></i> View Tests
          </button>
        </td>
      </tr>
    `;
  }
  tableBody += '</tbody>';
  
  // Combine and add to list
  table.innerHTML = tableHeader + tableBody;
  bulkResultsList.appendChild(table);
  
  // Add event listeners to view tests buttons
  const viewTestsButtons = bulkResultsList.querySelectorAll('.view-tests-btn');
  viewTestsButtons.forEach(button => {
    button.addEventListener('click', function() {
      const className = this.getAttribute('data-class-name');
      const standard = this.getAttribute('data-standard');
      
      // Navigate to tests tab and generate tests for this class
      document.getElementById('compliance-tests-tab').click();
      document.getElementById('testClassName').value = className;
      document.getElementById('testStandard').value = standard;
      generateComplianceTests();
    });
  });
}
}

// Generate tests from report
function generateTestsFromReport() {
  const className = document.getElementById('className').value;
  const standard = document.getElementById('standard').value;
  
  if (!className || !standard) {
    alert('Class name or standard information is missing.');
    return;
  }
  
  // Navigate to compliance tests tab
  const complianceTestsTab = document.getElementById('compliance-tests-tab');
  complianceTestsTab.click();
  
  // Fill in the generate tests form
  document.getElementById('testClassName').value = className;
  document.getElementById('testStandard').value = standard;
  
  // Trigger the form submission
  document.getElementById('generateTestsForm').dispatchEvent(new Event('submit'));
}

// Generate test for a specific violation
function generateTestForViolation(ruleId, className, methodName, standard) {
  // Navigate to compliance tests tab
  const complianceTestsTab = document.getElementById('compliance-tests-tab');
  complianceTestsTab.click();
  
  // Fill in the generate tests form
  document.getElementById('testClassName').value = className;
  document.getElementById('testStandard').value = standard;
  
  // Show loading UI and generate tests
  document.getElementById('noTestsMessage').style.display = 'none';
  document.getElementById('testsList').style.display = 'none';
  document.getElementById('generatedTestsSkeleton').style.display = 'block';
  
  // For demo, show sample data after timeout
  setTimeout(() => {
    document.getElementById('generatedTestsSkeleton').style.display = 'none';
    document.getElementById('testsList').style.display = 'block';
    
    // Enable buttons
    document.getElementById('exportTestsBtn').disabled = false;
    document.getElementById('runAllTestsBtn').disabled = false;
    
    // Display a single test for this violation
    const demoTests = [
      {
        id: new Date().getTime().toString(),
        name: `testCompliance${methodName}For${standard}`,
        className: `compliance.${className}Test`,
        packageName: 'com.example.compliance',
        description: `Compliance test for ${className}.${methodName} against ${standard} standard (Rule: ${ruleId})`,
        type: 'SECURITY',
        status: 'GENERATED',
        methodName: `testCompliance${methodName}For${standard}`
      }
    ];
    
    displayComplianceTests(demoTests);
  }, 1500);
}

// Generate compliance tests for a specific standard
function generateComplianceTestsForStandard(className, standard) {
  // Navigate to compliance tests tab
  const complianceTestsTab = document.getElementById('compliance-tests-tab');
  complianceTestsTab.click();
  
  // Fill in the generate tests form
  document.getElementById('testClassName').value = className;
  document.getElementById('testStandard').value = standard;
  
  // Trigger the form submission
  document.getElementById('generateTestsForm').dispatchEvent(new Event('submit'));
}

// Helper function: Format standard name
function formatStandardName(standard) {
  switch (standard) {
    case 'OWASP_TOP_10':
      return 'OWASP Top 10';
    case 'PCI_DSS':
      return 'PCI DSS';
    case 'GDPR':
      return 'GDPR';
    case 'HIPAA':
      return 'HIPAA';
    case 'SOX':
      return 'SOX';
    default:
      return standard;
  }
}

// Helper function: Get severity color class
function getSeverityColorClass(severity) {
  switch (severity) {
    case 'CRITICAL':
      return 'danger';
    case 'HIGH':
      return 'warning';
    case 'MEDIUM':
      return 'info';
    case 'LOW':
    case 'INFORMATIONAL':
      return 'success';
    default:
      return 'secondary';
  }
}

// Helper function: Get compliance score class
function getComplianceScoreClass(score) {
  if (score >= 90) {
    return 'text-success';
  } else if (score >= 80) {
    return 'text-info';
  } else if (score >= 70) {
    return 'text-warning';
  } else {
    return 'text-danger';
  }
}

// Helper function: Get standard CSS class
function getStandardClass(standard) {
  switch (standard) {
    case 'OWASP_TOP_10':
    case 'OWASP':
      return 'owasp';
    case 'PCI_DSS':
    case 'PCI':
      return 'pci';
    case 'GDPR':
      return 'gdpr';
    case 'HIPAA':
      return 'hipaa';
    case 'SOX':
      return 'sox';
    default:
      return '';
  }
}

// Helper function: Get standard color
function getStandardColor(standard) {
  switch (standard) {
    case 'OWASP_TOP_10':
    case 'OWASP':
      return '#6c63ff';
    case 'PCI_DSS':
    case 'PCI':
      return '#ff4757';
    case 'GDPR':
      return '#2e86de';
    case 'HIPAA':
      return '#20c997';
    case 'SOX':
      return '#2f3542';
    default:
      return '#6c757d';
  }
}

// Helper function: Get test status badge class
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

// Helper function: Escape HTML
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