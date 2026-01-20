const EXPERT_API_URL = '/api/expert/public/all';
let currentPage = 0;
let currentSearch = '';
let currentSpecialization = '';
let currentSort = ''; // 'name-asc', 'newest', etc.

// ===========================================
// HÀM CHÍNH: GỌI API VÀ HIỂN THỊ DỮ LIỆU
// ===========================================
async function loadExperts(page = 0, q = '', specialization = '', sort = '') {
    try {
        const url = new URL(EXPERT_API_URL, window.location.origin);
        url.searchParams.append('page', page);
        url.searchParams.append('size', 9);

        if (q) {
            url.searchParams.append('q', q);
        }
        if (specialization) {
            url.searchParams.append('specialization', specialization);
        }

        if (sort) {
            let sortField = '';
            let sortDirection = '';

            if (sort === 'name-asc') {
                sortField = 'name';
                sortDirection = 'asc';
            } else if (sort === 'name-desc') {
                sortField = 'name';
                sortDirection = 'desc';
            } else if (sort === 'newest') {
                sortField = 'createdDate';
                sortDirection = 'desc';
            }

            if(sortField) {
                url.searchParams.append('sort', `${sortField},${sortDirection}`);
            }
        }

        const response = await fetch(url);

        if (!response.ok) {
            throw new Error(`API returned status ${response.status}`);
        }

        const data = await response.json();
        console.log("Expert data:", data);
        
        currentPage = data.number || 0;

        if (data.content && data.content.length > 0) {
            renderExpertCards(data.content);
            renderPagination(data);
        } else {
            $('.experts-grid').html('<div class="col-12"><div class="alert alert-info" role="alert">Không tìm thấy chuyên gia nào.</div></div>');
            $('.pagination').empty();
        }

    } catch (error) {
        console.error('Lỗi khi tải danh sách chuyên gia:', error);
        $('.experts-grid').html('<div class="col-12"><div class="alert alert-danger" role="alert">Không thể tải dữ liệu chuyên gia. Vui lòng thử lại sau.</div></div>');
        $('.pagination').empty();
    }
}

function renderExpertCards(experts) {
    const grid = $('.experts-grid');
    grid.empty(); // Xóa nội dung cũ

    if (experts.length === 0) {
        grid.html('<div class="col-12"><p class="text-center text-muted">Không tìm thấy chuyên gia nào phù hợp.</p></div>');
        return;
    }

    experts.forEach(expert => {
        // Tạo HTML cho mỗi card chuyên gia
        const avatarUrl = expert.avatar || 'https://via.placeholder.com/128x128?text=Expert';
        const expertName = expert.name || 'Đang cập nhật';
        const expertTitle = expert.title ? expert.title + ' ' : '';
        const cardHtml = `
            <div class="col-md-6 col-lg-4">
                <div class="card h-100 text-center">
                    <div class="d-flex justify-content-center pt-4">
                        <div class="rounded-circle overflow-hidden bg-light" style="width: 8rem; height: 8rem;">
                            <img src="${avatarUrl}" 
                                 class="w-100 h-100 object-fit-cover" 
                                 alt="${expertName}"
                                 onerror="this.src='https://via.placeholder.com/128x128?text=Expert'">
                        </div>
                    </div>
                    <div class="card-body">
                        <h5 class="card-title mb-1">${expertTitle}${expertName}</h5>
                        <p class="text-muted small">${expert.specialization || 'Chưa rõ chuyên môn'}</p>
                        <ul class="list-unstyled text-start small mt-3">
                            <li class="d-flex align-items-center mb-2">
                                <i class="bi bi-geo-alt me-2 text-muted"></i> ${expert.institution || 'Đang cập nhật cơ quan'}
                            </li>
                            <li class="d-flex align-items-center mb-2">
                                <i class="bi bi-mortarboard me-2 text-muted"></i> ${expert.education ? 'Xem thông tin học vấn' : 'Chưa có thông tin học vấn'}
                            </li>
                            <li class="d-flex align-items-center mb-2">
                                <i class="bi bi-book me-2 text-muted"></i> ${expert.bio ? 'Xem tiểu sử' : 'Chưa có tiểu sử'}
                            </li>
                            <li class="d-flex align-items-center">
                                <i class="bi bi-award me-2 text-muted"></i> ${expert.achievements ? 'Xem thành tựu' : 'Chưa có thành tựu'}
                            </li>
                        </ul>
                    </div>
                    <div class="card-footer bg-white">
                        <a href="/expert-detail/${expert.slug || expert.id}" class="btn btn-outline-secondary">Xem chi tiết</a>
                    </div>
                </div>
            </div>
        `;
        grid.append(cardHtml);
    });
}

