// dashboard.js - JavaScript for the Quality Intelligence Dashboard

// API Base URL - use relative path to work on any host
const API_BASE_URL = '/api/v1';

// Global variables
let modelStatusVisible = false;
let modelStatusPollingInterval = null;
let dashboardRefreshInterval = null;
const modelStatusUpdateFrequency = 2000; // 2 seconds
let currentTimeRange = 30; // Default to 30 days
let charts = {}; // Store chart objects

// Initialize the dashboard
document.addEventListener('DOMContentLoaded', function() {
  try {
    console.log('Dashboard initializing...');
    
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

    // Initialize tooltips
    initTooltips();

    // Initialize the dashboard data
    initDashboard();

    // Initialize model status display
    initModelStatus();

    // Initialize event listeners
    initEventListeners();
    
    console.log('Dashboard initialized successfully');
  } catch (error) {
    console.error('Error initializing dashboard:', error);
    // Show error message to user
    const container = document.querySelector('.container-fluid');
    if (container) {
      container.insertAdjacentHTML('afterbegin', `
        <div class="alert alert-danger alert-dismissible fade show" role="alert">
          <strong>Error!</strong> Failed to initialize dashboard: ${error.message}
          <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        </div>
      `);
    }
  }
});

// Initialize Bootstrap tooltips
function initTooltips() {
  const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]');
  [...tooltipTriggerList].map(tooltipTriggerEl => new bootstrap.Tooltip(tooltipTriggerEl));
}

// Initialize dashboard data
function initDashboard() {
  // Load dashboard summary
  loadDashboardSummary();

  // Load failure trends
  loadFailureTrends(currentTimeRange);

  // Load category health
  loadCategoryHealth();

  // Load most frequently failing tests
  loadMostFailingTests();

  // Load flaky tests
  loadFlakyTests();

  // Load recent executions
  loadRecentExecutions();

  // Setup auto-refresh if enabled
  const refreshInterval = parseInt(localStorage.getItem('dashboardRefreshInterval') || '60', 10);
  if (refreshInterval > 0) {
    dashboardRefreshInterval = setInterval(refreshDashboard, refreshInterval * 1000);
  }
}

// Initialize event listeners
function initEventListeners() {
  // Refresh dashboard button
  const refreshDashboardBtn = document.getElementById('refreshDashboardBtn');
  if (refreshDashboardBtn) {
    refreshDashboardBtn.addEventListener('click', refreshDashboard);
  }

  // Time range buttons
  const timeRangeBtns = document.querySelectorAll('.time-range-btn');
  timeRangeBtns.forEach(btn => {
    btn.addEventListener('click', function() {
      // Remove active class from all buttons
      timeRangeBtns.forEach(b => b.classList.remove('active'));
      // Add active class to clicked button
      this.classList.add('active');
      // Update time range and reload data
      currentTimeRange = parseInt(this.getAttribute('data-range'), 10);
      loadFailureTrends(currentTimeRange);
    });
  });

  // Save dashboard config button
  const saveConfigBtn = document.getElementById('saveConfigBtn');
  if (saveConfigBtn) {
    saveConfigBtn.addEventListener('click', saveDashboardConfig);
  }

  // View all flaky tests button
  const viewAllFlakyTestsBtn = document.getElementById('viewAllFlakyTestsBtn');
  if (viewAllFlakyTestsBtn) {
    viewAllFlakyTestsBtn.addEventListener('click', function() {
      // Here you would redirect to a detailed view
      // For now, let's just show a modal with more flaky tests
      loadAllFlakyTests();
    });
  }

  // Heal flaky test button
  const healFlakyTestBtn = document.getElementById('healFlakyTestBtn');
  if (healFlakyTestBtn) {
    healFlakyTestBtn.addEventListener('click', function() {
      const testId = this.getAttribute('data-test-id');
      if (testId) {
        healTest(testId);
      }
    });
  }
}

// Initialize model status display
function initModelStatus() {
  const refreshModelStatusBtn = document.getElementById('refreshModelStatusBtn');
  if (refreshModelStatusBtn) {
    refreshModelStatusBtn.addEventListener('click', updateModelStatus);
  }
}

