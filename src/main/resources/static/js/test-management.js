// test-management.js - JavaScript for the test management page

// API Base URL
const API_BASE_URL = 'http://localhost:8080/api/v1';

// Global variables
let modelStatusVisible = false;
let modelStatusPollingInterval = null;
const modelStatusUpdateFrequency = 2000; // 2 seconds
let allTests = []; // Store all tests
let currentSort = { field: 'modified', direction: 'desc' };
let currentFilters = {
  status: ['PASSED', 'FAILED', 'BROKEN', 'HEALED'],
  type: ['Unit', 'Integration', 'API', 'UI'],
  search: ''
};
let currentPage = 1;
const pageSize = 10;
let selectedBrokenTest = null;
let priorityConfig = null;

// Initialize the application
document.addEventListener('DOMContentLoaded', function() {
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

  // Initialize model status display
  initModelStatus();

  // Initialize event listeners
  initEventListeners();

  // Initialize test library
  loadTestLibrary();

  // Initialize broken tests
  loadBrokenTests();

  // Initialize prioritization config
  loadPriorityConfig();

  // Initialize prioritized tests
  loadPrioritizedTests();
});

// Initialize Bootstrap tooltips
function initTooltips() {
  const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]');
  [...tooltipTriggerList].map(tooltipTriggerEl => new bootstrap.Tooltip(tooltipTriggerEl));
}

// Initialize model status display
function initModelStatus() {
  const refreshModelStatusBtn = document.getElementById('refreshModelStatusBtn');
  if (refreshModelStatusBtn) {
    refreshModelStatusBtn.addEventListener('click', updateModelStatus);
  }
}

