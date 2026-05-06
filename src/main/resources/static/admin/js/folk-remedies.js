var size = 10;

async function loadAllFolkRemedies(page) {
  const param = document.getElementById("param").value || "";
  const status = document.getElementById("status").value || "";
  var url = `/api/folk-remedies/admin/list?page=${page}&size=${size}&sort=createdAt,desc`;
  if (param !== "") {
      url += `&search=${encodeURIComponent(param)}`;
  }
  if (status !== "") {
      url += `&status=${encodeURIComponent(status)}`;
  }

  const response = await fetch(url, {
    method: 'GET',
    headers: new Headers({
      'Authorization': 'Bearer ' + token
    })
  });

  if (checkResponseError(response)) return;

  if (!response.ok) {
    toastr.error("Lỗi khi tải dữ liệu bài thuốc");
    return;
  }

  const result = await response.json();
  const list = result.content || [];
  
  const totalPage = result.totalPages || 0;
  const totalElements = result.totalElements || 0;
  const numberOfElements = result.numberOfElements || 0;

  const start = totalElements === 0 ? 0 : page * size + 1;
  const end = totalElements === 0 ? 0 : page * size + numberOfElements;

  let main = '';
  for (let i = 0; i < list.length; i++) {
    const d = list[i];
    
    // Xử lý huy hiệu trạng thái
    let statusBadge = '';
    if (d.status === 'approved') {
        statusBadge = '<span class="badge bg-success">Đã duyệt</span>';
    } else if (d.status === 'rejected') {
        statusBadge = '<span class="badge bg-danger">Từ chối</span>';
    } else {
        statusBadge = '<span class="badge bg-warning text-dark">Chờ duyệt</span>';
    }

    // Hiển thị cây liên kết
    let plantsHtml = '';
    if (d.plants && d.plants.length > 0) {
        plantsHtml = d.plants.map(p => `<span class="badge bg-info mb-1">${p.name}</span>`).join('<br>');
    } else {
        plantsHtml = '<span class="text-muted small">Không có</span>';
    }

    // Hiển thị bệnh liên kết
    let diseasesHtml = '';
    if (d.diseases && d.diseases.length > 0) {
        diseasesHtml = d.diseases.map(d => `<span class="badge bg-secondary mb-1">${d.name}</span>`).join('<br>');
    } else {
        diseasesHtml = '<span class="text-muted small">Không có</span>';
    }

    main += `
      <tr>
        <td>${d.id}</td>
        <td class="fw-bold">${d.name}</td>
        <td>
            <div class="small text-muted mb-1"><strong>Nguồn:</strong> ${d.source || 'Không rõ'}</div>
            <div class="small"><strong>Cách dùng:</strong> ${d.usageInstruction ? (d.usageInstruction.length > 50 ? d.usageInstruction.substring(0, 50) + '...' : d.usageInstruction) : 'Đang cập nhật'}</div>
        </td>
        <td>${statusBadge}</td>
        <td>${plantsHtml}</td>
        <td>${diseasesHtml}</td>
        <td>${d.createdAt || 'N/A'}</td>
        <td class="text-center" style="min-width: 80px;">
            <a href="/admin/create-folk-remedy?id=${d.id}" class="btn btn-primary btn-sm mb-1" title="Sửa"><i class="fa-solid fa-pencil"></i></a>
            <button onclick="deleteRemedy(${d.id})" class="btn btn-danger btn-sm mb-1" title="Xóa"><i class="fa-solid fa-trash"></i></button>
        </td>
    </tr>`;
  }

  document.getElementById("listData").innerHTML = main;
  document.getElementById("numElm").innerText = `Đang hiển thị ${start}-${end} trong ${totalElements} kết quả`;

  // Pagination (Sliding)
  let pageHtml = '';
  if (totalPage > 1) {
    // Nút Trước
    pageHtml += `<li class="page-item ${page === 0 ? 'disabled' : ''}">
                   <a class="page-link" href="#" onclick="loadAllFolkRemedies(${page - 1})">Trước</a>
                 </li>`;
    
    // Tính toán số trang hiển thị
    let startPage = Math.max(0, page - 2);
    let endPage = Math.min(totalPage - 1, page + 2);
    
    if (startPage > 0) {
        pageHtml += `<li class="page-item"><a class="page-link" href="#" onclick="loadAllFolkRemedies(0)">1</a></li>`;
        if (startPage > 1) {
            pageHtml += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
    }
    
    for (let i = startPage; i <= endPage; i++) {
        pageHtml += `<li class="page-item ${i === page ? 'active' : ''}">
                       <a class="page-link" href="#" onclick="loadAllFolkRemedies(${i})">${i + 1}</a>
                     </li>`;
    }
    
    if (endPage < totalPage - 1) {
        if (endPage < totalPage - 2) {
            pageHtml += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
        pageHtml += `<li class="page-item"><a class="page-link" href="#" onclick="loadAllFolkRemedies(${totalPage - 1})">${totalPage}</a></li>`;
    }

    // Nút Sau
    pageHtml += `<li class="page-item ${page === totalPage - 1 ? 'disabled' : ''}">
                   <a class="page-link" href="#" onclick="loadAllFolkRemedies(${page + 1})">Sau</a>
                 </li>`;
  }
  document.getElementById("pageable").innerHTML = pageHtml;
}

async function approveRemedy(id) {
    if (!confirm("Xác nhận duyệt bài thuốc này?")) return;
    
    const response = await fetch(`/api/folk-remedies/admin/approve?id=${id}`, {
        method: 'POST',
        headers: new Headers({
            'Authorization': 'Bearer ' + token
        })
    });
    
    if (checkResponseError(response)) return;
    
    if (response.ok) {
        toastr.success("Đã duyệt bài thuốc thành công!");
        loadAllFolkRemedies(0);
    } else {
        toastr.error("Có lỗi xảy ra khi duyệt");
    }
}

async function rejectRemedy(id) {
    if (!confirm("Từ chối bài thuốc này?")) return;
    
    const response = await fetch(`/api/folk-remedies/admin/reject?id=${id}`, {
        method: 'POST',
        headers: new Headers({
            'Authorization': 'Bearer ' + token
        })
    });
    
    if (checkResponseError(response)) return;
    
    if (response.ok) {
        toastr.warning("Đã từ chối bài thuốc!");
        loadAllFolkRemedies(0);
    } else {
        toastr.error("Có lỗi xảy ra khi từ chối");
    }
}

async function deleteRemedy(id) {
    if (!confirm("Cảnh báo: Bạn có chắc chắn muốn xóa vĩnh viễn bài thuốc này không?")) return;
    
    const response = await fetch(`/api/folk-remedies/admin/delete?id=${id}`, {
        method: 'DELETE',
        headers: new Headers({
            'Authorization': 'Bearer ' + token
        })
    });
    
    if (checkResponseError(response)) return;
    
    if (response.ok) {
        swal({
            title: "Thông báo",
            text: "Xóa bài thuốc thành công!",
            type: "success"
        }, function() {
            loadAllFolkRemedies(0);
        });
    } else {
        toastr.error("Có lỗi xảy ra khi xóa");
    }
}
