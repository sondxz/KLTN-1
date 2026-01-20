var size = 8;

async function loadAllResearch(page) {
    const param = document.getElementById("param").value || "";
    const field = document.getElementById("field").value || "";
    const year = document.getElementById("year").value
    var url = `/api/research/public/all?page=${page}&size=${size}&q=${param}&sort=id,desc`;
    if(field != ""){
        url += `&field=${field}`
    }
    if(year != ""){
        url += `&publishedYear=${year}`
    }
    const response = await fetch(url, {
    });
    const result = await response.json();
    const list = result.content || [];
    const totalPage = result.totalPages || 0;

    let main = '';
    for (let i = 0; i < list.length; i++) {
        const d = list[i];
        main +=
            `
            <div class="card p-4">
              <div class="row g-4">
                <div class="col-sm-3">
                    <div class="mb-3">
                    <img src="${d.imageBanner}" class="img-fluid rounded" alt="Nghiên cứu 1">
                  </div>
                </div>
                <!-- Nội dung chính -->
                <div class="col-md-6">
                  <div class="d-flex align-items-center gap-3 mb-3">
                    <span class="badge bg-light text-primary">${d.field}</span>
                    <div class="d-flex align-items-center text-muted small">
                      <i class="bi bi-calendar me-1"></i>${d.publishedYear}
                    </div>
                  </div>
                  <h5 class="mb-3">
                    <a href="#" class="text-decoration-none text-dark fw-semibold">${d.title}</a>
                  </h5>
                  <p class="text-muted mb-3">
                  ${d.abstractText}
                  </p>
                  <div class="d-flex flex-wrap gap-3 mb-3 small text-muted">
                    <span><i class="bi bi-person me-1"></i> ${d.authors}</span>
                    <span><i class="bi bi-book me-1"></i>${d.journal}</span>
                  </div>
                  <div class="d-flex flex-wrap gap-2">
                    ${(d.researchPlants?.map(pd =>  `<span class="badge bg-light text-dark"><i class="bi bi-tag me-1"></i>${pd.plant?.name}</span>`) || []).join(' ')}
                  </div>
                </div>
        
                <div class="col-md-3 d-flex flex-column justify-content-between">
                  <div class="d-flex flex-column gap-2">
                    <a href="/research-detail/${d.slug}" class="btn btn-outline-secondary w-100 d-flex align-items-center justify-content-center gap-2">
                      <i class="bi bi-file-earmark-text"></i> Xem chi tiết
                    </a>
                    ${
                      d.linkDocument == null
                        ? `<button disabled class="btn btn-outline-secondary w-100 d-flex align-items-center justify-content-center gap-2">
                            <i class="bi bi-filetype-pdf"></i> Không có tài liệu đính kèm
                           </button>`
                        : `<a href="${d.linkDocument}" target="_blank" rel="noopener noreferrer"
                              class="btn btn-outline-secondary w-100 d-flex align-items-center justify-content-center gap-2">
                              <i class="bi bi-filetype-pdf"></i> Tài liệu
                           </a>`
                    }
                  </div>
                </div>
              </div>
            </div>
            `;
    }
    document.getElementById("list-research").innerHTML = main;
    renderPagination(page, totalPage)
}



function renderPagination(currentPage, totalPages) {
    let maxVisiblePages = 3; // số trang hiển thị chính giữa
    let mainpage = '';

    // nút prev
    if (currentPage > 0) {
        mainpage += `<li class="page-item pointer" onclick="loadAllResearch(${currentPage - 1})"><a class="page-link">&laquo;</a></li>`;
    }

    // trang đầu
    if (currentPage > 1) {
        mainpage += `<li class="page-item pointer" onclick="loadAllResearch(0)"><a class="page-link">1</a></li>`;
    }

    // dấu ...
    if (currentPage > 2) {
        mainpage += `<li class="page-item disabled"><a class="page-link">...</a></li>`;
    }

    // trang ở giữa
    for (let i = currentPage - 1; i <= currentPage + 1; i++) {
        if (i >= 0 && i < totalPages) {
            mainpage += `<li onclick="loadAllResearch(${i})" class="page-item pointer ${i === currentPage ? 'active' : ''}"><a class="page-link">${i + 1}</a></li>`;
        }
    }

    // dấu ...
    if (currentPage < totalPages - 3) {
        mainpage += `<li class="page-item disabled"><a class="page-link">...</a></li>`;
    }

    // trang cuối
    if (currentPage < totalPages - 2) {
        mainpage += `<li class="page-item pointer" onclick="loadAllResearch(${totalPages - 1})"><a class="page-link">${totalPages}</a></li>`;
    }

    // nút next
    if (currentPage < totalPages - 1) {
        mainpage += `<li class="page-item pointer" onclick="loadAllResearch(${currentPage + 1})"><a class="page-link">&raquo;</a></li>`;
    }

    document.getElementById("pageable").innerHTML = mainpage;
}