// Toggle model status display
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

// Update model status display
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

// Update model status UI elements
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

// Load dashboard summary
function loadDashboardSummary() {
  fetch(`${API_BASE_URL}/quality-dashboard/summary`)
    .then(response => {
      if (!response.ok) {
        throw new Error(`API returned ${response.status}`);
      }
      return response.json();
    })
    .then(data => {
      updateDashboardSummary(data);
    })
    .catch(error => {
      console.error('Error loading dashboard summary:', error);
      showErrorMessage('Failed to load dashboard summary');
    });
}

// Update dashboard summary with data
function updateDashboardSummary(data) {
  // Health score
  const healthScoreValue = document.getElementById('healthScoreValue');
  const healthScoreProgress = document.getElementById('healthScoreProgress');
  if (healthScoreValue && healthScoreProgress) {
    healthScoreValue.textContent = data.healthScore;
    healthScoreProgress.style.width = `${data.healthScore}%`;
    
    // Update color based on score
    healthScoreProgress.className = 'progress-bar';
    if (data.healthScore >= 80) {
      healthScoreProgress.classList.add('bg-success');
    } else if (data.healthScore >= 60) {
      healthScoreProgress.classList.add('bg-info');
    } else if (data.healthScore >= 40) {
      healthScoreProgress.classList.add('bg-warning');
    } else {
      healthScoreProgress.classList.add('bg-danger');
    }
  }
  
  // Pass rate
  const passRateValue = document.getElementById('passRateValue');
  const passRateProgress = document.getElementById('passRateProgress');
  if (passRateValue && passRateProgress) {
    const passRate = Math.round(data.averagePassRate * 100);
    passRateValue.textContent = passRate;
    passRateProgress.style.width = `${passRate}%`;
    
    // Update color based on pass rate
    passRateProgress.className = 'progress-bar';
    if (passRate >= 90) {
      passRateProgress.classList.add('bg-success');
    } else if (passRate >= 75) {
      passRateProgress.classList.add('bg-info');
    } else if (passRate >= 50) {
      passRateProgress.classList.add('bg-warning');
    } else {
      passRateProgress.classList.add('bg-danger');
    }
  }
  
  // Flaky tests
  const flakyTestsValue = document.getElementById('flakyTestsValue');
  const flakyTestsPercent = document.getElementById('flakyTestsPercent');
  const flakyTestsTrend = document.getElementById('flakyTestsTrend');
  if (flakyTestsValue && flakyTestsPercent && flakyTestsTrend) {
    flakyTestsValue.textContent = data.flakyTests;
    
    // Calculate flaky tests percentage
    const flakyPercent = Math.round((data.flakyTests / data.totalTests) * 100);
    flakyTestsPercent.textContent = `${flakyPercent}%`;
    
    // Trend indicator (mock for now)
    if (Math.random() > 0.5) {
      flakyTestsTrend.innerHTML = '<i class="bi bi-arrow-down"></i> Improving';
      flakyTestsTrend.className = 'badge bg-success-light text-success';
    } else {
      flakyTestsTrend.innerHTML = '<i class="bi bi-arrow-up"></i> Increasing';
      flakyTestsTrend.className = 'badge bg-danger-light text-danger';
    }
  }
  
  // Test coverage
  const testCoverageValue = document.getElementById('testCoverageValue');
  const coverageProgress = document.getElementById('coverageProgress');
  if (testCoverageValue && coverageProgress) {
    const coverage = Math.round(data.testCoverage * 100);
    testCoverageValue.textContent = coverage;
    coverageProgress.style.width = `${coverage}%`;
  }
  
  // Test status counts and chart
  const passedTestsCount = document.getElementById('passedTestsCount');
  const failedTestsCount = document.getElementById('failedTestsCount');
  const brokenTestsCount = document.getElementById('brokenTestsCount');
  const flakyTestsCount = document.getElementById('flakyTestsCount');
  
  if (passedTestsCount && failedTestsCount && brokenTestsCount && flakyTestsCount) {
    passedTestsCount.textContent = data.passingTests;
    failedTestsCount.textContent = data.failingTests;
    brokenTestsCount.textContent = data.totalTests - data.passingTests - data.failingTests - data.flakyTests;
    flakyTestsCount.textContent = data.flakyTests;
    
    // Update chart
    updateTestStatusChart(data);
  }
  
  // Hide skeletons and show actual content
  document.getElementById('testStatusCardSkeleton').style.display = 'none';
  document.getElementById('testStatusCard').style.display = 'block';
}

