var token = localStorage.getItem("token");
var size = 10;

// ========== PLANTS ==========
// Cache để lưu thông tin user
const userCache = {};

// Hàm lấy thông tin user từ username
async function getUserInfo(username) {
    if (!username) return null;
    
    // Kiểm tra cache
    if (userCache[username]) {
        return userCache[username];
    }
    
    try {
        // Gọi API để lấy user info (nếu có API này)
        // Hoặc có thể lấy từ danh sách user đã load
        // Tạm thời return null, sẽ cập nhật sau khi có API
        return null;
    } catch (err) {
        console.error("Error getting user info:", err);
        return null;
    }
}

// Hàm format date
function formatDate(dateString) {
    if (!dateString) return '-';
    try {
        const date = new Date(dateString);
        if (isNaN(date.getTime())) {
            // Nếu không parse được, thử format khác
            // Format: "12:34 20/12/2025" hoặc ISO format
            if (dateString.includes('/')) {
                // Format: "hh:mm dd/MM/yyyy"
                const parts = dateString.split(' ');
                if (parts.length === 2) {
                    return dateString; // Giữ nguyên nếu đã đúng format
                }
            }
            return dateString; // Trả về nguyên bản nếu không parse được
        }
        // Format: "hh:mm dd/MM/yyyy"
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const year = date.getFullYear();
        return `${hours}:${minutes} ${day}/${month}/${year}`;
    } catch (e) {
        return dateString; // Trả về nguyên bản nếu có lỗi
    }
}

async function loadPendingPlants(page) {
    const q = document.getElementById("plantSearch")?.value || "";
    var url = `/api/plant/expert/pending?page=${page}&size=${size}`;
    if(q) {
        url += `&q=${encodeURIComponent(q)}`;
    }
    
    try {
        const response = await fetch(url, {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        });

        if (!response.ok) {
            toastr.error("Lỗi khi tải dữ liệu");
            return;
        }

        const result = await response.json();
        const list = result.content || [];
        const totalPage = result.totalPages || 0;
        const totalElements = result.totalElements || 0;

        // Update count
        document.getElementById("plantsCount").textContent = totalElements;

        let main = '';
        if (list.length === 0) {
            main = '<tr><td colspan="7" class="text-center">Không có cây dược liệu nào chờ duyệt</td></tr>';
            document.getElementById("plantsTableBody").innerHTML = main;
        } else {
            for (let i = 0; i < list.length; i++) {
                const d = list[i];
                main += `
                    <tr>
                        <td class="align-middle"><span class="text-muted small">#${d.id}</span></td>
                        <td class="align-middle">
                            <img src="${d.image || '/image/placeholder.svg'}" 
                                 class="rounded" 
                                 style="width: 60px; height: 60px; object-fit: cover; border: 1px solid #dee2e6;">
                        </td>
                        <td class="align-middle">
                            <div class="fw-semibold">${d.name}</div>
                        </td>
                        <td class="align-middle">
                            <span class="text-muted small fst-italic">${d.scientificName || 'Chưa có'}</span>
                        </td>
                        <td class="align-middle">
                            <div class="d-flex align-items-center">
                                <i class="bi bi-person-circle me-2 text-secondary"></i>
                                <span id="user-name-${d.id}">${d.createdBy || '-'}</span>
                            </div>
                        </td>
                        <td class="align-middle">
                            <small class="text-muted">
                                <i class="bi bi-clock me-1"></i>${formatDate(d.createdAt)}
                            </small>
                        </td>
                        <td class="align-middle">
                            <div class="btn-group btn-group-sm" role="group">
                                <button onclick="approvePlant(${d.id})" 
                                        class="btn btn-outline-success" 
                                        title="Duyệt bài đăng">
                                    <i class="bi bi-check-lg"></i> Duyệt
                                </button>
                                <button onclick="rejectPlant(${d.id})" 
                                        class="btn btn-outline-danger" 
                                        title="Từ chối bài đăng">
                                    <i class="bi bi-x-lg"></i> Từ chối
                                </button>
                                <a href="/plant-detail/${d.slug}" 
                                   target="_blank" 
                                   class="btn btn-outline-primary" 
                                   title="Xem chi tiết">
                                    <i class="bi bi-eye"></i>
                                </a>
                            </div>
                        </td>
                    </tr>`;
            }
            document.getElementById("plantsTableBody").innerHTML = main;
            
            // Load tên người dùng cho từng plant
            for (let i = 0; i < list.length; i++) {
                const d = list[i];
                if (d.createdBy) {
                    // Thử parse createdBy thành ID
                    const createdByAsId = parseInt(d.createdBy);
                    if (!isNaN(createdByAsId) && createdByAsId > 0) {
                        // Nếu là số, gọi API find-by-id
                        fetch(`/api/admin/find-user-by-id?id=${createdByAsId}`, {
                            headers: {
                                'Authorization': 'Bearer ' + token
                            }
                        })
                            .then(res => res.ok ? res.json() : null)
                            .then(userInfo => {
                                if (userInfo) {
                                    const userNameCell = document.getElementById(`user-name-${d.id}`);
                                    if (userNameCell) {
                                        userNameCell.textContent = userInfo.fullname || userInfo.username || d.createdBy;
                                    }
                                }
                            })
                            .catch(() => {
                                // Nếu không tìm thấy bằng ID, thử tìm bằng username
                                fetch(`/api/admin/find-user-by-username?username=${encodeURIComponent(d.createdBy)}`, {
                                    headers: {
                                        'Authorization': 'Bearer ' + token
                                    }
                                })
                                    .then(res => res.ok ? res.json() : null)
                                    .then(userInfo => {
                                        if (userInfo) {
                                            const userNameCell = document.getElementById(`user-name-${d.id}`);
                                            if (userNameCell) {
                                                userNameCell.textContent = userInfo.fullname || userInfo.username || d.createdBy;
                                            }
                                        }
                                    })
                                    .catch(() => {});
                            });
                    } else {
                        // Nếu không phải số, thử tìm bằng username
                        fetch(`/api/admin/find-user-by-username?username=${encodeURIComponent(d.createdBy)}`, {
                            headers: {
                                'Authorization': 'Bearer ' + token
                            }
                        })
                            .then(res => res.ok ? res.json() : null)
                            .then(userInfo => {
                                if (userInfo) {
                                    const userNameCell = document.getElementById(`user-name-${d.id}`);
                                    if (userNameCell) {
                                        userNameCell.textContent = userInfo.fullname || userInfo.username || d.createdBy;
                                    }
                                }
                            })
                            .catch(() => {});
                    }
                }
            }
        }
        renderPagination(page, totalPage, "plantsPagination", loadPendingPlants);
    } catch (err) {
        console.error(err);
        toastr.error("Lỗi kết nối!");
    }
}

