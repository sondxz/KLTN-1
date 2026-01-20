var contentChart = null;
var userChart = null;

async function loadDashboard() {
    try {
        const token = localStorage.getItem("token");
        const response = await fetch('/api/admin/statistics', {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        });

        if (!response.ok) {
            throw new Error('Failed to load dashboard data');
        }

        const stats = await response.json();
        displayDashboard(stats);
    } catch (error) {
        console.error('Error loading dashboard:', error);
        toastr.error('Không thể tải dữ liệu dashboard');
    }
}

function displayDashboard(stats) {
    displayMainStats(stats);
    displayMonthStats(stats);
    displayTopItems(stats);
    drawCharts(stats);
    
    // Initialize icons
    lucide.createIcons();
}

function displayMainStats(stats) {
    const cardsHtml = `
        <div class="col-md-6 col-lg-3">
            <div class="card stat-card primary shadow-sm h-100">
                <div class="card-body">
                    <div class="d-flex align-items-center justify-content-between">
                        <div>
                            <div class="stat-label">Tổng cây dược liệu</div>
                            <div class="stat-value text-primary">${formatNumber(stats.totalPlants || 0)}</div>
                            <div class="stat-change text-muted">
                                <i data-lucide="clock" style="width: 12px; height: 12px;"></i> Chờ duyệt: ${stats.pendingPlants || 0}
                            </div>
                        </div>
                        <div class="bg-primary bg-opacity-10 rounded p-3">
                            <i data-lucide="leaf" class="text-primary" style="width: 32px; height: 32px;"></i>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-md-6 col-lg-3">
            <div class="card stat-card success shadow-sm h-100">
                <div class="card-body">
                    <div class="d-flex align-items-center justify-content-between">
                        <div>
                            <div class="stat-label">Tổng người dùng</div>
                            <div class="stat-value text-success">${formatNumber(stats.totalUsers || 0)}</div>
                            <div class="stat-change text-muted">
                                <i data-lucide="user-check" style="width: 12px; height: 12px;"></i> Hoạt động: ${stats.activeUsers || 0}
                            </div>
                        </div>
                        <div class="bg-success bg-opacity-10 rounded p-3">
                            <i data-lucide="users" class="text-success" style="width: 32px; height: 32px;"></i>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-md-6 col-lg-3">
            <div class="card stat-card info shadow-sm h-100">
                <div class="card-body">
                    <div class="d-flex align-items-center justify-content-between">
                        <div>
                            <div class="stat-label">Tổng bài viết</div>
                            <div class="stat-value text-info">${formatNumber(stats.totalArticles || 0)}</div>
                            <div class="stat-change text-muted">
                                <i data-lucide="clock" style="width: 12px; height: 12px;"></i> Chờ duyệt: ${stats.pendingArticles || 0}
                            </div>
                        </div>
                        <div class="bg-info bg-opacity-10 rounded p-3">
                            <i data-lucide="file-text" class="text-info" style="width: 32px; height: 32px;"></i>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-md-6 col-lg-3">
            <div class="card stat-card warning shadow-sm h-100">
                <div class="card-body">
                    <div class="d-flex align-items-center justify-content-between">
                        <div>
                            <div class="stat-label">Tổng nghiên cứu</div>
                            <div class="stat-value text-warning">${formatNumber(stats.totalResearch || 0)}</div>
                        </div>
                        <div class="bg-warning bg-opacity-10 rounded p-3">
                            <i data-lucide="book-open" class="text-warning" style="width: 32px; height: 32px;"></i>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;

    document.getElementById('mainStatsCards').innerHTML = cardsHtml;
    lucide.createIcons();
}

function displayMonthStats(stats) {
    const monthCardsHtml = `
        <div class="col-md-6 col-lg-3">
            <div class="card stat-card secondary shadow-sm h-100">
                <div class="card-body">
                    <div class="d-flex align-items-center justify-content-between">
                        <div>
                            <div class="stat-label">Cây mới tháng này</div>
                            <div class="stat-value text-secondary">${formatNumber(stats.newPlantsThisMonth || 0)}</div>
                        </div>
                        <div class="bg-secondary bg-opacity-10 rounded p-3">
                            <i data-lucide="leaf" class="text-secondary" style="width: 28px; height: 28px;"></i>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-md-6 col-lg-3">
            <div class="card stat-card secondary shadow-sm h-100">
                <div class="card-body">
                    <div class="d-flex align-items-center justify-content-between">
                        <div>
                            <div class="stat-label">User mới tháng này</div>
                            <div class="stat-value text-secondary">${formatNumber(stats.newUsersThisMonth || 0)}</div>
                        </div>
                        <div class="bg-secondary bg-opacity-10 rounded p-3">
                            <i data-lucide="user-plus" class="text-secondary" style="width: 28px; height: 28px;"></i>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-md-6 col-lg-3">
            <div class="card stat-card secondary shadow-sm h-100">
                <div class="card-body">
                    <div class="d-flex align-items-center justify-content-between">
                        <div>
                            <div class="stat-label">Bài viết mới tháng này</div>
                            <div class="stat-value text-secondary">${formatNumber(stats.newArticlesThisMonth || 0)}</div>
                        </div>
                        <div class="bg-secondary bg-opacity-10 rounded p-3">
                            <i data-lucide="file-plus" class="text-secondary" style="width: 28px; height: 28px;"></i>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-md-6 col-lg-3">
            <div class="card stat-card secondary shadow-sm h-100">
                <div class="card-body">
                    <div class="d-flex align-items-center justify-content-between">
                        <div>
                            <div class="stat-label">Nghiên cứu mới tháng này</div>
                            <div class="stat-value text-secondary">${formatNumber(stats.newResearchThisMonth || 0)}</div>
                        </div>
                        <div class="bg-secondary bg-opacity-10 rounded p-3">
                            <i data-lucide="book-plus" class="text-secondary" style="width: 28px; height: 28px;"></i>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;

    document.getElementById('monthStatsCards').innerHTML = monthCardsHtml;
    lucide.createIcons();
}