// Update test status distribution chart
function updateTestStatusChart(data) {
  const ctx = document.getElementById('testStatusChart').getContext('2d');
  
  // Destroy existing chart if it exists
  if (charts.testStatusChart) {
    charts.testStatusChart.destroy();
  }
  
  // Create the chart
  charts.testStatusChart = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: ['Passed', 'Failed', 'Broken', 'Flaky'],
      datasets: [{
        data: [
          data.passingTests,
          data.failingTests,
          data.totalTests - data.passingTests - data.failingTests - data.flakyTests,
          data.flakyTests
        ],
        backgroundColor: [
          '#28a745',
          '#dc3545',
          '#fd7e14',
          '#ffc107'
        ],
        borderWidth: 0,
        hoverOffset: 4
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '70%',
      plugins: {
        legend: {
          display: false
        },
        tooltip: {
          callbacks: {
            label: function(context) {
              const label = context.label || '';
              const value = context.raw;
              const total = context.dataset.data.reduce((a, b) => a + b, 0);
              const percentage = Math.round((value / total) * 100);
              return `${label}: ${value} (${percentage}%)`;
            }
          }
        }
      }
    }
  });
}

// Load failure trends
function loadFailureTrends(days) {
  const useRealData = localStorage.getItem('useRealData') === 'true';
  fetch(`${API_BASE_URL}/quality-dashboard/trends?timeframe=${days}${useRealData ? '&useRealData=true' : ''}`)
    .then(response => response.json())
    .then(data => {
      updateFailureTrendChart(data);
    })
    .catch(error => {
      console.error('Error loading failure trends:', error);
      showErrorMessage('Failed to load failure trends');
    });
}

// Update failure trend chart
function updateFailureTrendChart(trendData) {
  const ctx = document.getElementById('trendChart').getContext('2d');
  
  // Prepare chart data
  const labels = trendData.map(point => {
    const date = new Date(point.timestamp);
    return date.toLocaleDateString();
  });
  
  const passRateData = trendData.map(point => point.passRate * 100);
  const failedTestsData = trendData.map(point => point.failedTests);
  
  // Destroy existing chart if it exists
  if (charts.trendChart) {
    charts.trendChart.destroy();
  }
  
  // Create the chart
  charts.trendChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: labels,
      datasets: [
        {
          label: 'Pass Rate (%)',
          data: passRateData,
          borderColor: '#4361ee',
          backgroundColor: 'rgba(67, 97, 238, 0.1)',
          fill: true,
          tension: 0.3,
          yAxisID: 'y',
          pointRadius: 3,
          pointBackgroundColor: '#4361ee'
        },
        {
          label: 'Failed Tests',
          data: failedTestsData,
          borderColor: '#dc3545',
          backgroundColor: 'rgba(220, 53, 69, 0.1)',
          fill: true,
          tension: 0.3,
          yAxisID: 'y1',
          pointRadius: 2,
          pointBackgroundColor: '#dc3545'
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false
      },
      plugins: {
        legend: {
          position: 'top',
          align: 'end'
        },
        tooltip: {
          mode: 'index',
          intersect: false
        }
      },
      scales: {
        x: {
          grid: {
            display: false
          }
        },
        y: {
          type: 'linear',
          display: true,
          position: 'left',
          title: {
            display: true,
            text: 'Pass Rate (%)'
          },
          min: 0,
          max: 100,
          ticks: {
            callback: function(value) {
              return value + '%';
            }
          }
        },
        y1: {
          type: 'linear',
          display: true,
          position: 'right',
          title: {
            display: true,
            text: 'Failed Tests'
          },
          min: 0,
          grid: {
            drawOnChartArea: false
          }
        }
      }
    }
  });
  
  // Hide skeleton and show actual content
  document.getElementById('failureTrendSkeleton').style.display = 'none';
  document.getElementById('failureTrendChart').style.display = 'block';
}

