let quillArticle = null;

function initArticleEditor() {
  if (quillArticle) return quillArticle;
  const el = document.getElementById("editor");
  if (!el || typeof Quill === "undefined") return null;
  quillArticle = new Quill(el, {
    theme: "snow",
    placeholder: "Nhập nội dung chi tiết...",
    modules: {
      toolbar: [
        [{ header: [1, 2, 3, false] }],
        ["bold", "italic", "underline", "strike"],
        [{ list: "ordered" }, { list: "bullet" }],
        ["link", "image"],
        ["clean"],
      ],
    },
  });
  return quillArticle;
}
var size = 10;

async function loadAllArticle(page) {
  const param = document.getElementById("param").value || "";
  const status = document.getElementById("status").value || "";
  var url = `/api/articles/admin/all?page=${page}&size=${size}&q=${param}`;
  if(status != ""){
    url += `&status=${status}`
  }
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
    toastr.error("Lỗi khi tải dữ liệu");
    return;
  }

  const result = await response.json();
  
  const list = result.content || [];
  console.log(list);
  
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
        <td><img src="${d.imageBanner}" class="img-table"></td>
        <td>${d.title}</td>
        <td>${d.diseases?.name}</td>
        <td>${d.excerpt}</td>
        <td>${d.user.fullname}</td>
        <td>${d.viewCount != null ? d.viewCount : 0}</td>
        <td><span class="badge" style="background:${d.color}">${d.statusLabel}</span></td>
        <td>${d.createdAt}</td>
        <td>${d.updatedAt}</td>
        <td class="text-center">
            <a href="/admin/create-article?id=${d.id}" class="btn btn-primary btn-sm" title="Sửa"><i class="fa-solid fa-pencil"></i></a>
            <button onclick="deleteArticle(${d.id})" class="btn btn-danger btn-sm " title="Xóa"><i class="fa-solid fa-xmark"></i></button>
        </td>
    </tr>`;
  }

  document.getElementById("listData").innerHTML = main;
  document.getElementById("numElm").innerText = `Đang hiển thị ${start}-${end} trong ${totalElements} kết quả`;

  // Pagination
  let pageHtml = '';
  for (let i = 1; i <= totalPage; i++) {
    pageHtml += `<li class="page-item ${i === page + 1 ? 'active' : ''}">
                   <a class="page-link" href="#" onclick="loadAllArticle(${i - 1})">${i}</a>
                 </li>`;
  }
  document.getElementById("pageable").innerHTML = pageHtml;
}

/**
 * Xuất danh sách bài viết ra CSV, áp dụng bộ lọc hiện tại.
 */
async function exportArticles() {
  const param = document.getElementById("param").value || "";
  const status = document.getElementById("status").value || "";

  let url = `/api/articles/admin/export?q=${encodeURIComponent(param)}`;
  if (status !== "") {
    url += `&status=${encodeURIComponent(status)}`;
  }

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
    a.download = 'articles_export.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(downloadUrl);
  } catch (e) {
    console.error(e);
    toastr.error("Có lỗi xảy ra khi xuất Excel");
  }
}

async function loadAArticle() {
    initArticleEditor();
    var id = window.location.search.split('=')[1];
    if (id != null) {
        var url = '/api/articles/public/find-by-id?id=' + id;
        const response = await fetch(url, {
            method: 'GET'
        });
        var result = await response.json();
        document.getElementById("title").value = result.title
        document.getElementById("slug").value = result.slug
        document.getElementById("excerpt").value = result.excerpt
        document.getElementById("previewWrapper").innerHTML = `<img src="${result.imageBanner}" class="img-fluid rounded" />`
        document.getElementById("status").value = result.articleStatus
        document.getElementById("allowComments").value = result.allowComments == true?'yes':'no'
        if (quillArticle) {
          quillArticle.root.innerHTML = result.content || '';
        }
        window.uploadedImageBanner = result.imageBanner;
        document.getElementById("diseases").value = result.diseases?.id
    }
}

async function saveBV() {
  var uls = new URL(document.URL)
  var id = uls.searchParams.get("id");
  const title = $("#title").val().trim();
  const slug = $("#slug").val().trim();
  const excerpt = $("#excerpt").val().trim();
  const diseases = $("#diseases").val();
  const editorInstance = quillArticle || initArticleEditor();
  const content = editorInstance ? editorInstance.root.innerHTML : '';
  const status = $("#status").val(); // ⚠️ lưu ý: là string, ví dụ "BAN_NHAP"
  const allowComments = $("#allowComments").val() === "yes";

  // ✅ Validate cơ bản
  if (!title || !content) {
    toastr.error("Vui lòng nhập tiêu đề và nội dung bài viết!");
    return;
  }

  // ✅ 2️⃣ Tạo object bài viết
  const article = {
    id:id,
    title,
    excerpt,
    content,
    imageBanner: window.uploadedImageBanner || null, // có thể null
    articleStatus: status || "BAN_NHAP", // nếu không chọn thì mặc định là bản nháp
    allowComments,
    diseases: {id:diseases},
    slug,
  };

  console.log("📦 Gửi dữ liệu bài viết:", article);

  // ✅ 3️⃣ Gửi API lưu bài viết
  try {
    const res = await fetch("/api/articles/admin/save", {
      method: "POST",
      headers: {
        'Authorization': 'Bearer ' + token,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(article),
    });

    // Xử lý 401/403 - redirect về login nếu token hết hạn
    if (checkResponseError(res)) {
      return; // Đã redirect, không cần xử lý tiếp
    }

    if (res.status < 300) {
        swal({
            title: "Thông báo",
            text: "thêm/sửa bài viết thành công!",
            type: "success"
        },
        function() {
            window.location.href = "/admin/list-article"
        });
    } 
    else {
      const err = await res.text();
      toastr.error("Lỗi khi lưu bài viết: " + err);
    }
  } 
  catch (error) {
    console.error("❌ Lỗi khi gọi API:", error);
    toastr.error("Không thể kết nối đến server!");
  }
}


async function loadArticleStatus() {
  try {
    const res = await fetch("/api/articles/public/article-status");
    if (!res.ok) {
      throw new Error("Không thể lấy danh sách trạng thái");
    }

    const statuses = await res.json();
    const select = document.getElementById("status");
    select.innerHTML = "";
    statuses.forEach(s => {
      const opt = document.createElement("option");
      opt.value = s.name;
      opt.textContent = s.label;
      select.appendChild(opt);
    });
  } catch (error) {
    console.error("Lỗi khi tải trạng thái bài viết:", error);
    toastr.error("Không thể tải danh sách trạng thái bài viết");
  }
}

async function loadArticleStatusList() {
  var res = await fetch("/api/articles/public/article-status");
  var list = await res.json();
  var main = '<option value="">Tất cả trạng thái</option>';
  list.forEach(s => {
    main += `<option value="${s.name}">${s.label}</option>`;
  });
  document.getElementById("status").innerHTML = main
}

async function deleteArticle(id) {
    var con = confirm("Xác nhận xóa bài viết này?")
    if (con == false) {
        return;
    }
    var url = '/api/articles/admin/delete?id=' + id;
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
    
    if (response.status < 300) {
        swal({
            title: "Thông báo",
            text: "xóa bài viết thành công!",
            type: "success"
        },
        function() {
            loadAllArticle(0)
        });
    }
    if (response.status == exceptionCode) {
        toastr.warning(result.defaultMessage);
        swal({
            title: "Thông báo",
            text: result.defaultMessage,
            type: "error"
        },
        function() {
        });
    }
}

async function loadDiseasesList() {
  var res = await fetch("/api/diseases/public/get-all-list");
  var list = await res.json();
  var main = '';
  list.forEach(s => {
    main += `<option value="${s.id}">${s.name}</option>`;
  });
  document.getElementById("diseases").innerHTML = main
}