// Initialize event listeners
function initEventListeners() {
  // Refresh button
  const refreshBtn = document.getElementById('refreshBtn');
  if (refreshBtn) {
    refreshBtn.addEventListener('click', refreshData);
  }

  // Test search
  const testSearchBtn = document.getElementById('testSearchBtn');
  const testSearchInput = document.getElementById('testSearchInput');
  if (testSearchBtn && testSearchInput) {
    testSearchBtn.addEventListener('click', function() {
      currentFilters.search = testSearchInput.value.trim();
      filterAndDisplayTests();
    });
    
    testSearchInput.addEventListener('keyup', function(event) {
      if (event.key === 'Enter') {
        currentFilters.search = testSearchInput.value.trim();
        filterAndDisplayTests();
      }
    });
  }

  // Status filters
  const statusFilters = document.querySelectorAll('.status-filter');
  statusFilters.forEach(filter => {
    filter.addEventListener('change', function() {
      // Update status filters
      currentFilters.status = Array.from(document.querySelectorAll('.status-filter:checked')).map(cb => cb.value);
      filterAndDisplayTests();
    });
  });

  // Type filters
  const typeFilters = document.querySelectorAll('.type-filter');
  typeFilters.forEach(filter => {
    filter.addEventListener('change', function() {
      // Update type filters
      currentFilters.type = Array.from(document.querySelectorAll('.type-filter:checked')).map(cb => cb.value);
      filterAndDisplayTests();
    });
  });

  // Reset filters
  const resetFiltersBtn = document.getElementById('resetFiltersBtn');
  if (resetFiltersBtn) {
    resetFiltersBtn.addEventListener('click', resetFilters);
  }

  // Table sorting
  const sortableHeaders = document.querySelectorAll('.sortable');
  sortableHeaders.forEach(header => {
    header.addEventListener('click', function() {
      const field = this.getAttribute('data-sort');
      
      // Toggle direction if clicking the same field
      if (currentSort.field === field) {
        currentSort.direction = currentSort.direction === 'asc' ? 'desc' : 'asc';
      } else {
        currentSort.field = field;
        currentSort.direction = 'asc';
      }
      
      // Update sort indicators
      updateSortIndicators();
      
      // Resort and display tests
      sortAndDisplayTests();
    });
  });

  // Test library actions
  document.addEventListener('click', function(event) {
    // View test details
    if (event.target.closest('.view-test-btn')) {
      const testId = event.target.closest('.view-test-btn').getAttribute('data-test-id');
      showTestDetails(testId);
    }
    
    // Run test
    if (event.target.closest('.run-test-btn')) {
      const testId = event.target.closest('.run-test-btn').getAttribute('data-test-id');
      runTest(testId);
    }
  });

  // Healing actions
  document.addEventListener('click', function(event) {
    // Select broken test
    if (event.target.closest('.broken-test-item')) {
      const testId = event.target.closest('.broken-test-item').getAttribute('data-test-id');
      selectBrokenTest(testId);
    }
    
    // Heal selected test
    const healSelectedBtn = document.getElementById('healSelectedBtn');
    if (healSelectedBtn) {
      healSelectedBtn.addEventListener('click', function() {
        if (selectedBrokenTest) {
          healTest(selectedBrokenTest);
        }
      });
    }
    
    // Heal all tests
    const healAllBtn = document.getElementById('healAllBtn');
    if (healAllBtn) {
      healAllBtn.addEventListener('click', healAllTests);
    }
  });

  // Priority configuration
  const rangeInputs = document.querySelectorAll('.form-range');
  rangeInputs.forEach(input => {
    input.addEventListener('input', function() {
      // Update the value display
      const valueDisplay = document.getElementById(`${this.id}Value`);
      if (valueDisplay) {
        valueDisplay.textContent = this.value;
      }
    });
  });
  
  // Save priority configuration
  const savePriorityConfigBtn = document.getElementById('savePriorityConfigBtn');
  if (savePriorityConfigBtn) {
    savePriorityConfigBtn.addEventListener('click', savePriorityConfig);
  }
  
  // Prioritization context change
  const prioritizationContext = document.getElementById('prioritizationContext');
  if (prioritizationContext) {
    prioritizationContext.addEventListener('change', function() {
      loadPrioritizedTests(this.value);
    });
  }
  
  // Run prioritized tests
  const runPrioritizedBtn = document.getElementById('runPrioritizedBtn');
  if (runPrioritizedBtn) {
    runPrioritizedBtn.addEventListener('click', runPrioritizedTests);
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

// Load test library
function loadTestLibrary() {
  // Show skeleton
  document.getElementById('testLibrarySkeleton').style.display = 'block';
  document.getElementById('testLibraryContent').style.display = 'none';
  
  // Check if we should use real data or mock data
  // Fetch tests from API
  fetch(`${API_BASE_URL}/tests`)
    .then(response => response.json())
    .then(tests => {
      if (tests && tests.length > 0) {
        console.log('Loaded test data:', tests.length, 'tests');
        allTests = tests;
      } else {
        console.log('No tests found');
        allTests = [];
      }
      
      // Filter, sort, and display tests
      filterSortAndDisplayTests();
    })
    .catch(error => {
      console.error('Error loading tests:', error);
      showErrorMessage('Failed to load tests');
      allTests = [];
      
      // Filter, sort, and display tests
      filterSortAndDisplayTests();
    });
}

// Filter, sort, and display tests
function filterSortAndDisplayTests() {
  // Filter tests
  const filteredTests = filterTests(allTests);
  
  // Sort tests
  const sortedTests = sortTests(filteredTests);
  
  // Display tests
  displayTests(sortedTests);
}

// Filter tests by current filters
function filterTests(tests) {
  return tests.filter(test => {
    // Filter by status
    if (!currentFilters.status.includes(test.status)) {
      return false;
    }
    
    // Filter by type
    if (!currentFilters.type.includes(test.type)) {
      return false;
    }
    
    // Filter by search
    if (currentFilters.search && !testMatchesSearch(test, currentFilters.search)) {
      return false;
    }
    
    return true;
  });
}

// Check if test matches search string
function testMatchesSearch(test, search) {
  const searchLower = search.toLowerCase();
  return (
    (test.name && test.name.toLowerCase().includes(searchLower)) ||
    (test.className && test.className.toLowerCase().includes(searchLower)) ||
    (test.methodName && test.methodName.toLowerCase().includes(searchLower)) ||
    (test.packageName && test.packageName.toLowerCase().includes(searchLower))
  );
}

// Sort tests by current sort settings
function sortTests(tests) {
  return [...tests].sort((a, b) => {
    let aValue = a[currentSort.field];
    let bValue = b[currentSort.field];
    
    // Handle special cases
    if (currentSort.field === 'modified') {
      aValue = new Date(a.modifiedAt || a.createdAt || 0);
      bValue = new Date(b.modifiedAt || b.createdAt || 0);
    }
    
    // Handle string comparison
    if (typeof aValue === 'string' && typeof bValue === 'string') {
      if (currentSort.direction === 'asc') {
        return aValue.localeCompare(bValue);
      } else {
        return bValue.localeCompare(aValue);
      }
    }
    
    // Handle date comparison
    if (aValue instanceof Date && bValue instanceof Date) {
      if (currentSort.direction === 'asc') {
        return aValue - bValue;
      } else {
        return bValue - aValue;
      }
    }
    
    // Default comparison
    if (currentSort.direction === 'asc') {
      return aValue > bValue ? 1 : -1;
    } else {
      return aValue < bValue ? 1 : -1;
    }
  });
}

// Display tests in the table
function displayTests(tests) {
  const tableBody = document.getElementById('testLibraryTableBody');
  const testCount = document.getElementById('testCount');
  
  if (!tableBody || !testCount) return;
  
  // Clear table
  tableBody.innerHTML = '';
  
  // Calculate pagination
  const totalPages = Math.ceil(tests.length / pageSize);
  const startIndex = (currentPage - 1) * pageSize;
  const endIndex = Math.min(startIndex + pageSize, tests.length);
  const pageTests = tests.slice(startIndex, endIndex);
  
  // Update test count
  testCount.textContent = `Showing ${startIndex + 1}-${endIndex} of ${tests.length} tests`;
  
  // Add tests to table
  pageTests.forEach(test => {
    const row = document.createElement('tr');
    
    // Format status badge
    const statusClass = getStatusClass(test.status);
    const statusBadge = `<span class="test-status-badge ${statusClass}">${test.status}</span>`;
    
    // Format date
    const modifiedDate = test.modifiedAt ? new Date(test.modifiedAt).toLocaleString() : 
                        (test.createdAt ? new Date(test.createdAt).toLocaleString() : 'N/A');
    
    row.innerHTML = `
      <td>
        <div class="fw-semibold">${test.name || test.methodName}</div>
        <small class="text-muted">${test.packageName || ''}</small>
      </td>
      <td>${test.className || 'Unknown'}</td>
      <td>${test.type || 'Unit'}</td>
      <td>${statusBadge}</td>
      <td>${modifiedDate}</td>
      <td>
        <button class="btn btn-sm btn-outline-primary view-test-btn" data-test-id="${test.id}">
          <i class="bi bi-eye"></i>
        </button>
        <button class="btn btn-sm btn-outline-success run-test-btn" data-test-id="${test.id}">
          <i class="bi bi-play"></i>
        </button>
      </td>
    `;
    
    tableBody.appendChild(row);
  });
  
  // Update pagination
  updatePagination(currentPage, totalPages);
  
  // Hide skeleton and show content
  document.getElementById('testLibrarySkeleton').style.display = 'none';
  document.getElementById('testLibraryContent').style.display = 'block';
}

// Update pagination controls
function updatePagination(currentPage, totalPages) {
  const pagination = document.querySelector('.pagination');
  if (!pagination) return;
  
  // Clear pagination
  pagination.innerHTML = '';
  
  // Previous button
  const prevItem = document.createElement('li');
  prevItem.className = `page-item ${currentPage === 1 ? 'disabled' : ''}`;
  prevItem.innerHTML = `<a class="page-link" href="#" aria-label="Previous"><span aria-hidden="true">&laquo;</span></a>`;
  pagination.appendChild(prevItem);
  
  // Add page links
  const maxPages = 5;
  const startPage = Math.max(1, currentPage - Math.floor(maxPages / 2));
  const endPage = Math.min(totalPages, startPage + maxPages - 1);
  
  for (let i = startPage; i <= endPage; i++) {
    const pageItem = document.createElement('li');
    pageItem.className = `page-item ${i === currentPage ? 'active' : ''}`;
    pageItem.innerHTML = `<a class="page-link" href="#">${i}</a>`;
    
    // Add click event
    pageItem.addEventListener('click', function(event) {
      event.preventDefault();
      currentPage = i;
      filterSortAndDisplayTests();
    });
    
    pagination.appendChild(pageItem);
  }
  
  // Next button
  const nextItem = document.createElement('li');
  nextItem.className = `page-item ${currentPage === totalPages ? 'disabled' : ''}`;
  nextItem.innerHTML = `<a class="page-link" href="#" aria-label="Next"><span aria-hidden="true">&raquo;</span></a>`;
  pagination.appendChild(nextItem);
  
  // Add click events for previous and next
  prevItem.addEventListener('click', function(event) {
    event.preventDefault();
    if (currentPage > 1) {
      currentPage--;
      filterSortAndDisplayTests();
    }
  });
  
  nextItem.addEventListener('click', function(event) {
    event.preventDefault();
    if (currentPage < totalPages) {
      currentPage++;
      filterSortAndDisplayTests();
    }
  });
}

// Filter and display tests
function filterAndDisplayTests() {
  // Reset to first page
  currentPage = 1;
  
  // Filter, sort, and display
  filterSortAndDisplayTests();
}

// Sort and display tests
function sortAndDisplayTests() {
  // Filter, sort, and display
  filterSortAndDisplayTests();
}

// Update sort indicators
function updateSortIndicators() {
  // Remove active class from all headers
  document.querySelectorAll('.sortable').forEach(header => {
    header.classList.remove('sort-active');
    
    // Reset icons
    const icon = header.querySelector('.sort-icon');
    if (icon) {
      icon.className = 'bi bi-arrow-down-up sort-icon';
    }
  });
  
  // Add active class to current sort header
  const currentHeader = document.querySelector(`.sortable[data-sort="${currentSort.field}"]`);
  if (currentHeader) {
    currentHeader.classList.add('sort-active');
    
    // Update icon
    const icon = currentHeader.querySelector('.sort-icon');
    if (icon) {
      icon.className = `bi bi-arrow-${currentSort.direction === 'asc' ? 'up' : 'down'} sort-icon`;
    }
  }
}

// Reset filters
function resetFilters() {
  // Reset status filters
  document.querySelectorAll('.status-filter').forEach(checkbox => {
    checkbox.checked = true;
  });
  currentFilters.status = ['PASSED', 'FAILED', 'BROKEN', 'HEALED'];
  
  // Reset type filters
  document.querySelectorAll('.type-filter').forEach(checkbox => {
    checkbox.checked = true;
  });
  currentFilters.type = ['Unit', 'Integration', 'API', 'UI'];
  
  // Reset search
  const searchInput = document.getElementById('testSearchInput');
  if (searchInput) {
    searchInput.value = '';
  }
  currentFilters.search = '';
  
  // Reset to first page
  currentPage = 1;
  
  // Filter, sort, and display
  filterSortAndDisplayTests();
}

// Show test details
function showTestDetails(testId) {
  // Find test
  const test = allTests.find(t => t.id === testId);
  if (!test) return;
  
  const modalContent = document.getElementById('testDetailContent');
  const runTestBtn = document.getElementById('runTestBtn');
  const modal = new bootstrap.Modal(document.getElementById('testDetailModal'));
  
  if (!modalContent || !runTestBtn) return;
  
  // Format status badge
  const statusClass = getStatusClass(test.status);
  const statusBadge = `<span class="test-status-badge ${statusClass}">${test.status}</span>`;
  
  // Format dates
  const createdDate = test.createdAt ? new Date(test.createdAt).toLocaleString() : 'N/A';
  const modifiedDate = test.modifiedAt ? new Date(test.modifiedAt).toLocaleString() : 'N/A';
  
  // Get assertions
  const assertions = test.assertions || [];
  
  // Build content
  modalContent.innerHTML = `
    <div class="test-detail-header">
      <h5>${test.name || test.methodName}</h5>
      <div class="d-flex justify-content-between align-items-center mb-2">
        <span class="text-muted">${test.packageName}.${test.className}.${test.methodName}</span>
        ${statusBadge}
      </div>
      <div class="row">
        <div class="col-md-6">
          <small class="text-muted">Type: ${test.type || 'Unit'}</small>
        </div>
        <div class="col-md-6">
          <small class="text-muted">Priority: ${test.priority || 'MEDIUM'}</small>
        </div>
        <div class="col-md-6">
          <small class="text-muted">Created: ${createdDate}</small>
        </div>
        <div class="col-md-6">
          <small class="text-muted">Modified: ${modifiedDate}</small>
        </div>
      </div>
    </div>
    
    <h6>Source Code</h6>
    <pre class="code-block"><code>${test.sourceCode || 'No source code available'}</code></pre>
    
    ${assertions.length > 0 ? `
      <h6>Assertions</h6>
      <ul class="test-assertions">
        ${assertions.map(assertion => `<li>${assertion}</li>`).join('')}
      </ul>
    ` : ''}
    
    ${test.description ? `
      <h6>Description</h6>
      <p>${test.description}</p>
    ` : ''}
  `;
  
  // Set test ID on run button
  runTestBtn.setAttribute('data-test-id', test.id);
  
  // Show modal
  modal.show();
}

// Run a test
function runTest(testId) {
  // Find test
  const test = allTests.find(t => t.id === testId);
  if (!test) return;
  
  // Show loading
  const runButtons = document.querySelectorAll(`.run-test-btn[data-test-id="${testId}"]`);
  runButtons.forEach(button => {
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>';
  });
  
  // Call API to run test
  fetch(`${API_BASE_URL}/enhanced-tests/${testId}/execute`, {
    method: 'POST'
  })
    .then(response => response.json())
    .then(result => {
      // Update test in allTests
      const index = allTests.findIndex(t => t.id === testId);
      if (index >= 0) {
        allTests[index] = result;
      }
      
      // Refilter and display
      filterSortAndDisplayTests();
      
      // Show success message
      alert(`Test ${result.name || result.methodName} executed successfully with status: ${result.status}`);
    })
    .catch(error => {
      console.error('Error running test:', error);
      
      // Show error message
      alert('Failed to run test. Please try again later.');
    })
    .finally(() => {
      // Reset buttons
      runButtons.forEach(button => {
        button.disabled = false;
        button.innerHTML = '<i class="bi bi-play"></i>';
      });
    });
}

// Load broken tests
function loadBrokenTests() {
  // Show skeleton
  document.getElementById('brokenTestsSkeleton').style.display = 'block';
  document.getElementById('brokenTestsContent').style.display = 'none';
  
  // Hide analysis content
  document.getElementById('testAnalysisSkeleton').style.display = 'none';
  document.getElementById('testAnalysisContent').style.display = 'none';
  document.getElementById('noTestSelectedMessage').style.display = 'block';
  
  // Disable heal button
  document.getElementById('healSelectedBtn').disabled = true;
  
  // Fetch broken tests from API
  fetch(`${API_BASE_URL}/tests/status/BROKEN`)
    .then(response => response.json())
    .then(tests => {
      displayBrokenTests(tests);
    })
    .catch(error => {
      console.error('Error loading broken tests:', error);
      showErrorMessage('Failed to load broken tests');
      displayBrokenTests([]);
    });
}

// Display broken tests
function displayBrokenTests(tests) {
  const brokenTestsList = document.getElementById('brokenTestsList');
  const healAllBtn = document.getElementById('healAllBtn');
  
  if (!brokenTestsList || !healAllBtn) return;
  
  // Clear list
  brokenTestsList.innerHTML = '';
  
  // Disable heal all button if no broken tests
  healAllBtn.disabled = tests.length === 0;
  
  if (tests.length === 0) {
    brokenTestsList.innerHTML = `
      <div class="text-center p-4">
        <i class="bi bi-check-circle-fill text-success" style="font-size: 2rem;"></i>
        <p class="mt-2 mb-0">No broken tests found</p>
      </div>
    `;
  } else {
    // Add tests to list
    tests.forEach(test => {
      const item = document.createElement('div');
      item.className = 'broken-test-item border-bottom';
      item.setAttribute('data-test-id', test.id);
      
      // Create error type badge
      const errorType = getErrorType(test);
      
      item.innerHTML = `
        <div class="d-flex justify-content-between align-items-center mb-1">
          <h6 class="mb-0">${test.name || test.methodName}</h6>
          <span class="error-type">${errorType}</span>
        </div>
        <div class="text-muted small mb-1">${test.className}.${test.methodName}</div>
        <div class="text-danger small text-truncate">${test.failureMessage || 'Unknown error'}</div>
      `;
      
      brokenTestsList.appendChild(item);
    });
  }
  
  // Hide skeleton and show content
  document.getElementById('brokenTestsSkeleton').style.display = 'none';
  document.getElementById('brokenTestsContent').style.display = 'block';
}

// Select a broken test for analysis
function selectBrokenTest(testId) {
  // Find test
  const test = allTests.find(t => t.id === testId);
  if (!test) return;
  
  // Set selected test
  selectedBrokenTest = testId;
  
  // Update UI
  const brokenTestItems = document.querySelectorAll('.broken-test-item');
  brokenTestItems.forEach(item => {
    item.classList.remove('active');
  });
  
  const selectedItem = document.querySelector(`.broken-test-item[data-test-id="${testId}"]`);
  if (selectedItem) {
    selectedItem.classList.add('active');
  }
  
  // Enable heal button
  document.getElementById('healSelectedBtn').disabled = false;
  
  // Show analysis skeleton
  document.getElementById('testAnalysisSkeleton').style.display = 'block';
  document.getElementById('testAnalysisContent').style.display = 'none';
  document.getElementById('noTestSelectedMessage').style.display = 'none';
  
  // Fetch test analysis from API
  fetch(`${API_BASE_URL}/healing/analyze-test/${testId}`)
    .then(response => response.json())
    .then(analysis => {
      displayTestAnalysis(test, analysis);
    })
    .catch(error => {
      console.error('Error loading test analysis:', error);
      showErrorMessage('Failed to load test analysis');
    });
}

// Display test analysis
function displayTestAnalysis(test, analysis) {
  const analysisTestName = document.getElementById('analysisTestName');
  const analysisTestClass = document.getElementById('analysisTestClass');
  const analysisTestStatus = document.getElementById('analysisTestStatus');
  const errorDetails = document.getElementById('errorDetails');
  const errorMessage = document.getElementById('errorMessage');
  const errorStackTrace = document.getElementById('errorStackTrace');
  const detectedPatterns = document.getElementById('detectedPatterns');
  const patternsList = document.getElementById('patternsList');
  const healingOptions = document.getElementById('healingOptions');
  const suggestionsList = document.getElementById('suggestionsList');
  
  if (!analysisTestName || !analysisTestClass || !analysisTestStatus) return;
  
  // Update test info
  analysisTestName.textContent = test.name || test.methodName;
  analysisTestClass.textContent = `${test.packageName}.${test.className}.${test.methodName}`;
  
  // Status badge
  const statusBadge = `<span class="test-status-badge test-status-broken">BROKEN</span>`;
  analysisTestStatus.innerHTML = statusBadge;
  
  // Error details
  if (test.failureMessage || test.stackTrace) {
    errorMessage.textContent = test.failureMessage || 'Unknown error';
    errorStackTrace.textContent = test.stackTrace || 'No stack trace available';
    errorDetails.style.display = 'block';
  } else {
    errorDetails.style.display = 'none';
  }
  
  // Detected patterns
  if (analysis.detectedPatterns && analysis.detectedPatterns.length > 0) {
    patternsList.innerHTML = '';
    
    analysis.detectedPatterns.forEach(pattern => {
      const patternItem = document.createElement('div');
      patternItem.className = `pattern-item pattern-${getPatternSeverity(pattern.confidenceScore)}`;
      
      patternItem.innerHTML = `
        <h6>
          <i class="bi bi-exclamation-triangle"></i>
          ${pattern.patternType}
          <span class="pattern-confidence">${Math.round(pattern.confidenceScore * 100)}% confidence</span>
        </h6>
        <p>${pattern.description}</p>
        <div class="code-snippet">
          <pre><code>${pattern.errorSignature}</code></pre>
        </div>
      `;
      
      patternsList.appendChild(patternItem);
    });
    
    detectedPatterns.style.display = 'block';
  } else {
    detectedPatterns.style.display = 'none';
  }
  
  // Healing suggestions
  if (analysis.suggestedFixes && analysis.suggestedFixes.length > 0) {
    suggestionsList.innerHTML = '';
    
    analysis.suggestedFixes.forEach((suggestion, index) => {
      const suggestionItem = document.createElement('div');
      suggestionItem.className = 'suggestion-item';
      
      suggestionItem.innerHTML = `
        <div class="d-flex align-items-center">
          <span class="badge bg-light text-dark me-2">${index + 1}</span>
          <span>${suggestion}</span>
        </div>
      `;
      
      suggestionsList.appendChild(suggestionItem);
    });
    
    healingOptions.style.display = 'block';
  } else {
    healingOptions.style.display = 'none';
  }
  
  // Hide skeleton and show content
  document.getElementById('testAnalysisSkeleton').style.display = 'none';
  document.getElementById('testAnalysisContent').style.display = 'block';
}

// Heal a test
function healTest(testId) {
  // Show loading
  const healButtons = document.querySelectorAll(`button[data-test-id="${testId}"]`);
  healButtons.forEach(button => {
    button.disabled = true;
    button.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Healing...';
  });
  
  // Disable heal selected button
  document.getElementById('healSelectedBtn').disabled = true;
  document.getElementById('healSelectedBtn').innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Healing...';
  
  // Call API to heal test
  fetch(`${API_BASE_URL}/healing/test/${testId}`, {
    method: 'POST'
  })
    .then(response => {
      if (response.ok) {
        // Show success message
        alert('Test healing has been initiated. The test will be updated shortly.');
        
        // Refresh broken tests after a delay
        setTimeout(() => {
          loadBrokenTests();
          loadTestLibrary();
        }, 1000);
      } else {
        throw new Error('Failed to heal test');
      }
    })
    .catch(error => {
      console.error('Error healing test:', error);
      
      // Show error message
      alert('Failed to heal test. Please try again later.');
      
      // Reset buttons
      healButtons.forEach(button => {
        button.disabled = false;
        button.innerHTML = '<i class="bi bi-heart-pulse"></i> Heal';
      });
      
      document.getElementById('healSelectedBtn').disabled = false;
      document.getElementById('healSelectedBtn').innerHTML = '<i class="bi bi-heart-pulse"></i> Heal Selected Test';
    });
}

// Heal all broken tests
function healAllTests() {
  // Show loading
  const healAllBtn = document.getElementById('healAllBtn');
  if (!healAllBtn) return;
  
  healAllBtn.disabled = true;
  healAllBtn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Healing All Tests...';
  
  // Call API to heal all tests
  fetch(`${API_BASE_URL}/healing/all`, {
    method: 'POST'
  })
    .then(response => {
      if (response.ok) {
        // Show success message
        alert('Test healing has been initiated for all broken tests. Tests will be updated shortly.');
        
        // Refresh broken tests after a delay
        setTimeout(() => {
          loadBrokenTests();
          loadTestLibrary();
        }, 1000);
      } else {
        throw new Error('Failed to heal tests');
      }
    })
    .catch(error => {
      console.error('Error healing all tests:', error);
      
      // Show error message
      alert('Failed to heal all tests. Please try again later.');
      
      // Reset button
      healAllBtn.disabled = false;
      healAllBtn.innerHTML = '<i class="bi bi-heart-pulse"></i> Heal All Broken Tests';
    });
}

// Load priority configuration
function loadPriorityConfig() {
  // Show skeleton
  document.getElementById('priorityConfigSkeleton').style.display = 'block';
  document.getElementById('priorityConfigContent').style.display = 'none';
  
  // Fetch config from API
  fetch(`${API_BASE_URL}/priorities/config`)
    .then(response => response.json())
    .then(config => {
      priorityConfig = config;
      updatePriorityConfigUI(config);
    })
    .catch(error => {
      console.error('Error loading priority config:', error);
      showErrorMessage('Failed to load priority configuration');
    });
}

// Update priority configuration UI
function updatePriorityConfigUI(config) {
  // Update form fields
  document.getElementById('enablePrioritization').checked = config.enabled;
  document.getElementById('failureWeight').value = config.failureWeight;
  document.getElementById('failureWeightValue').textContent = config.failureWeight;
  document.getElementById('changeCorrelationWeight').value = config.changeCorrelationWeight;
  document.getElementById('changeCorrelationWeightValue').textContent = config.changeCorrelationWeight;
  document.getElementById('executionTimeWeight').value = config.executionTimeWeight;
  document.getElementById('executionTimeWeightValue').textContent = config.executionTimeWeight;
  document.getElementById('coverageWeight').value = config.coverageWeight;
  document.getElementById('coverageWeightValue').textContent = config.coverageWeight;
  document.getElementById('prioritizeFlaky').checked = config.prioritizeFlaky;
  document.getElementById('prioritizeNew').checked = config.prioritizeNew;
  document.getElementById('maxFastTrackTests').value = config.maxFastTrackTests;
  
  // Hide skeleton and show content
  document.getElementById('priorityConfigSkeleton').style.display = 'none';
  document.getElementById('priorityConfigContent').style.display = 'block';
}

// Save priority configuration
function savePriorityConfig() {
  // Get values from form
  const config = {
    enabled: document.getElementById('enablePrioritization').checked,
    failureWeight: parseInt(document.getElementById('failureWeight').value, 10),
    changeCorrelationWeight: parseInt(document.getElementById('changeCorrelationWeight').value, 10),
    executionTimeWeight: parseInt(document.getElementById('executionTimeWeight').value, 10),
    coverageWeight: parseInt(document.getElementById('coverageWeight').value, 10),
    prioritizeFlaky: document.getElementById('prioritizeFlaky').checked,
    prioritizeNew: document.getElementById('prioritizeNew').checked,
    maxFastTrackTests: parseInt(document.getElementById('maxFastTrackTests').value, 10)
  };
  
  // Show loading
  const saveBtn = document.getElementById('savePriorityConfigBtn');
  if (!saveBtn) return;
  
  saveBtn.disabled = true;
  saveBtn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Saving...';
  
  // Call API to save config
  fetch(`${API_BASE_URL}/priorities/config`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(config)
  })
    .then(response => response.json())
    .then(updatedConfig => {
      // Update config
      priorityConfig = updatedConfig;
      
      // Show success message
      alert('Priority configuration saved successfully.');
      
      // Refresh prioritized tests
      loadPrioritizedTests();
    })
    .catch(error => {
      console.error('Error saving priority config:', error);
      
      // Show error message
      alert('Failed to save priority configuration. Please try again later.');
    })
    .finally(() => {
      // Reset button
      saveBtn.disabled = false;
      saveBtn.innerHTML = 'Save Configuration';
    });
}

// Load prioritized tests
function loadPrioritizedTests(context = 'all') {
  // Show skeleton
  document.getElementById('prioritizedTestsSkeleton').style.display = 'block';
  document.getElementById('prioritizedTestsContent').style.display = 'none';
  
  // Build API URL based on context
  let apiUrl = `${API_BASE_URL}/priorities/execution`;
  if (context === 'changed') {
    apiUrl += '?changedOnly=true';
  } else if (context === 'failed') {
    apiUrl += '?failedOnly=true';
  }
  
  // Fetch prioritized tests from API
  fetch(apiUrl)
    .then(response => response.json())
    .then(tests => {
      displayPrioritizedTests(tests);
    })
    .catch(error => {
      console.error('Error loading prioritized tests:', error);
      showErrorMessage('Failed to load prioritized tests');
      displayPrioritizedTests([]);
    });
}

// Display prioritized tests
function displayPrioritizedTests(tests) {
  const tableBody = document.getElementById('prioritizedTestsTableBody');
  const testCount = document.getElementById('prioritizedTestCount');
  const runBtn = document.getElementById('runPrioritizedBtn');
  
  if (!tableBody || !testCount || !runBtn) return;
  
  // Clear table
  tableBody.innerHTML = '';
  
  // Update test count
  testCount.textContent = `Showing ${tests.length} tests`;
  
  // Disable run button if no tests
  runBtn.disabled = tests.length === 0;
  
  // Add tests to table
  tests.forEach((test, index) => {
    const row = document.createElement('tr');
    
    // Determine priority level
    let priorityLevel = 'low';
    if (test.priorityScore >= 75) {
      priorityLevel = 'high';
    } else if (test.priorityScore >= 40) {
      priorityLevel = 'medium';
    }
    
    // Create priority indicator
    const priorityIndicator = `<div class="priority-indicator priority-${priorityLevel}">${index + 1}</div>`;
    
    // Create priority factors
    const factors = [];
    if (test.priorityFactors.failureHistory) {
      factors.push('<span class="priority-factor factor-failure">Recent Failures</span>');
    }
    if (test.priorityFactors.codeChangeCorrelation) {
      factors.push('<span class="priority-factor factor-change">Code Changes</span>');
    }
    if (test.priorityFactors.coverage) {
      factors.push('<span class="priority-factor factor-coverage">Coverage</span>');
    }
    if (test.priorityFactors.executionTime) {
      factors.push('<span class="priority-factor factor-time">Fast Execution</span>');
    }
    if (test.priorityFactors.isNew) {
      factors.push('<span class="priority-factor factor-new">New Test</span>');
    }
    
    row.innerHTML = `
      <td class="text-center">
        ${priorityIndicator}
      </td>
      <td>
        <div class="fw-semibold">${test.name || test.methodName}</div>
        <small class="text-muted">${test.className}</small>
      </td>
      <td class="small">
        ${factors.join(' ')}
      </td>
      <td>
        <span class="priority-score">${test.priorityScore}</span>
      </td>
      <td>
        <button class="btn btn-sm btn-outline-primary run-test-btn" data-test-id="${test.id}">
          <i class="bi bi-play"></i> Run
        </button>
        <button class="btn btn-sm btn-outline-secondary view-test-btn" data-test-id="${test.id}">
          <i class="bi bi-eye"></i> View
        </button>
      </td>
    `;
    
    tableBody.appendChild(row);
  });
  
  // Hide skeleton and show content
  document.getElementById('prioritizedTestsSkeleton').style.display = 'none';
  document.getElementById('prioritizedTestsContent').style.display = 'block';
}

// Run prioritized tests
function runPrioritizedTests() {
  // Get context
  const context = document.getElementById('prioritizationContext').value;
  
  // Show loading
  const runBtn = document.getElementById('runPrioritizedBtn');
  if (!runBtn) return;
  
  runBtn.disabled = true;
  runBtn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Running...';
  
  // Build API URL based on context
  let apiUrl = `${API_BASE_URL}/priorities/execution/run`;
  if (context === 'changed') {
    apiUrl += '?changedOnly=true';
  } else if (context === 'failed') {
    apiUrl += '?failedOnly=true';
  }
  
  // Call API to run tests
  fetch(apiUrl, {
    method: 'POST'
  })
    .then(response => {
      if (response.ok) {
        // Show success message
        alert('Tests are being executed in prioritized order. Results will be available shortly.');
        
        // Refresh data after a delay
        setTimeout(refreshData, 2000);
      } else {
        throw new Error('Failed to run tests');
      }
    })
    .catch(error => {
      console.error('Error running prioritized tests:', error);
      
      // Show error message
      alert('Failed to run tests. Please try again later.');
    })
    .finally(() => {
      // Reset button
      runBtn.disabled = false;
      runBtn.innerHTML = '<i class="bi bi-play"></i> Run in Prioritized Order';
    });
}

// Refresh all data
function refreshData() {
  loadTestLibrary();
  loadBrokenTests();
  loadPrioritizedTests(document.getElementById('prioritizationContext').value);
}

// Utility functions
function getStatusClass(status) {
  switch (status) {
    case 'PASSED':
      return 'test-status-passed';
    case 'FAILED':
      return 'test-status-failed';
    case 'BROKEN':
      return 'test-status-broken';
    case 'HEALED':
      return 'test-status-healed';
    default:
      return '';
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

function getErrorType(test) {
  if (!test.failureMessage) return 'Unknown';
  
  if (test.failureMessage.includes('NullPointerException')) {
    return 'NullPointerException';
  } else if (test.failureMessage.includes('AssertionError')) {
    return 'AssertionError';
  } else if (test.failureMessage.includes('IndexOutOfBoundsException')) {
    return 'IndexOutOfBoundsException';
  } else if (test.failureMessage.includes('ClassCastException')) {
    return 'ClassCastException';
  } else if (test.failureMessage.includes('NoSuchElementException')) {
    return 'NoSuchElementException';
  } else {
    // Extract first part of error message
    const parts = test.failureMessage.split(':');
    return parts[0];
  }
}

function getPatternSeverity(confidence) {
  if (confidence >= 0.8) {
    return 'high';
  } else if (confidence >= 0.5) {
    return 'medium';
  } else {
    return 'low';
  }
}

// Mock data functions