// Load category health
function loadCategoryHealth() {
  const useRealData = localStorage.getItem('useRealData') === 'true';
  fetch(`${API_BASE_URL}/quality-dashboard/health-by-category${useRealData ? '?useRealData=true' : ''}`)
    .then(response => response.json())
    .then(data => {
      updateCategoryHealth(data);
    })
    .catch(error => {
      console.error('Error loading category health:', error);
      showErrorMessage('Failed to load category health');
    });
}

// Update category health
function updateCategoryHealth(categoryData) {
  const tableBody = document.getElementById('categoryHealthTableBody');
  if (!tableBody) return;
  
  // Clear existing rows
  tableBody.innerHTML = '';
  
  // Add rows for each category
  Object.entries(categoryData).forEach(([category, health]) => {
    const row = document.createElement('tr');
    
    // Calculate pass rate percentage
    const passRate = Math.round((health.passingTests / health.totalTests) * 100);
    
    // Determine health indicator class
    let healthClass = getHealthClass(health.healthScore);
    
    row.innerHTML = `
      <td>
        <div class="d-flex align-items-center">
          <div class="category-icon me-2">
            <i class="bi ${getCategoryIcon(category)}"></i>
          </div>
          <div>
            <div class="fw-semibold">${category}</div>
            <small class="text-muted">${health.totalTests} tests</small>
          </div>
        </div>
      </td>
      <td class="text-center">${health.passingTests} / ${health.totalTests}</td>
      <td class="text-center">${passRate}%</td>
      <td class="text-center">
        <div class="d-flex align-items-center justify-content-center">
          <span class="badge me-2 ${healthClass}-light text-${healthClass}">${health.healthScore}</span>
          <div class="category-bar" style="width: 50px;">
            <div class="category-bar-fill bg-${healthClass}" style="width: ${health.healthScore}%;"></div>
          </div>
        </div>
      </td>
    `;
    
    tableBody.appendChild(row);
  });
  
  // Hide skeleton and show actual content
  document.getElementById('healthByCategorySkeleton').style.display = 'none';
  document.getElementById('healthByCategoryContent').style.display = 'block';
}

// Load most frequently failing tests
function loadMostFailingTests() {
  const useRealData = localStorage.getItem('useRealData') === 'true';
  fetch(`${API_BASE_URL}/quality-dashboard/most-failing?limit=5${useRealData ? '&useRealData=true' : ''}`)
    .then(response => response.json())
    .then(data => {
      updateMostFailingTests(data);
    })
    .catch(error => {
      console.error('Error loading most failing tests:', error);
      showErrorMessage('Failed to load most failing tests');
    });
}

