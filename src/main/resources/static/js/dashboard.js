// Global variables
let currentPeriod = 'day';
let charts = {};
let dataCache = {
    day: { stats: null, chartData: null },
    week: { stats: null, chartData: null },
    month: { stats: null, chartData: null },
    ytd: { stats: null, chartData: null }
};
let isInitialLoad = true;

// Initialize dashboard on page load
document.addEventListener('DOMContentLoaded', () => {
    initializeTabs();
    initializePeriodSelector();
    loadAllData();
});

// Initialize tab switching
function initializeTabs() {
    const tabButtons = document.querySelectorAll('.tab-button');
    tabButtons.forEach(button => {
        button.addEventListener('click', () => {
            const targetTab = button.dataset.tab;
            switchTab(targetTab);
        });
    });
}

// Switch between chart tabs
function switchTab(tabName) {
    // Update button states
    document.querySelectorAll('.tab-button').forEach(btn => {
        btn.classList.remove('active');
        if (btn.dataset.tab === tabName) {
            btn.classList.add('active');
        }
    });
    
    // Update content visibility
    document.querySelectorAll('.tab-content').forEach(content => {
        content.classList.remove('active');
    });
    document.getElementById(`${tabName}Tab`).classList.add('active');
}

// Initialize period selector
function initializePeriodSelector() {
    const periodSelect = document.getElementById('periodSelect');
    periodSelect.addEventListener('change', (e) => {
        currentPeriod = e.target.value;
        displayCachedData();
    });
}

// Load all data for all periods on initial load
async function loadAllData() {
    showLoading();
    
    try {
        // Fetch data for all periods in parallel
        const periods = ['day', 'week', 'month', 'ytd'];
        const promises = [];
        
        for (const period of periods) {
            promises.push(
                axios.get(`/api/water/stats/${period}`),
                axios.get(`/api/water/chart/${period}`)
            );
        }
        
        const responses = await Promise.all(promises);
        
        // Store data in cache
        let responseIndex = 0;
        for (const period of periods) {
            dataCache[period].stats = responses[responseIndex].data;
            dataCache[period].chartData = responses[responseIndex + 1].data;
            responseIndex += 2;
        }
        
        // Display the current period data
        displayCachedData();
        
    } catch (error) {
        console.error('Error fetching data:', error);
        showError('Failed to load water usage data. Please ensure the backend is running.');
    }
}

// Display data from cache for current period
function displayCachedData() {
    const cached = dataCache[currentPeriod];
    
    if (!cached.stats || !cached.chartData) {
        console.error('No cached data for period:', currentPeriod);
        return;
    }
    
    // Update the dashboard with cached data
    updateDashboard(cached.stats);
    updateCharts(cached.chartData);
    
    if (isInitialLoad) {
        showDashboard();
        isInitialLoad = false;
    }
}

// Refresh data for all periods (called by timer)
async function refreshAllData() {
    try {
        const periods = ['day', 'week', 'month', 'ytd'];
        const promises = [];
        
        for (const period of periods) {
            promises.push(
                axios.get(`/api/water/stats/${period}`),
                axios.get(`/api/water/chart/${period}`)
            );
        }
        
        const responses = await Promise.all(promises);
        
        // Update cache
        let responseIndex = 0;
        for (const period of periods) {
            dataCache[period].stats = responses[responseIndex].data;
            dataCache[period].chartData = responses[responseIndex + 1].data;
            responseIndex += 2;
        }
        
        // Update display if we're looking at the current period
        displayCachedData();
        
    } catch (error) {
        console.error('Error refreshing data:', error);
    }
}

// Update dashboard statistics
function updateDashboard(stats) {
    // Update stat cards
    document.getElementById('totalUsage').textContent = formatGallons(stats.totalGallons) + ' gal';
    document.getElementById('dailyAverage').textContent = formatGallons(stats.averageDailyUsage) + ' gal';
    document.getElementById('peakUsage').textContent = formatGallons(stats.peakDailyUsage) + ' gal';
    document.getElementById('currentReading').textContent = formatGallons(stats.currentReading);
    
    // Update usage change indicator
    const changeElement = document.getElementById('usageChange');
    if (stats.percentageChange !== 0) {
        const isPositive = stats.percentageChange > 0;
        const arrow = isPositive ? '↑' : '↓';
        const prefix = isPositive ? '+' : '';
        changeElement.textContent = `${arrow} ${prefix}${stats.percentageChange.toFixed(1)}% vs previous period`;
        changeElement.className = isPositive ? 'stat-change positive' : 'stat-change negative';
        changeElement.style.display = 'flex';
    } else {
        changeElement.style.display = 'none';
    }
    
    // Update period summary
    document.getElementById('summaryPeriod').textContent = stats.period.charAt(0).toUpperCase() + stats.period.slice(1);
    document.getElementById('summaryStartDate').textContent = formatDate(stats.startDate);
    document.getElementById('summaryEndDate').textContent = formatDate(stats.endDate);
    document.getElementById('summaryLowestUsage').textContent = formatGallons(stats.lowestDailyUsage) + ' gal';
}

