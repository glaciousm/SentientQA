// code-scanner.js - Functions for scanning code and generating real test data

// API Base URL
const API_BASE_URL = '/api/v1';

// Create a modal for scanning code 
function createScanCodeModal() {
  // Create modal HTML
  const modalHtml = `
    <div class="modal fade" id="scanCodeModal" tabindex="-1" aria-labelledby="scanCodeModalLabel" aria-hidden="true">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title" id="scanCodeModalLabel">Scan Code & Generate Tests</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <form id="scanCodeForm">
              <div class="mb-3">
                <label for="sourcePath" class="form-label">Source Path</label>
                <input type="text" class="form-control" id="sourcePath" 
                  placeholder="Enter path to Java file or directory" required>
                <div class="form-text">Path to a Java file or directory containing Java files</div>
              </div>
              <div class="mb-3">
                <label for="maxTests" class="form-label">Maximum Tests</label>
                <input type="number" class="form-control" id="maxTests" min="1" max="50" value="10">
                <div class="form-text">Maximum number of tests to generate</div>
              </div>
            </form>
            <div id="scanResults" class="mt-3" style="display: none;"></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
            <button type="button" class="btn btn-primary" id="scanCodeBtn">Scan & Generate Tests</button>
          </div>
        </div>
      </div>
    </div>
  `;
  
  // Add modal to document
  document.body.insertAdjacentHTML('beforeend', modalHtml);
  
  // Initialize modal
  const modal = new bootstrap.Modal(document.getElementById('scanCodeModal'));
  
  // Add scan button to navbar
  const navbarNav = document.querySelector('.navbar-nav.me-auto');
  if (navbarNav) {
    const scanCodeItem = document.createElement('li');
    scanCodeItem.className = 'nav-item';
    scanCodeItem.innerHTML = `
      <a class="nav-link" href="#" id="scanCodeLink">
        <i class="bi bi-search"></i> Scan Code
      </a>
    `;
    navbarNav.appendChild(scanCodeItem);
    
    // Add event listener to the link
    document.getElementById('scanCodeLink').addEventListener('click', function() {
      modal.show();
    });
  }
  
  // Add event listener to scan button
  document.getElementById('scanCodeBtn').addEventListener('click', function() {
    const sourcePath = document.getElementById('sourcePath').value;
    const maxTests = document.getElementById('maxTests').value;
    
    if (!sourcePath) {
      alert('Please enter a source path');
      return;
    }
    
    // Show loading indicator
    const scanResults = document.getElementById('scanResults');
    scanResults.style.display = 'block';
    scanResults.innerHTML = `
      <div class="d-flex justify-content-center">
        <div class="spinner-border text-primary" role="status">
          <span class="visually-hidden">Loading...</span>
        </div>
      </div>
      <p class="text-center mt-2">Scanning code and generating tests...</p>
      <p class="text-center text-muted small">This may take a few moments</p>
    `;
    
    // Enable real data mode
    localStorage.setItem('useRealData', 'true');
    
    // Call the API to scan code
    fetch(`${API_BASE_URL}/tests/scan?sourcePath=${encodeURIComponent(sourcePath)}&maxTests=${maxTests}`, {
      method: 'POST'
    })
    .then(response => {
      if (!response.ok) {
        throw new Error(`API returned ${response.status}`);
      }
      return response.json();
    })
    .then(data => {
      // Update results
      if (data.length === 0) {
        scanResults.innerHTML = `
          <div class="alert alert-warning">
            No tests were generated. Please check the path and try again.
          </div>
        `;
      } else {
        scanResults.innerHTML = `
          <div class="alert alert-success">
            Successfully generated ${data.length} tests! 
            <button type="button" class="btn btn-sm btn-primary float-end" onclick="refreshDashboard()">
              Refresh Dashboard
            </button>
          </div>
          <div class="mt-3">
            <h6>Generated Tests:</h6>
            <ul class="list-group">
              ${data.map(test => `
                <li class="list-group-item">
                  <strong>${test.name}</strong>
                  <small class="d-block text-muted">${test.className}</small>
                </li>
              `).join('')}
            </ul>
          </div>
        `;
      }
    })
    .catch(error => {
      console.error('Error scanning code:', error);
      scanResults.innerHTML = `
        <div class="alert alert-danger">
          Error scanning code: ${error.message}
        </div>
      `;
    });
  });
}

// Initialize when the document is loaded
document.addEventListener('DOMContentLoaded', function() {
  createScanCodeModal();
});