// Update most failing tests
function updateMostFailingTests(failingTests) {
  const tableBody = document.getElementById('failingTestsTableBody');
  if (!tableBody) return;
  
  // Clear existing rows
  tableBody.innerHTML = '';
  
  // Add rows for each failing test
  failingTests.forEach(test => {
    const row = document.createElement('tr');
    
    // Calculate failure percentage
    const failureRate = Math.round(test.failureRate * 100);
    
    row.innerHTML = `
      <td>
        <div>
          <div class="fw-semibold">${test.testName}</div>
          <small class="text-muted">${test.className}.${test.methodName}</small>
        </div>
      </td>
      <td class="text-center">
        <span class="fw-semibold text-danger">${test.failureCount}</span>
        <span class="text-muted">/ ${test.executionCount}</span>
      </td>
      <td>
        <div class="d-flex align-items-center">
          <span class="me-2">${failureRate}%</span>
          <div class="failure-rate-bar flex-grow-1">
            <div class="failure-rate-fill" style="width: ${failureRate}%;"></div>
          </div>
        </div>
      </td>
      <td class="text-center">
        <button class="btn btn-sm btn-outline-primary action-btn heal-test-btn" data-test-id="${test.testId}">
          <i class="bi bi-heart-pulse"></i> Heal
        </button>
        <button class="btn btn-sm btn-outline-secondary action-btn" data-test-id="${test.testId}">
          <i class="bi bi-search"></i> Details
        </button>
      </td>
    `;
    
    tableBody.appendChild(row);
  });
  
  // Add click event for heal buttons
  const healButtons = tableBody.querySelectorAll('.heal-test-btn');
  healButtons.forEach(button => {
    button.addEventListener('click', function() {
      const testId = this.getAttribute('data-test-id');
      healTest(testId);
    });
  });
  
  // Hide skeleton and show actual content
  document.getElementById('failingTestsSkeleton').style.display = 'none';
  document.getElementById('failingTestsContent').style.display = 'block';
}

// Load flaky tests
function loadFlakyTests() {
  const useRealData = localStorage.getItem('useRealData') === 'true';
  fetch(`${API_BASE_URL}/quality-dashboard/flaky-tests${useRealData ? '?useRealData=true' : ''}`)
    .then(response => response.json())
    .then(data => {
      updateFlakyTests(data);
    })
    .catch(error => {
      console.error('Error loading flaky tests:', error);
      showErrorMessage('Failed to load flaky tests');
    });
}

// Update flaky tests
function updateFlakyTests(flakyTests) {
  const flakyTestsList = document.getElementById('flakyTestsList');
  if (!flakyTestsList) return;
  
  // Clear existing items
  flakyTestsList.innerHTML = '';
  
  // Take the first 3 for the summary view
  const displayTests = flakyTests.slice(0, 3);
  
  // Add items for each flaky test
  displayTests.forEach(test => {
    const item = document.createElement('div');
    item.className = 'flaky-test-item border-bottom';
    
    // Calculate pass rate percentage
    const passRate = Math.round(test.passRate * 100);
    
    item.innerHTML = `
      <div class="d-flex justify-content-between align-items-center">
        <div>
          <h6 class="mb-1">${test.testName}</h6>
          <small class="text-muted">${test.className}.${test.methodName}</small>
        </div>
        <span class="badge bg-warning-light text-warning">Pass Rate: ${passRate}%</span>
      </div>
      <div class="flaky-test-details mt-1">
        <div class="execution-alternating">
          <i class="bi bi-arrow-repeat"></i> ${test.alternatingSessions} alternating results in ${test.executionCount} executions
        </div>
        <div class="flaky-pattern text-muted">${test.suggestedAction}</div>
      </div>
      <div class="mt-2">
        <button class="btn btn-sm btn-outline-primary view-details-btn" data-test-id="${test.testId}">
          <i class="bi bi-search"></i> View Details
        </button>
        <button class="btn btn-sm btn-outline-success heal-test-btn" data-test-id="${test.testId}">
          <i class="bi bi-heart-pulse"></i> Heal Test
        </button>
      </div>
    `;
    
    flakyTestsList.appendChild(item);
  });
  
  // Add click event for view details buttons
  const viewDetailsButtons = flakyTestsList.querySelectorAll('.view-details-btn');
  viewDetailsButtons.forEach(button => {
    button.addEventListener('click', function() {
      const testId = this.getAttribute('data-test-id');
      showFlakyTestDetails(testId);
    });
  });
  
  // Add click event for heal buttons
  const healButtons = flakyTestsList.querySelectorAll('.heal-test-btn');
  healButtons.forEach(button => {
    button.addEventListener('click', function() {
      const testId = this.getAttribute('data-test-id');
      healTest(testId);
    });
  });
  
  // Hide skeleton and show actual content
  document.getElementById('flakyTestsSkeleton').style.display = 'none';
  document.getElementById('flakyTestsContent').style.display = 'block';
}