// Update charts with new data
function updateCharts(chartData) {
    const dataPoints = chartData.dataPoints || [];
    
    // Destroy existing charts
    Object.values(charts).forEach(chart => {
        if (chart) chart.destroy();
    });
    
    // Check if mobile device
    const isMobile = window.innerWidth <= 768;
    const isSmallMobile = window.innerWidth <= 480;
    
    // Adjust data points for mobile to improve performance and readability
    let displayPoints = dataPoints;
    if (isMobile && dataPoints.length > 50) {
        // Sample data points for mobile
        const step = Math.ceil(dataPoints.length / 50);
        displayPoints = dataPoints.filter((_, index) => index % step === 0);
    }
    
    // Common chart options
    const commonOptions = {
        responsive: true,
        maintainAspectRatio: false,
        interaction: {
            mode: 'nearest',
            axis: 'x',
            intersect: false
        },
        plugins: {
            legend: {
                display: false
            },
            tooltip: {
                backgroundColor: 'rgba(0, 0, 0, 0.8)',
                padding: isMobile ? 8 : 12,
                cornerRadius: 8,
                titleFont: {
                    size: isMobile ? 11 : 13
                },
                bodyFont: {
                    size: isMobile ? 11 : 13
                },
                callbacks: {
                    label: function(context) {
                        return formatGallons(context.parsed.y) + ' gal';
                    }
                }
            }
        },
        scales: {
            x: {
                grid: {
                    display: false
                },
                ticks: {
                    maxRotation: isSmallMobile ? 90 : 45,
                    minRotation: 0,
                    autoSkip: true,
                    maxTicksLimit: isMobile ? 6 : 12,
                    font: {
                        size: isMobile ? 10 : 12
                    }
                }
            },
            y: {
                grid: {
                    borderDash: [3, 3],
                    color: 'rgba(0, 0, 0, 0.1)'
                },
                ticks: {
                    callback: function(value) {
                        return formatGallons(value);
                    },
                    font: {
                        size: isMobile ? 10 : 12
                    },
                    maxTicksLimit: isMobile ? 5 : 8
                }
            }
        }
    };
    
    // Usage Over Time Chart (Area)
    const usageCtx = document.getElementById('usageChart').getContext('2d');
    charts.usage = new Chart(usageCtx, {
        type: 'line',
        data: {
            labels: displayPoints.map(d => d.label),
            datasets: [{
                label: 'Usage',
                data: displayPoints.map(d => d.usage),
                fill: true,
                backgroundColor: 'rgba(59, 130, 246, 0.1)',
                borderColor: 'rgb(59, 130, 246)',
                borderWidth: isMobile ? 1.5 : 2,
                tension: 0.3,
                pointRadius: currentPeriod === 'day' && !isMobile ? 3 : 0,
                pointHoverRadius: isMobile ? 4 : 6,
                pointHitRadius: isMobile ? 10 : 8
            }]
        },
        options: commonOptions
    });
    
    // Cumulative Usage Chart (Line)
    const cumulativeCtx = document.getElementById('cumulativeChart').getContext('2d');
    charts.cumulative = new Chart(cumulativeCtx, {
        type: 'line',
        data: {
            labels: displayPoints.map(d => d.label),
            datasets: [{
                label: 'Total',
                data: displayPoints.map(d => d.value),
                fill: false,
                borderColor: 'rgb(16, 185, 129)',
                borderWidth: isMobile ? 1.5 : 2,
                tension: 0.1,
                pointRadius: 0,
                pointHoverRadius: isMobile ? 4 : 6,
                pointHitRadius: isMobile ? 10 : 8
            }]
        },
        options: commonOptions
    });
    
    // Consumption Rate Chart (Bar)
    const hourlyCtx = document.getElementById('hourlyChart').getContext('2d');
    charts.hourly = new Chart(hourlyCtx, {
        type: 'bar',
        data: {
            labels: displayPoints.map(d => d.label),
            datasets: [{
                label: 'Usage',
                data: displayPoints.map(d => d.usage),
                backgroundColor: 'rgba(245, 158, 11, 0.8)',
                borderColor: 'rgb(245, 158, 11)',
                borderWidth: 1,
                borderRadius: isMobile ? 2 : 4
            }]
        },
        options: {
            ...commonOptions,
            scales: {
                ...commonOptions.scales,
                y: {
                    ...commonOptions.scales.y,
                    beginAtZero: true
                }
            }
        }
    });
}