async function approvePlant(id) {
    if (!confirm("Bạn có chắc chắn muốn duyệt cây dược liệu này?")) {
        return;
    }

    try {
        const response = await fetch(`/api/plant/expert/approve?id=${id}`, {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        });

        // Xử lý 401/403 - redirect về login nếu token hết hạn
        if (checkResponseError(response)) {
            return; // Đã redirect, không cần xử lý tiếp
        }

        if (response.ok) {
            toastr.success("Đã duyệt thành công!");
            loadPendingPlants(0);
        } else {
            toastr.error("Lỗi khi duyệt!");
        }
    } catch (err) {
        toastr.error("Lỗi kết nối!");
    }
}

async function rejectPlant(id) {
    if (!confirm("Bạn có chắc chắn muốn từ chối cây dược liệu này?")) {
        return;
    }

    try {
        const response = await fetch(`/api/plant/expert/reject?id=${id}`, {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        });

        // Xử lý 401/403 - redirect về login nếu token hết hạn
        if (checkResponseError(response)) {
            return; // Đã redirect, không cần xử lý tiếp
        }

        if (response.ok) {
            toastr.success("Đã từ chối thành công!");
            loadPendingPlants(0);
        } else {
            toastr.error("Lỗi khi từ chối!");
        }
    } catch (err) {
        toastr.error("Lỗi kết nối!");
    }
}