// Load all flaky tests for the modal
function loadAllFlakyTests() {
  const useRealData = localStorage.getItem('useRealData') === 'true';
  fetch(`${API_BASE_URL}/quality-dashboard/flaky-tests${useRealData ? '?useRealData=true' : ''}`)
    .then(response => response.json())
    .then(data => {
      showAllFlakyTestsModal(data);
    })
    .catch(error => {
      console.error('Error loading all flaky tests:', error);
      showErrorMessage('Failed to load all flaky tests');
    });
}

// Show modal with all flaky tests
function showAllFlakyTestsModal(flakyTests) {
  // This would typically be done with a real modal
  // For now, we'll just show a detailed view of the first flaky test
  if (flakyTests.length > 0) {
    showFlakyTestDetails(flakyTests[0].testId);
  }
}

// Show flaky test details
function showFlakyTestDetails(testId) {
  // Fetch the specific test details from API
  fetch(`${API_BASE_URL}/quality-dashboard/flaky-tests/${testId}`)
    .then(response => response.json())
    .then(test => {
      displayFlakyTestDetailsModal(test);
    })
    .catch(error => {
      console.error('Error loading flaky test details:', error);
      showErrorMessage('Failed to load test details');
    });
}

// Display flaky test details in modal
function displayFlakyTestDetailsModal(test) {
  
  const modal = new bootstrap.Modal(document.getElementById('flakyTestDetailsModal'));
  const modalContent = document.getElementById('flakyTestDetailsContent');
  const healButton = document.getElementById('healFlakyTestBtn');
  
  if (modalContent && healButton) {
    // Populate modal content
    modalContent.innerHTML = `
      <h5>${test.testName}</h5>
      <p class="text-muted">${test.className}.${test.methodName}</p>
      
      <div class="card mb-3">
        <div class="card-header bg-light">
          <h6 class="mb-0">Execution History</h6>
        </div>
        <div class="card-body">
          <div class="d-flex justify-content-between mb-3">
            <div>
              <h6 class="mb-1">Pass Rate</h6>
              <span class="fs-4">${Math.round(test.passRate * 100)}%</span>
            </div>
            <div>
              <h6 class="mb-1">Executions</h6>
              <span class="fs-4">${test.executionCount}</span>
            </div>
            <div>
              <h6 class="mb-1">Alternations</h6>
              <span class="fs-4">${test.alternatingSessions}</span>
            </div>
            <div>
              <h6 class="mb-1">Last Run</h6>
              <span class="text-muted">${new Date(test.lastExecuted).toLocaleString()}</span>
            </div>
          </div>
          
          <h6>Suggested Action</h6>
          <div class="alert alert-warning">
            <i class="bi bi-lightbulb"></i> ${test.suggestedAction}
          </div>
        </div>
      </div>
      
      <div class="card">
        <div class="card-header bg-light">
          <h6 class="mb-0">Test Source Code</h6>
        </div>
        <div class="card-body">
          <pre class="mb-0 code-block"><code>
@Test
public void ${test.methodName}() {
    // Test implementation would be shown here
    // This is where the flakiness occurs
    ${generateMockTestCode(test)}
}
          </code></pre>
        </div>
      </div>
    `;
    
    // Set the test ID on the heal button
    healButton.setAttribute('data-test-id', test.testId);
    
    // Show the modal
    modal.show();
  }
}

// Load recent executions
function loadRecentExecutions() {
  const useRealData = localStorage.getItem('useRealData') === 'true';
  fetch(`${API_BASE_URL}/tests/recent-executions?limit=5${useRealData ? '&useRealData=true' : ''}`)
    .then(response => response.json())
    .then(data => {
      updateRecentExecutions(data);
    })
    .catch(error => {
      console.error('Error loading recent executions:', error);
      showErrorMessage('Failed to load recent executions');
    });
}

