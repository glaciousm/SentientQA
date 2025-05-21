// real-data-toggle.js - Toggle between real data and mock data

// Function to toggle between real data and mock data
function toggleRealDataMode() {
  const currentMode = localStorage.getItem('useRealData') === 'true';
  const newMode = !currentMode;
  
  localStorage.setItem('useRealData', newMode.toString());
  localStorage.removeItem('showedRealDataNotification');
  
  // Update toggle button state
  updateRealDataToggleButton();
  
  // Show notification about mode change
  if (newMode) {
    showNotification('Real data mode enabled. Refreshing dashboard...');
  } else {
    showNotification('Mock data mode enabled. Refreshing dashboard...', 'info');
  }
  
  // Refresh the dashboard to apply the new mode
  setTimeout(refreshDashboard, 1000);
}

// Function to update the toggle button state
function updateRealDataToggleButton() {
  const useRealData = localStorage.getItem('useRealData') === 'true';
  const toggleButton = document.getElementById('realDataToggle');
  
  if (toggleButton) {
    toggleButton.checked = useRealData;
    
    const toggleLabel = document.getElementById('realDataModeLabel');
    if (toggleLabel) {
      toggleLabel.textContent = useRealData ? 'Using Real Data' : 'Using Mock Data';
    }
  }
}

// Function to show a notification
function showNotification(message, type = 'success') {
  // Create toast container if it doesn't exist
  let toastContainer = document.getElementById('toastContainer');
  
  if (!toastContainer) {
    toastContainer = document.createElement('div');
    toastContainer.id = 'toastContainer';
    toastContainer.className = 'toast-container position-fixed bottom-0 end-0 p-3';
    toastContainer.style.zIndex = '1070';
    document.body.appendChild(toastContainer);
  }
  
  // Create a unique ID for this toast
  const toastId = 'toast-' + new Date().getTime();
  
  // Create the toast
  const toastHtml = `
    <div id="${toastId}" class="toast" role="alert" aria-live="assertive" aria-atomic="true">
      <div class="toast-header bg-${type} text-white">
        <strong class="me-auto">${type === 'success' ? 'Success' : type === 'warning' ? 'Warning' : 'Information'}</strong>
        <small>just now</small>
        <button type="button" class="btn-close btn-close-white" data-bs-dismiss="toast" aria-label="Close"></button>
      </div>
      <div class="toast-body">
        ${message}
      </div>
    </div>
  `;
  
  // Add the toast to the container
  toastContainer.insertAdjacentHTML('beforeend', toastHtml);
  
  // Initialize and show the toast
  const toastElement = document.getElementById(toastId);
  const toast = new bootstrap.Toast(toastElement, { autohide: true, delay: 5000 });
  toast.show();
  
  // Remove the toast element after it's hidden
  toastElement.addEventListener('hidden.bs.toast', function() {
    toastElement.remove();
  });
}

// Initialize real data mode on page load
document.addEventListener('DOMContentLoaded', function() {
  // Set default to mock data if not already set
  if (localStorage.getItem('useRealData') === null) {
    localStorage.setItem('useRealData', 'false');
  }
  
  // Create a toggle switch in the navbar
  const navbarNav = document.querySelector('.navbar-nav');
  if (navbarNav) {
    const toggleContainer = document.createElement('li');
    toggleContainer.className = 'nav-item ms-3 d-flex align-items-center';
    toggleContainer.innerHTML = `
      <div class="form-check form-switch">
        <input class="form-check-input" type="checkbox" role="switch" id="realDataToggle">
        <label class="form-check-label" for="realDataToggle" id="realDataModeLabel">Using Mock Data</label>
      </div>
    `;
    navbarNav.appendChild(toggleContainer);
    
    // Add event listener to the toggle
    const toggleButton = document.getElementById('realDataToggle');
    if (toggleButton) {
      toggleButton.addEventListener('change', toggleRealDataMode);
      
      // Set initial state
      updateRealDataToggleButton();
    }
  }
});