function displayTopItems(stats) {
    // Top Plants
    let topPlantsHtml = '';
    if (stats.topPlants && stats.topPlants.length > 0) {
        stats.topPlants.forEach((plant, index) => {
            topPlantsHtml += `
                <div class="list-group-item top-item-card">
                    <div class="d-flex align-items-center">
                        <div class="flex-shrink-0 me-3">
                            <span class="badge bg-warning rounded-pill">${index + 1}</span>
                        </div>
                        <div class="flex-grow-1">
                            <h6 class="mb-1 fw-semibold">${plant.name || 'N/A'}</h6>
                            <small class="text-muted">
                                <i data-lucide="eye" style="width: 12px; height: 12px;"></i> ${plant.viewCount || 0} lượt xem
                            </small>
                        </div>
                        <div class="flex-shrink-0">
                            <a href="/plant-detail/${plant.slug || plant.id}" class="btn btn-sm btn-outline-primary" target="_blank">
                                <i data-lucide="external-link" style="width: 14px; height: 14px;"></i>
                            </a>
                        </div>
                    </div>
                </div>
            `;
        });
    } else {
        topPlantsHtml = '<div class="list-group-item text-center text-muted py-3">Chưa có dữ liệu</div>';
    }
    document.getElementById('topPlantsList').innerHTML = topPlantsHtml;

    // Top Articles
    let topArticlesHtml = '';
    if (stats.topArticles && stats.topArticles.length > 0) {
        stats.topArticles.forEach((article, index) => {
            topArticlesHtml += `
                <div class="list-group-item top-item-card">
                    <div class="d-flex align-items-center">
                        <div class="flex-shrink-0 me-3">
                            <span class="badge bg-info rounded-pill">${index + 1}</span>
                        </div>
                        <div class="flex-grow-1">
                            <h6 class="mb-1 fw-semibold">${article.title || 'N/A'}</h6>
                            <small class="text-muted">
                                <i data-lucide="eye" style="width: 12px; height: 12px;"></i> ${article.viewCount || 0} lượt xem
                            </small>
                        </div>
                        <div class="flex-shrink-0">
                            <a href="/article-detail/${article.slug || article.id}" class="btn btn-sm btn-outline-info" target="_blank">
                                <i data-lucide="external-link" style="width: 14px; height: 14px;"></i>
                            </a>
                        </div>
                    </div>
                </div>
            `;
        });
    } else {
        topArticlesHtml = '<div class="list-group-item text-center text-muted py-3">Chưa có dữ liệu</div>';
    }
    document.getElementById('topArticlesList').innerHTML = topArticlesHtml;

    lucide.createIcons();
}

function drawCharts(stats) {
    drawContentChart(stats);
    drawUserChart(stats);
}

function drawContentChart(stats) {
    const ctx = document.getElementById('contentChart');
    if (!ctx) return;

    if (contentChart) {
        contentChart.destroy();
    }

    contentChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: ['Cây dược liệu', 'Bài viết', 'Nghiên cứu', 'Bình luận', 'Họ thực vật', 'Công dụng'],
            datasets: [{
                label: 'Số lượng',
                data: [
                    stats.totalPlants || 0,
                    stats.totalArticles || 0,
                    stats.totalResearch || 0,
                    stats.totalComments || 0,
                    stats.totalFamilies || 0,
                    stats.totalDiseases || 0
                ],
                backgroundColor: [
                    'rgba(13, 110, 253, 0.7)',
                    'rgba(25, 135, 84, 0.7)',
                    'rgba(255, 193, 7, 0.7)',
                    'rgba(220, 53, 69, 0.7)',
                    'rgba(13, 202, 240, 0.7)',
                    'rgba(108, 117, 125, 0.7)'
                ],
                borderColor: [
                    'rgba(13, 110, 253, 1)',
                    'rgba(25, 135, 84, 1)',
                    'rgba(255, 193, 7, 1)',
                    'rgba(220, 53, 69, 1)',
                    'rgba(13, 202, 240, 1)',
                    'rgba(108, 117, 125, 1)'
                ],
                borderWidth: 2,
                borderRadius: 6
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    display: false
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        precision: 0
                    }
                }
            }
        }
    });
}

function drawUserChart(stats) {
    const ctx = document.getElementById('userChart');
    if (!ctx) return;

    if (userChart) {
        userChart.destroy();
    }

    userChart = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: ['Người dùng hoạt động', 'Người dùng bị khóa', 'Chuyên gia'],
            datasets: [{
                data: [
                    stats.activeUsers || 0,
                    stats.lockedUsers || 0,
                    stats.totalExperts || 0
                ],
                backgroundColor: [
                    'rgba(25, 135, 84, 0.8)',
                    'rgba(220, 53, 69, 0.8)',
                    'rgba(255, 193, 7, 0.8)'
                ],
                borderColor: [
                    'rgba(25, 135, 84, 1)',
                    'rgba(220, 53, 69, 1)',
                    'rgba(255, 193, 7, 1)'
                ],
                borderWidth: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    position: 'bottom'
                }
            }
        }
    });
}

function formatNumber(num) {
    return new Intl.NumberFormat('vi-VN').format(num);
}