// ===========================================
// HÀM TẠO GIAO DIỆN PHÂN TRANG
// ===========================================
function renderPagination(pageData) {
    const paginationUl = $('.pagination');
    paginationUl.empty(); // Xóa phân trang cũ

    const totalPages = pageData.totalPages;
    const currentPageIndex = pageData.number; // 0-based index

    if (totalPages <= 1) return;

    // Nút PREVIOUS
    const prevDisabled = pageData.first ? 'disabled' : '';
    paginationUl.append(`
        <li class="page-item ${prevDisabled}">
            <a class="page-link" href="#" data-page="${currentPageIndex - 1}">&laquo;</a>
        </li>
    `);

    // Các nút trang số (Chỉ hiển thị 5 trang gần nhất hoặc xung quanh)
    let startPage = Math.max(0, currentPageIndex - 2);
    let endPage = Math.min(totalPages - 1, currentPageIndex + 2);

    // Đảm bảo luôn hiển thị 5 trang nếu có đủ
    if (endPage - startPage < 4 && totalPages >= 5) {
        if (currentPageIndex < 2) {
            endPage = Math.min(totalPages - 1, 4);
        } else if (currentPageIndex > totalPages - 3) {
            startPage = Math.max(0, totalPages - 5);
        }
    }


    for (let i = startPage; i <= endPage; i++) {
        const activeClass = i === currentPageIndex ? 'active' : '';
        paginationUl.append(`
            <li class="page-item ${activeClass}">
                <a class="page-link" href="#" data-page="${i}">${i + 1}</a>
            </li>
        `);
    }

    // Nút NEXT
    const nextDisabled = pageData.last ? 'disabled' : '';
    paginationUl.append(`
        <li class="page-item ${nextDisabled}">
            <a class="page-link" href="#" data-page="${currentPageIndex + 1}">&raquo;</a>
        </li>
    `);

    // Bắt sự kiện click vào nút trang
    paginationUl.find('a.page-link').on('click', function(e) {
        e.preventDefault();
        const newPage = $(this).data('page');
        if (newPage !== undefined) {
            loadExperts(newPage, currentSearch, currentSpecialization, currentSort);
        }
    });
}


// ===========================================
// XỬ LÝ SỰ KIỆN TÌM KIẾM VÀ LỌC
// ===========================================
document.addEventListener('DOMContentLoaded', function() {
    loadExperts(currentPage, currentSearch, currentSpecialization, currentSort);

    // 1. Xử lý Tìm kiếm (Input type="text")
    $('#searchInput').on('keyup', function(e) {
        if (e.key === 'Enter' || $(this).val().length % 3 === 0 || $(this).val().length === 0) {
            currentSearch = $(this).val();
            // Reset về trang 0 khi tìm kiếm mới
            loadExperts(0, currentSearch, currentSpecialization, currentSort);
        }
    });

    // 2. Xử lý Lọc theo Chuyên môn (Select)
    $('#specializationFilter').on('change', function() {
        currentSpecialization = $(this).val() || '';
        // Reset về trang 0 khi lọc mới
        loadExperts(0, currentSearch, currentSpecialization, currentSort);
    });

    // 3. Xử lý Sắp xếp (Select)
    $('#sortFilter').on('change', function() {
        currentSort = $(this).val() || '';
        // Reset về trang 0 khi sắp xếp mới
        loadExperts(0, currentSearch, currentSpecialization, currentSort);
    });
});