// Update recent executions
function updateRecentExecutions(executions) {
  const tableBody = document.getElementById('recentExecutionsTableBody');
  if (!tableBody) return;
  
  // Clear existing rows
  tableBody.innerHTML = '';
  
  // Add rows for each execution
  executions.forEach(execution => {
    const row = document.createElement('tr');
    row.className = 'execution-row';
    
    // Format timestamp
    const executionTime = new Date(execution.executedAt).toLocaleString();
    
    // Determine status class
    let statusClass = 'status-passed';
    let statusText = 'Passed';
    
    if (!execution.successful) {
      statusClass = 'status-failed';
      statusText = 'Failed';
    } else if (execution.status === 'FLAKY') {
      statusClass = 'status-flaky';
      statusText = 'Flaky';
    }
    
    // Format duration
    const duration = formatDuration(execution.executionTimeMs);
    
    row.innerHTML = `
      <td>
        <div>
          <div class="fw-semibold">${execution.testName || 'Test'}</div>
          <small class="execution-time">${executionTime}</small>
        </div>
      </td>
      <td>
        ${execution.errorMessage ? 
          `<span class="text-danger" data-bs-toggle="tooltip" data-bs-placement="top" title="${execution.errorMessage}">
            <i class="bi bi-exclamation-circle"></i>
           </span>` : 
          ''}
      </td>
      <td class="text-center">
        <span class="d-flex align-items-center justify-content-center">
          <span class="execution-status ${statusClass}"></span>
          ${statusText}
        </span>
      </td>
      <td>
        <span class="execution-duration">${duration}</span>
      </td>
    `;
    
    tableBody.appendChild(row);
  });
  
  // Reinitialize tooltips
  initTooltips();
  
  // Hide skeleton and show actual content
  document.getElementById('recentExecutionsSkeleton').style.display = 'none';
  document.getElementById('recentExecutionsContent').style.display = 'block';
}

// Refresh the dashboard
function refreshDashboard() {
  // Reload all data
  loadDashboardSummary();
  loadFailureTrends(currentTimeRange);
  loadCategoryHealth();
  loadMostFailingTests();
  loadFlakyTests();
  loadRecentExecutions();
}

// Save dashboard configuration
function saveDashboardConfig() {
  const timeRange = document.getElementById('dashboardTimeRange').value;
  const refreshInterval = document.getElementById('dashboardRefreshInterval').value;
  const showFlakyTests = document.getElementById('showFlakyTestsSwitch').checked;
  const showFailureTrend = document.getElementById('showFailureTrendSwitch').checked;
  const showCategoryHealth = document.getElementById('showCategoryHealthSwitch').checked;
  
  // Save to local storage
  localStorage.setItem('dashboardTimeRange', timeRange);
  localStorage.setItem('dashboardRefreshInterval', refreshInterval);
  localStorage.setItem('showFlakyTests', showFlakyTests);
  localStorage.setItem('showFailureTrend', showFailureTrend);
  localStorage.setItem('showCategoryHealth', showCategoryHealth);
  
  // Update current settings
  currentTimeRange = parseInt(timeRange, 10);
  
  // Clear existing interval
  if (dashboardRefreshInterval) {
    clearInterval(dashboardRefreshInterval);
    dashboardRefreshInterval = null;
  }
  
  // Set new interval if enabled
  if (parseInt(refreshInterval, 10) > 0) {
    dashboardRefreshInterval = setInterval(refreshDashboard, parseInt(refreshInterval, 10) * 1000);
  }
  
  // Reload data with new settings
  loadFailureTrends(currentTimeRange);
  
  // Update UI visibility based on toggles
  const flakyTestsCard = document.querySelector('.card:has(#flakyTestsList)').closest('.col-lg-6');
  const failureTrendCard = document.querySelector('.card:has(#trendChart)').closest('.col-lg-8');
  const categoryHealthCard = document.querySelector('.card:has(#categoryHealthTableBody)').closest('.col-lg-6');
  
  if (flakyTestsCard) flakyTestsCard.style.display = showFlakyTests ? 'block' : 'none';
  if (failureTrendCard) failureTrendCard.style.display = showFailureTrend ? 'block' : 'none';
  if (categoryHealthCard) categoryHealthCard.style.display = showCategoryHealth ? 'block' : 'none';
  
  // Close the modal
  const modal = bootstrap.Modal.getInstance(document.getElementById('dashboardConfigModal'));
  if (modal) {
    modal.hide();
  }
  
  // Show success message
  alert('Dashboard configuration saved');
}

