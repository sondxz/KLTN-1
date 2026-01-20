var size = 10;

async function loadAllDiseases(page) {
  const param = document.getElementById("param").value || "";
  const url = `/api/diseases/public/get-all?page=${page}&size=${size}&q=${param}`;

  const response = await fetch(url, {
    method: 'GET',
    headers: new Headers({
      'Authorization': 'Bearer ' + token
    })
  });

  // Xử lý 401/403 - redirect về login nếu token hết hạn
  if (checkResponseError(response)) {
    return; // Đã redirect, không cần xử lý tiếp
  }

  if (!response.ok) {
    toastr.error("Lỗi khi tải dữ liệu bệnh");
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
    main += `
      <tr>
        <td>${d.id}</td>
        <td>${d.name}</td>
        <td>${d.description || ""}</td>
        <td class="text-center">
          <button class="btn btn-sm btn-primary me-1" onclick="editDisease(${d.id})"><i class="fa-solid fa-pencil"></i></button>
          <button class="btn btn-sm btn-danger" onclick="deleteDisease(${d.id})"><i class="fa-solid fa-xmark"></i></button>
        </td>
      </tr>`;
  }

  document.getElementById("listDiseases").innerHTML = main;
  document.getElementById("numElm").innerText = `Đang hiển thị ${start}-${end} trong ${totalElements} kết quả`;

  // Pagination
  let pageHtml = '';
  for (let i = 1; i <= totalPage; i++) {
    pageHtml += `<li class="page-item ${i === page + 1 ? 'active' : ''}">
                   <a class="page-link" href="#" onclick="loadAllDiseases(${i - 1})">${i}</a>
                 </li>`;
  }
  document.getElementById("pageable").innerHTML = pageHtml;
}

// ================= THÊM / CẬP NHẬT =================
async function saveDisease() {
  const id = document.getElementById("diseaseId").value;
  const name = document.getElementById("diseaseName").value.trim();
  const description = document.getElementById("diseaseDesc").value.trim();
  const slug = document.getElementById("diseaseSlug").value.trim();

  if (name === "") {
    toastr.warning("Tên bệnh không được bỏ trống!");
    return;
  }

  const data = { id, name, description, slug };

  const url = `/api/diseases/admin/create`;

  const response = await fetch(url, {
    method: 'POST',
    headers: new Headers({
      'Authorization': 'Bearer ' + token,
      'Content-Type': 'application/json'
    }),
    body: JSON.stringify(data)
  });

  // Xử lý 401/403 - redirect về login nếu token hết hạn
  if (checkResponseError(response)) {
    return; // Đã redirect, không cần xử lý tiếp
  }

  const result = await response.json();

  if (response.ok) {
    swal({
      title: "Thành công",
      text: "Lưu thông tin bệnh thành công!",
      type: "success"
    }, function () {
      loadAllDiseases(0);
      $("#exampleModal").modal("hide");
    });
  } else if (response.status === exceptionCode) {
    toastr.error(result.defaultMessage || "Lỗi dữ liệu!");
  } else {
    toastr.error("Không thể lưu thông tin bệnh!");
  }
}

// ================= XÓA =================
async function deleteDisease(id) {
  const confirmDel = confirm("Bạn có chắc muốn xóa bệnh này?");
  if (!confirmDel) return;

  const url = `/api/diseases/admin/delete?id=${id}`;

  const response = await fetch(url, {
    method: 'DELETE',
    headers: new Headers({
      'Authorization': 'Bearer ' + token
    })
  });

  // Xử lý 401/403 - redirect về login nếu token hết hạn
  if (checkResponseError(response)) {
    return; // Đã redirect, không cần xử lý tiếp
  }

  if (response.ok) {
    swal({
      title: "Đã xóa!",
      text: "Bệnh đã được xóa thành công!",
      type: "success"
    }, function () {
      loadAllDiseases(0);
    });
  } else {
    toastr.error("Không thể xóa bệnh!");
  }
}

// ================= SỬA =================
async function editDisease(id) {
  const url = `/api/diseases/public/find-by-id?id=${id}`;
  const response = await fetch(url, {
    headers: new Headers({
      'Authorization': 'Bearer ' + token
    })
  });

  // Xử lý 401/403 - redirect về login nếu token hết hạn
  if (checkResponseError(response)) {
    return; // Đã redirect, không cần xử lý tiếp
  }

  if (!response.ok) {
    toastr.error("Không thể tải thông tin bệnh!");
    return;
  }

  const d = await response.json();
  document.getElementById("diseaseId").value = d.id;
  document.getElementById("diseaseName").value = d.name;
  document.getElementById("diseaseDesc").value = d.description || "";
  document.getElementById("diseaseSlug").value = d.slug || "";
  document.querySelector(".modal-title").innerText = "Cập nhật bệnh";
  $("#exampleModal").modal("show");
}

// ================= AUTO LOAD =================
document.addEventListener("DOMContentLoaded", function () {
  loadAllDiseases(0);
});

/**
 * Xuất danh sách bệnh ra CSV, áp dụng bộ lọc hiện tại.
 */
async function exportDiseases() {
  const param = document.getElementById("param").value || "";
  let url = `/api/diseases/admin/export?q=${encodeURIComponent(param)}`;

  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: new Headers({
        'Authorization': 'Bearer ' + token
      })
    });

    // Xử lý 401/403 - redirect về login nếu token hết hạn
    if (checkResponseError(response)) {
      return; // Đã redirect, không cần xử lý tiếp
    }

    if (!response.ok) {
      toastr.error("Lỗi khi xuất Excel");
      return;
    }

    const blob = await response.blob();
    const downloadUrl = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = downloadUrl;
    a.download = 'diseases_export.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(downloadUrl);
  } catch (e) {
    console.error(e);
    toastr.error("Có lỗi xảy ra khi xuất Excel");
  }
}