// Utility functions
function formatGallons(value) {
    if (value === null || value === undefined) return '0';
    return new Intl.NumberFormat('en-US', {
        maximumFractionDigits: 1,
        minimumFractionDigits: 0
    }).format(value);
}

function formatDate(dateString) {
    if (!dateString) return 'N/A';
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', { 
        month: 'short', 
        day: 'numeric', 
        year: 'numeric' 
    });
}

// UI state management
function showLoading() {
    document.getElementById('loadingState').style.display = 'flex';
    document.getElementById('errorState').style.display = 'none';
    document.getElementById('dashboard').style.display = 'none';
}

function showError(message) {
    document.getElementById('loadingState').style.display = 'none';
    document.getElementById('errorState').style.display = 'flex';
    document.getElementById('dashboard').style.display = 'none';
    document.getElementById('errorMessage').textContent = message;
}

function showDashboard() {
    document.getElementById('loadingState').style.display = 'none';
    document.getElementById('errorState').style.display = 'none';
    document.getElementById('dashboard').style.display = 'block';
}

// Handle window resize and orientation change
let resizeTimeout;
window.addEventListener('resize', () => {
    clearTimeout(resizeTimeout);
    resizeTimeout = setTimeout(() => {
        // Redraw charts with new responsive settings
        if (dataCache[currentPeriod].chartData) {
            updateCharts(dataCache[currentPeriod].chartData);
        }
    }, 250);
});

// Handle orientation change specifically
window.addEventListener('orientationchange', () => {
    setTimeout(() => {
        if (dataCache[currentPeriod].chartData) {
            updateCharts(dataCache[currentPeriod].chartData);
        }
    }, 300);
});

// Email functionality
async function sendEmailSummary() {
    const emailButton = document.getElementById('emailButton');
    const modal = document.getElementById('emailModal');
    const modalTitle = document.getElementById('modalTitle');
    const modalMessage = document.getElementById('modalMessage');
    const modalSpinner = document.getElementById('modalSpinner');
    const modalBody = modal.querySelector('.modal-body');
    
    // Disable button and show modal
    emailButton.disabled = true;
    modal.classList.add('show');
    modalTitle.textContent = 'Sending Email...';
    modalMessage.textContent = `Sending ${currentPeriod} summary to your email...`;
    modalSpinner.style.display = 'block';
    modalBody.classList.remove('success', 'error');
    
    try {
        const response = await axios.post(`/api/water/email/${currentPeriod}`);
        
        if (response.data.status === 'success') {
            // Success state
            modalTitle.textContent = 'Email Sent!';
            modalMessage.innerHTML = `
                <div style="color: var(--success-color); margin-bottom: 1rem;">
                    <svg style="width: 48px; height: 48px; margin-bottom: 0.5rem;" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z"/>
                    </svg>
                </div>
                Your water usage summary for the <strong>${currentPeriod}</strong> period has been sent successfully!
            `;
            modalSpinner.style.display = 'none';
            modalBody.classList.add('success');
            
            // Auto-close after 3 seconds
            setTimeout(() => {
                closeEmailModal();
            }, 3000);
        } else {
            throw new Error(response.data.message || 'Failed to send email');
        }
    } catch (error) {
        console.error('Error sending email:', error);
        
        // Error state
        modalTitle.textContent = 'Email Failed';
        modalMessage.innerHTML = `
            <div style="color: var(--danger-color); margin-bottom: 1rem;">
                <svg style="width: 48px; height: 48px; margin-bottom: 0.5rem;" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
                </svg>
            </div>
            ${error.response?.data?.message || error.message || 'Failed to send email. Please try again later.'}
        `;
        modalSpinner.style.display = 'none';
        modalBody.classList.add('error');
    } finally {
        // Re-enable button after a delay
        setTimeout(() => {
            emailButton.disabled = false;
        }, 1000);
    }
}

function closeEmailModal() {
    const modal = document.getElementById('emailModal');
    modal.classList.remove('show');
    
    // Reset button state
    const emailButton = document.getElementById('emailButton');
    emailButton.disabled = false;
}

// Close modal when clicking outside
window.addEventListener('click', (event) => {
    const modal = document.getElementById('emailModal');
    if (event.target === modal) {
        closeEmailModal();
    }
});

// Auto-refresh data every 5 minutes (but keep it cached)
setInterval(() => {
    refreshAllData();
}, 5 * 60 * 1000);