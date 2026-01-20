let quillResearch = null;

function initResearchEditor() {
    if (quillResearch) return quillResearch;
    const el = document.getElementById("editor");
    if (!el || typeof Quill === "undefined") return null;
    quillResearch = new Quill(el, {
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
    return quillResearch;
}
var size = 10;

async function loadAllResearch(page) {
  const param = document.getElementById("param").value || "";
  const status = document.getElementById("status").value || "";
  var url = `/api/research/admin/all?page=${page}&size=${size}&q=${param}`;
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
        <td><img src="${d.imageBanner}" class="img-table"></td>
        <td>${d.title}</td>
        <td>${d.authors}</td>
        <td>${d.publishedYear}</td>
        <td><span class="badge" style="background:${d.color}">${d.statusLabel}</span></td>
        <td>${d.createdAt}</td>
        <td>${d.updatedAt}</td>
        <td class="text-center">
            <a href="/admin/create-research?id=${d.id}" class="btn btn-primary btn-sm" title="Sửa"><i class="fa-solid fa-pencil"></i></a>
            <button onclick="deleteResearch(${d.id})" class="btn btn-danger btn-sm " title="Xóa"><i class="fa-solid fa-xmark"></i></button>
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
 * Xuất danh sách nghiên cứu ra CSV, áp dụng bộ lọc hiện tại.
 */
async function exportResearch() {
  const param = document.getElementById("param").value || "";
  const status = document.getElementById("status").value || "";

  let url = `/api/research/admin/export?q=${encodeURIComponent(param)}`;
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
    a.download = 'research_export.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(downloadUrl);
  } catch (e) {
    console.error(e);
    toastr.error("Có lỗi xảy ra khi xuất Excel");
  }
}

async function loadResearchStatusList() {
  var res = await fetch("/api/research/public/research-status");
  var list = await res.json();
  var main = '<option value="">Tất cả trạng thái</option>';
  list.forEach(s => {
    main += `<option value="${s.name}">${s.label}</option>`;
  });
  document.getElementById("status").innerHTML = main
}


async function loadResearchStatus() {
  var res = await fetch("/api/research/public/research-status");
  var list = await res.json();
  var main = '';
  list.forEach(s => {
    main += `<option value="${s.name}">${s.label}</option>`;
  });
  document.getElementById("status").innerHTML = main
}

async function loadPlants() {
    const res = await fetch("/api/plant/admin/all-name", {
        method: 'GET',
        headers: new Headers({
        'Authorization': 'Bearer ' + token
        })
    });
    
    // Xử lý 401/403 - redirect về login nếu token hết hạn
    if (checkResponseError(res)) {
        return; // Đã redirect, không cần xử lý tiếp
    }
    
    var list = await res.json();
    var main = '';
    list.forEach(s => {
        main += `<option value="${s.id}">${s.name}</option>`;
    });
    document.getElementById("plants").innerHTML = main
    const ser = $("#plants");
    ser.select2({
        placeholder: "Chọn các cây dược liệu",
    });
}

async function loadExperts() {
    try {
        const res = await fetch("/api/expert/admin/all?size=1000", {
            method: 'GET',
            headers: new Headers({
                'Authorization': 'Bearer ' + token
            })
        });
        
        // Xử lý 401/403 - redirect về login nếu token hết hạn
        if (checkResponseError(res)) {
            return; // Đã redirect, không cần xử lý tiếp
        }
        
        if (!res.ok) {
            console.error("Error loading experts:", res.status, res.statusText);
            toastr.error("Không thể tải danh sách chuyên gia");
            return;
        }
        
        var result = await res.json();
        console.log("Experts API response:", result);
        
        var list = result.content || [];
        console.log("Experts list:", list);
        
        if (list.length === 0) {
            console.warn("No experts found in system");
            document.getElementById("experts").innerHTML = '<option value="">Không có chuyên gia nào trong hệ thống</option>';
            return;
        }
        
        var main = '';
        list.forEach(s => {
            if (s && s.id && s.name) {
                main += `<option value="${s.id}">${s.name}${s.title ? ' - ' + s.title : ''}</option>`;
            }
        });
        
        document.getElementById("experts").innerHTML = main;
        
        // Khởi tạo select2 sau khi đã có options
        const ser = $("#experts");
        ser.select2({
            placeholder: "Chọn các chuyên gia làm tác giả",
            allowClear: true,
            width: '100%'
        });
        
        console.log("Experts loaded successfully:", list.length, "experts");
    } catch (error) {
        console.error("Error in loadExperts:", error);
        toastr.error("Lỗi khi tải danh sách chuyên gia: " + error.message);
    }
}

async function deleteResearch(id) {
    var con = confirm("Xác nhận xóa nghiên cứu này?")
    if (con == false) {
        return;
    }
    var url = '/api/research/admin/delete?id=' + id;
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
            text: "xóa nghiên cứu thành công!",
            type: "success"
        },
        function() {
            loadAllResearch(0)
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


async function saveReseach() {
    const editorInstance = initResearchEditor();
    var uls = new URL(document.URL)
    var idPlant = uls.searchParams.get("id");
    var dto = 
    {
        research:{
            id:idPlant,
            title:document.getElementById("title").value,
            slug:document.getElementById("slug").value,
            abstractText:document.getElementById("abstractText").value,
            content: editorInstance ? editorInstance.root.innerHTML : '',
            authors:null, // Sẽ được set từ authorsText
            institution:document.getElementById("institution").value,
            publishedYear:document.getElementById("publishedYear").value,
            journal:document.getElementById("journal").value,
            field:document.getElementById("field").value,
            researchStatus:document.getElementById("status").value,
            linkDocument:window.documentPlant,
            imageBanner:window.uploadedImageBanner,
        },
        plantId: $("#plants").val(),
        expertIds: $("#experts").val() || [],
        authorsText: document.getElementById("authors").value.trim() || null
    }

    const response = await fetch(`/api/research/admin/save`, {
        method: 'POST',
        headers: new Headers({
            'Authorization': 'Bearer ' + token,
            'Content-Type': 'application/json'
        }),
        body: JSON.stringify(dto)
    });
    
    // Xử lý 401/403 - redirect về login nếu token hết hạn
    if (checkResponseError(response)) {
        return; // Đã redirect, không cần xử lý tiếp
    }
    
    if (response.status < 300) {
        swal({
                title: "Thông báo",
                text: "thêm/sửa nghiên cứu thành công!",
                type: "success"
            },
            function() {
                window.location.href = '/admin/list-research'
            });
    }
    else if (response.status == exceptionCode) {
        var result = await response.json()
        toastr.warning(result.defaultMessage);
    }
    else{
        toastr.error("Có lỗi xảy ra: "+response.status);
    }
}


async function loadAResearch() {
    initResearchEditor();
    var id = window.location.search.split('=')[1];
    if (id != null) {
        var url = '/api/research/public/find-by-id?id=' + id;
        const response = await fetch(url, {
            method: 'GET'
        });
        var result = await response.json();
        document.getElementById("title").value = result.title
        document.getElementById("slug").value = result.slug
        document.getElementById("authors").value = result.authors || ''
        document.getElementById("abstractText").value = result.abstractText
        document.getElementById("journal").value = result.journal
        document.getElementById("institution").value = result.institution
        document.getElementById("publishedYear").value = result.publishedYear
        document.getElementById("field").value = result.field
        document.getElementById("field").value = result.field
        document.getElementById("previewWrapper").innerHTML = `<img src="${result.imageBanner}" class="img-fluid rounded" />`
        document.getElementById("status").value = result.researchStatus
        if (quillResearch) {
            quillResearch.root.innerHTML = result.content || '';
        }
        window.uploadedImageBanner = result.imageBanner;
        window.documentPlant = result.linkDocument;
        if(result.linkDocument != null){
            document.getElementById("noti-choosefile").innerText = 'Đã có 1 tài liệu được upload'
        }
        const plantIds = result.researchPlants?.map(pd => pd.plant?.id) || [];
        $("#plants").val(plantIds).change()
        
        // Load experts
        const expertIds = result.researchExperts?.map(re => re.expert?.id).filter(id => id != null) || [];
        $("#experts").val(expertIds).change()
    }
}