// ========== ARTICLES ==========
async function loadPendingArticles(page) {
    const q = document.getElementById("articleSearch")?.value || "";
    var url = `/api/articles/expert/pending?page=${page}&size=${size}`;
    if(q) {
        url += `&q=${encodeURIComponent(q)}`;
    }
    
    try {
        const response = await fetch(url, {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        });

        if (!response.ok) {
            toastr.error("Lỗi khi tải dữ liệu");
            return;
        }

        const result = await response.json();
        const list = result.content || [];
        const totalPage = result.totalPages || 0;
        const totalElements = result.totalElements || 0;

        // Update count
        document.getElementById("articlesCount").textContent = totalElements;

        let main = '';
        if (list.length === 0) {
            main = '<tr><td colspan="6" class="text-center">Không có bài viết nào chờ duyệt</td></tr>';
            document.getElementById("articlesTableBody").innerHTML = main;
        } else {
            document.getElementById("articlesTableBody").innerHTML = '<tr><td colspan="6" class="text-center py-4"><div class="spinner-border text-primary" role="status"><span class="visually-hidden">Đang tải...</span></div></td></tr>';
            
            const userInfoPromises = list.map(d => {
                if (d.user) {
                    return Promise.resolve(d.user);
                }
                if (d.createdBy) {
                    const createdByAsId = parseInt(d.createdBy);
                    if (!isNaN(createdByAsId)) {
                        return fetch(`/api/admin/find-user-by-id?id=${createdByAsId}`, {
                            headers: {
                                'Authorization': 'Bearer ' + token
                            }
                        })
                            .then(res => res.ok ? res.json() : null)
                            .catch(() => null);
                    } else {
                        return fetch(`/api/admin/find-user-by-username?username=${encodeURIComponent(d.createdBy)}`, {
                            headers: {
                                'Authorization': 'Bearer ' + token
                            }
                        })
                            .then(res => res.ok ? res.json() : null)
                            .catch(() => null);
                    }
                }
                return Promise.resolve(null);
            });
            
            const userInfos = await Promise.all(userInfoPromises);
            
            for (let i = 0; i < list.length; i++) {
                const d = list[i];
                const userInfo = userInfos[i];
                const userName = userInfo ? (userInfo.fullname || userInfo.username || d.createdBy || '-') : (d.createdBy || '-');
                main += `
                    <tr>
                        <td class="align-middle"><span class="text-muted small">#${d.id}</span></td>
                        <td class="align-middle">
                            <img src="${d.imageBanner || '/image/placeholder.svg'}" 
                                 class="rounded" 
                                 style="width: 60px; height: 60px; object-fit: cover; border: 1px solid #dee2e6;">
                        </td>
                        <td class="align-middle">
                            <div class="fw-semibold">${d.title}</div>
                        </td>
                        <td class="align-middle">
                            <div class="d-flex align-items-center">
                                <i class="bi bi-person-circle me-2 text-secondary"></i>
                                <span>${userName}</span>
                            </div>
                        </td>
                        <td class="align-middle">
                            <small class="text-muted">
                                <i class="bi bi-clock me-1"></i>${formatDate(d.createdAt)}
                            </small>
                        </td>
                        <td class="align-middle">
                            <div class="btn-group btn-group-sm" role="group">
                                <button onclick="approveArticle(${d.id})" 
                                        class="btn btn-outline-success" 
                                        title="Duyệt bài viết">
                                    <i class="bi bi-check-lg"></i> Duyệt
                                </button>
                                <button onclick="rejectArticle(${d.id})" 
                                        class="btn btn-outline-danger" 
                                        title="Từ chối bài viết">
                                    <i class="bi bi-x-lg"></i> Từ chối
                                </button>
                                <a href="/article-detail/${d.slug}" 
                                   target="_blank" 
                                   class="btn btn-outline-primary" 
                                   title="Xem chi tiết">
                                    <i class="bi bi-eye"></i>
                                </a>
                            </div>
                        </td>
                    </tr>`;
            }
        }

        document.getElementById("articlesTableBody").innerHTML = main;
        renderPagination(page, totalPage, "articlesPagination", loadPendingArticles);
    } catch (err) {
        console.error(err);
        toastr.error("Lỗi kết nối!");
    }
}

async function approveArticle(id) {
    if (!confirm("Bạn có chắc chắn muốn duyệt bài viết này?")) {
        return;
    }

    try {
        const response = await fetch(`/api/articles/expert/approve?id=${id}`, {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        });

        // Xử lý 401/403 - redirect về login nếu token hết hạn
        if (checkResponseError(response)) {
            return; // Đã redirect, không cần xử lý tiếp
        }

        if (response.ok) {
            toastr.success("Đã duyệt thành công!");
            loadPendingArticles(0);
        } else {
            toastr.error("Lỗi khi duyệt!");
        }
    } catch (err) {
        toastr.error("Lỗi kết nối!");
    }
}

async function rejectArticle(id) {
    if (!confirm("Bạn có chắc chắn muốn từ chối bài viết này?")) {
        return;
    }

    try {
        const response = await fetch(`/api/articles/expert/reject?id=${id}`, {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        });

        // Xử lý 401/403 - redirect về login nếu token hết hạn
        if (checkResponseError(response)) {
            return; // Đã redirect, không cần xử lý tiếp
        }

        if (response.ok) {
            toastr.success("Đã từ chối thành công!");
            loadPendingArticles(0);
        } else {
            toastr.error("Lỗi khi từ chối!");
        }
    } catch (err) {
        toastr.error("Lỗi kết nối!");
    }
}

// ========== PAGINATION ==========
function renderPagination(currentPage, totalPages, paginationId, loadFunction) {
    let mainpage = '';

    if (currentPage > 0) {
        mainpage += `<li class="page-item"><a class="page-link" href="#" onclick="${loadFunction.name}(${currentPage - 1}); return false;">&laquo;</a></li>`;
    }

    for (let i = 0; i < totalPages; i++) {
        if (i === currentPage) {
            mainpage += `<li class="page-item active"><a class="page-link" href="#">${i + 1}</a></li>`;
        } else {
            mainpage += `<li class="page-item"><a class="page-link" href="#" onclick="${loadFunction.name}(${i}); return false;">${i + 1}</a></li>`;
        }
    }

    if (currentPage < totalPages - 1) {
        mainpage += `<li class="page-item"><a class="page-link" href="#" onclick="${loadFunction.name}(${currentPage + 1}); return false;">&raquo;</a></li>`;
    }

    document.getElementById(paginationId).innerHTML = mainpage;
}





