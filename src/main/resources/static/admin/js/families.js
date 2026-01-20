var size = 10;

// ================= LOAD DANH SÁCH =================
async function loadAllFamilies(page) {
  const param = document.getElementById("param").value || "";
  const url = `/api/families/public/all?page=${page}&size=${size}&search=${param}`;

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
    toastr.error("Lỗi khi tải dữ liệu họ thực vật");
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
    const f = list[i];
    main += `
      <tr>
        <td>${f.id}</td>
        <td>${f.name}</td>
        <td>${f.description || ""}</td>
        <td class="text-center">
          <button class="btn btn-sm btn-primary me-1" onclick="editFamily(${f.id})"><i class="fa-solid fa-pencil"></i></button>
          <button class="btn btn-sm btn-danger" onclick="deleteFamily(${f.id})"><i class="fa-solid fa-xmark"></i></button>
        </td>
      </tr>`;
  }
  document.getElementById("listFamilies").innerHTML = main;
  document.getElementById("numElm").innerText = `Đang hiển thị ${start}-${end} trong ${totalElements} kết quả`;

  // Pagination
  let pageHtml = '';
  for (let i = 1; i <= totalPage; i++) {
    pageHtml += `<li class="page-item ${i === page + 1 ? 'active' : ''}">
                   <a class="page-link" href="#" onclick="loadAllFamilies(${i - 1})">${i}</a>
                 </li>`;
  }
  document.getElementById("pageable").innerHTML = pageHtml;
}

// ================= THÊM / CẬP NHẬT =================
async function saveFamily() {
  const id = document.getElementById("familyId").value;
  const name = document.getElementById("familyName").value.trim();
  const description = document.getElementById("familyDesc").value.trim();
  const slug = document.getElementById("familySlug").value.trim();

  if (name === "") {
    toastr.warning("Tên họ thực vật không được bỏ trống!");
    return;
  }

  const data = { id, name, description, slug };
  

  const url = `/api/families/admin/create`;

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
      text: "Lưu thông tin họ thực vật thành công!",
      type: "success"
    }, function () {
      loadAllFamilies(0);
      $("#exampleModal").modal("hide")
    });
  } else if (response.status === exceptionCode) {
    toastr.error(result.defaultMessage || "Lỗi dữ liệu!");
  } else {
    toastr.error("Không thể lưu họ thực vật!");
  }
}

// ================= XÓA =================
async function deleteFamily(id) {
  const confirmDel = confirm("Bạn có chắc muốn xóa họ thực vật này?");
  if (!confirmDel) return;

  const url = `/api/families/admin/delete?id=${id}`;

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
      text: "Họ thực vật đã được xóa thành công!",
      type: "success"
    }, function () {
      loadAllFamilies(0);
    });
  } else {
    toastr.error("Không thể xóa họ thực vật!");
  }
}

// ================= SỬA =================
async function editFamily(id) {
  const url = `/api/families/public/find-by-id?id=${id}`;
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
    toastr.error("Không thể tải thông tin họ thực vật!");
    return;
  }

  const f = await response.json();
  document.getElementById("familyId").value = f.id;
  document.getElementById("familyName").value = f.name;
  document.getElementById("familyDesc").value = f.description || "";
  document.getElementById("familySlug").value = f.slug || "";
  document.querySelector(".modal-title").innerText = "Cập nhật họ thực vật";
  $("#exampleModal").modal("show")
}

// ================= AUTO LOAD =================
document.addEventListener("DOMContentLoaded", function () {
  loadAllFamilies(0);
});

/**
 * Xuất danh sách họ thực vật ra CSV, áp dụng bộ lọc hiện tại.
 */
async function exportFamilies() {
  const param = document.getElementById("param").value || "";
  let url = `/api/families/admin/export?search=${encodeURIComponent(param)}`;

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
    a.download = 'families_export.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(downloadUrl);
  } catch (e) {
    console.error(e);
    toastr.error("Có lỗi xảy ra khi xuất Excel");
  }
}