// Heal a test
function healTest(testId) {
  // Show loading state
  const healButtons = document.querySelectorAll(`.heal-test-btn[data-test-id="${testId}"]`);
  healButtons.forEach(button => {
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Healing...';
  });
  
  // Call the API to heal the test
  fetch(`${API_BASE_URL}/healing/test/${testId}`, {
    method: 'POST'
  })
    .then(response => {
      if (response.ok) {
        // Show success message
        alert('Test healing has been initiated. Please check back later for results.');
        
        // Close modal if open
        const modal = bootstrap.Modal.getInstance(document.getElementById('flakyTestDetailsModal'));
        if (modal) {
          modal.hide();
        }
        
        // Refresh data after a delay
        setTimeout(refreshDashboard, 1000);
      } else {
        throw new Error('Failed to heal test');
      }
    })
    .catch(error => {
      console.error('Error healing test:', error);
      alert('Failed to heal test. Please try again later.');
    })
    .finally(() => {
      // Reset buttons
      healButtons.forEach(button => {
        button.disabled = false;
        button.innerHTML = '<i class="bi bi-heart-pulse"></i> Heal Test';
      });
    });
}

// Utility functions
function getHealthClass(score) {
  if (score >= 90) return 'success';
  if (score >= 75) return 'info';
  if (score >= 50) return 'warning';
  if (score >= 25) return 'danger';
  return 'danger';
}

function getCategoryIcon(category) {
  switch (category.toLowerCase()) {
    case 'ui':
      return 'bi-window';
    case 'api':
      return 'bi-hdd-network';
    case 'unit':
      return 'bi-code-square';
    case 'integration':
      return 'bi-box';
    case 'performance':
      return 'bi-speedometer';
    case 'security':
      return 'bi-shield-lock';
    default:
      return 'bi-play-circle';
  }
}

function formatDuration(ms) {
  if (ms < 1000) {
    return `${ms}ms`;
  } else if (ms < 60000) {
    return `${(ms / 1000).toFixed(2)}s`;
  } else {
    const minutes = Math.floor(ms / 60000);
    const seconds = ((ms % 60000) / 1000).toFixed(2);
    return `${minutes}m ${seconds}s`;
  }
}

function showErrorMessage(message) {
  // Show error message in a toast or alert
  const toastContainer = document.getElementById('toastContainer') || createToastContainer();
  
  const toastId = 'toast-' + new Date().getTime();
  const toastHtml = `
    <div id="${toastId}" class="toast" role="alert" aria-live="assertive" aria-atomic="true">
      <div class="toast-header bg-danger text-white">
        <strong class="me-auto">Error</strong>
        <small>just now</small>
        <button type="button" class="btn-close btn-close-white" data-bs-dismiss="toast" aria-label="Close"></button>
      </div>
      <div class="toast-body">
        ${message}
      </div>
    </div>
  `;
  
  toastContainer.insertAdjacentHTML('beforeend', toastHtml);
  
  const toastElement = document.getElementById(toastId);
  const toast = new bootstrap.Toast(toastElement, { autohide: true, delay: 5000 });
  toast.show();
  
  toastElement.addEventListener('hidden.bs.toast', function() {
    toastElement.remove();
  });
}

function createToastContainer() {
  const toastContainer = document.createElement('div');
  toastContainer.id = 'toastContainer';
  toastContainer.className = 'toast-container position-fixed bottom-0 end-0 p-3';
  toastContainer.style.zIndex = '1070';
  document.body.appendChild(toastContainer);
  return toastContainer;
}

function generateMockTestCode(test) {
  return `
    // This is a simplified example of what causes flakiness
    int result = someService.performOperation();
    
    // The assert below fails intermittently
    // This suggests a timing or race condition issue
    assertEquals("Expected value", result);
  `;
}

