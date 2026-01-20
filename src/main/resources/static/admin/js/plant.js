
async function loadPlantStatus() {
    const res = await fetch("/api/plant/public/all-status");
    const statuses = await res.json();
    const select = document.getElementById("status");
    select.innerHTML = "";
    statuses.forEach(s => {
        const opt = document.createElement("option");
        opt.value = s.name;
        opt.textContent = s.label;
        select.appendChild(opt);
    });
}

async function loadPlantStatusSelect() {
    const res = await fetch("/api/plant/public/all-status");
    var list = await res.json();
    var main = '<option value="">Tất cả trạng thái</option>';
    list.forEach(s => {
        main += `<option value="${s.name}">${s.label}</option>`;
    });
    document.getElementById("status").innerHTML = main
}

async function loadDiseasesSelectAdd() {
    const res = await fetch("/api/diseases/public/get-all-list");
    var list = await res.json();
    var main = '';
    list.forEach(s => {
        main += `<option value="${s.id}">${s.name}</option>`;
    });
    document.getElementById("diseases").innerHTML = main
    const ser = $("#diseases");
    ser.select2({
        placeholder: "Chọn các công dụng chữa bệnh",
    });
}

async function loadFamiliesAdd() {
    const res = await fetch("/api/families/public/all-list");
    var list = await res.json();
    var main = '';
    list.forEach(s => {
        main += `<option value="${s.id}">${s.name}</option>`;
    });
    document.getElementById("families").innerHTML = main
}

async function loadFamiliesSelect() {
    const res = await fetch("/api/families/public/all-list");
    var list = await res.json();
    var main = '<option value="">Tất cả họ thực vật</option>';
    list.forEach(s => {
        main += `<option value="${s.id}">${s.name}</option>`;
    });
    document.getElementById("families").innerHTML = main
}

var size = 10;
async function loadAllPlant(page) {
  const param = document.getElementById("param").value || "";
  const status = document.getElementById("status").value || "";
  const families = document.getElementById("families").value || "";
  var url = `/api/plant/admin/all?page=${page}&size=${size}&q=${param}`;
  if(status != ""){
    url += `&plantStatus=${status}`
  }
  if(families != ""){
    url += `&familiesId=${families}`
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
        <td><img src="${d.image}" class="img-table"></td>
        <td>${d.name}</td>
        <td>${d.scientificName}</td>
        <td>${d.families?.name}</td>
        <td>${d.partsUsed}</td>
        <td><span class="badge" style="background:${d.color}">${d.statusLabel}</span></td>
        <td>${d.createdAt}</td>
        <td>${d.updatedAt}</td>
        <td class="text-center">
            <a href="/admin/create-plant?id=${d.id}" class="btn btn-primary btn-sm" title="Sửa"><i class="fa-solid fa-pencil"></i></a>
            <button onclick="deletePlant(${d.id})" class="btn btn-danger btn-sm " title="Xóa"><i class="fa-solid fa-xmark"></i></button>
        </td>
    </tr>`;
  }

  document.getElementById("listData").innerHTML = main;
  document.getElementById("numElm").innerText = `Đang hiển thị ${start}-${end} trong ${totalElements} kết quả`;
  renderPagination(page, totalPage);
}

/**
 * Xuất danh sách cây dược liệu ra file Excel (thực tế là CSV, Excel đọc được)
 * Áp dụng các bộ lọc hiện tại (từ khóa, trạng thái, họ thực vật)
 */
async function exportPlants() {
  const param = document.getElementById("param").value || "";
  const status = document.getElementById("status").value || "";
  const families = document.getElementById("families").value || "";

  let url = `/api/plant/admin/export?q=${encodeURIComponent(param)}`;
  if (status !== "") {
    url += `&plantStatus=${encodeURIComponent(status)}`;
  }
  if (families !== "") {
    url += `&familiesId=${encodeURIComponent(families)}`;
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
    // Đây là file CSV, Excel vẫn mở được nhưng đuôi phải là .csv
    a.download = 'plants_export.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(downloadUrl);
  } catch (e) {
    console.error(e);
    toastr.error("Có lỗi xảy ra khi xuất Excel");
  }
}

async function loadAPlant() {
    var id = window.location.search.split('=')[1];
    if (id != null && id.trim() !== "") {
        document.getElementById("pageTitle").textContent = "Sửa cây dược liệu";
        document.getElementById("pageSubtitle").textContent = "Cập nhật thông tin chi tiết về cây dược liệu";
        var url = '/api/plant/public/find-by-id?id=' + id;
        const response = await fetch(url, {
            method: 'GET'
        });
        var result = await response.json();
        document.getElementById("name").value = result.name
        document.getElementById("slug").value = result.slug
        document.getElementById("scientificName").value = result.scientificName
        document.getElementById("genus").value = result.genus
        document.getElementById("otherNames").value = result.otherNames
        document.getElementById("partsUsed").value = result.partsUsed
        document.getElementById("description").value = result.description
        document.getElementById("source").value = result.source || ""
        document.getElementById("families").value = result.families.id
        const diseaseIds = result.plantDiseases?.map(pd => pd.diseases?.id) || [];
        $("#diseases").val(diseaseIds).change()
        document.getElementById("status").value = result.plantStatus
        document.getElementById("botanicalCharacteristics").value = result.botanicalCharacteristics
        document.getElementById("stem").value = result.stem
        document.getElementById("leaf").value = result.leaf
        document.getElementById("flower").value = result.flower
        document.getElementById("fruitOrSeed").value = result.fruitOrSeed
        document.getElementById("root").value = result.root
        document.getElementById("chemicalComposition").value = result.chemicalComposition
        document.getElementById("ecology").value = result.ecology
        document.getElementById("distribution").value = result.distribution || ""
        document.getElementById("altitude").value = result.altitude || ""
        document.getElementById("harvestSeason").value = result.harvestSeason || ""
        document.getElementById("medicinalUses").value = result.medicinalUses
        document.getElementById("indications").value = result.indications
        document.getElementById("contraindications").value = result.contraindications
        document.getElementById("dosage").value = result.dosage
        document.getElementById("folkRemedies").value = result.folkRemedies || ""
        document.getElementById("sideEffects").value = result.sideEffects
        if(result.image != null && result.image != ""){
            document.getElementById("previewWrapperMain").innerHTML = `<img src="${result.image}" class="img-fluid rounded" />`
            window.ImageBanner = result.image
            // Hiển thị nút xóa ảnh chính
            const btnRemoveMainImage = document.getElementById("btn-remove-main-image");
            if (btnRemoveMainImage) {
                btnRemoveMainImage.style.display = 'inline-block';
                btnRemoveMainImage.disabled = false;
            }
        } else {
            document.getElementById("previewWrapperMain").innerHTML = ""
            window.ImageBanner = null
            // Ẩn nút xóa ảnh chính nếu không có ảnh
            const btnRemoveMainImage = document.getElementById("btn-remove-main-image");
            if (btnRemoveMainImage) {
                btnRemoveMainImage.style.display = 'none';
            }
        }
        if(result.linkDocument != null && result.linkDocument != ""){
            document.getElementById("noti-choosefile").innerHTML = `Có 1 file được chọn - <a href="${result.linkDocument}" download="download">Tải xuống<a>`
            const btnRemoveDoc = document.getElementById("btn-remove-doc");
            if (btnRemoveDoc) {
                btnRemoveDoc.style.display = 'inline-block';
                btnRemoveDoc.disabled = false;
            }
            // lưu lại link hiện tại nếu chưa có (phòng khi load trang sửa mà chưa upload mới)
            if (!window.documentPlant) {
                window.documentPlant = result.linkDocument;
            }
        } else {
            const btnRemoveDoc = document.getElementById("btn-remove-doc");
            if (btnRemoveDoc) {
                btnRemoveDoc.style.display = 'none';
            }
        }
        document.getElementById("anhdathem").style.display = 'block'
        var main = ''
        for (i = 0; i < result.plantMedia.length; i++) {
            main += `<div id="imgdathem${result.plantMedia[i].id}" class="col-xl-2 col-lg-3 col-md-3 col-sm-4 col-4">
                        <img style="width: 100%;" src="${result.plantMedia[i].imageLink}" class="image-upload">
                        <button onclick="deleteImageDT(${result.plantMedia[i].id})" class="btn btn-danger form-control">Xóa ảnh</button>
                    </div>`
        }
        document.getElementById("listanhdathem").innerHTML = main
    }
}



async function savePlant() {
    var uls = new URL(document.URL)
    var idPlant = uls.searchParams.get("id");
    var plantId = null;
    if (idPlant != null && idPlant.trim() !== "" && !isNaN(idPlant)) {
        plantId = parseInt(idPlant);
    }
    console.log("Plant ID from URL:", idPlant, "Parsed ID:", plantId);
    var dto = 
    {
        plant:{
            name:document.getElementById("name").value,
            scientificName:document.getElementById("scientificName").value,
            slug:document.getElementById("slug").value,
            genus:document.getElementById("genus").value,
            otherNames:document.getElementById("otherNames").value,
            partsUsed:document.getElementById("partsUsed").value,
            description:document.getElementById("description").value,
            botanicalCharacteristics:document.getElementById("botanicalCharacteristics").value,
            chemicalComposition:document.getElementById("chemicalComposition").value,
            ecology:document.getElementById("ecology").value,
            medicinalUses:document.getElementById("medicinalUses").value,
            indications:document.getElementById("indications").value,
            contraindications:document.getElementById("contraindications").value,
            dosage:document.getElementById("dosage").value,
            folkRemedies:document.getElementById("folkRemedies").value,
            sideEffects:document.getElementById("sideEffects").value,
            plantStatus:document.getElementById("status").value,
            stem:document.getElementById("stem").value,
            leaf:document.getElementById("leaf").value,
            flower:document.getElementById("flower").value,
            fruitOrSeed:document.getElementById("fruitOrSeed").value,
            root:document.getElementById("root").value,
            distribution:document.getElementById("distribution").value,
            altitude:document.getElementById("altitude").value,
            harvestSeason:document.getElementById("harvestSeason").value,
            source:document.getElementById("source").value,
            linkDocument:window.documentPlant,
            image:window.ImageBanner,
            families:document.getElementById("families").value ? {
                id:parseInt(document.getElementById("families").value),
            } : null
        },
        images: uploadedImages,
        diseasesIds: $("#diseases").val()
    }
    
    if (plantId != null) {
        dto.plant.id = plantId;
    }
    
    console.log("DTO to send:", JSON.stringify(dto, null, 2));

    const url = plantId != null ? `/api/plant/admin/update` : `/api/plant/admin/create`;
    const method = plantId != null ? 'PUT' : 'POST';
    console.log("Request URL:", url, "Method:", method);

    const response = await fetch(url, {
        method: method,
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
                text: "thêm/sửa cây thành công!",
                type: "success"
            },
            function() {
                window.location.href = '/admin/list-plant'
            });
    }
    else {
        // Đọc thông báo lỗi từ response body
        let errorMessage = "Có lỗi xảy ra: " + response.status;
        try {
            // Đọc text trước (vì response body chỉ đọc được 1 lần)
            const text = await response.text();
            
            if (text && text.trim()) {
                // Thử parse JSON
                try {
                    const result = JSON.parse(text);
                    if (result.defaultMessage) {
                        errorMessage = result.defaultMessage;
                    } else if (result.message) {
                        errorMessage = result.message;
                    } else if (result.error) {
                        errorMessage = result.error;
                    } else {
                        errorMessage = text;
                    }
                } catch (parseError) {
                    // Không phải JSON, dùng text trực tiếp
                    errorMessage = text;
                }
            }
        } catch (e) {
            console.error("Error reading error response:", e);
        }
        
        // Hiển thị thông báo lỗi
        if (response.status == 400 || response.status == 417) {
            toastr.warning(errorMessage);
        } else {
            toastr.error(errorMessage);
        }
    }
}



async function deletePlant(id) {
    var con = confirm("Xác nhận xóa cây dược liệu này?")
    if (con == false) {
        return;
    }
    var url = '/api/plant/admin/delete?id=' + id;
    try {
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
                text: "Xóa cây dược liệu thành công!",
                type: "success"
            },
            function() {
                loadAllPlant(0)
            });
        } else {
            // Xử lý lỗi - parse JSON response
            var errorMessage = "Xóa cây dược liệu thất bại";
            try {
                var result = await response.json();
                if (result.defaultMessage) {
                    errorMessage = result.defaultMessage;
                } else if (result.message) {
                    errorMessage = result.message;
                } else if (typeof result === 'string') {
                    errorMessage = result;
                }
            } catch (e) {
                // Nếu không parse được JSON, dùng message mặc định
                if (response.status === 417) {
                    errorMessage = "Không thể xóa cây dược liệu này. Vui lòng kiểm tra lại.";
                } else if (response.status === 404) {
                    errorMessage = "Không tìm thấy cây dược liệu.";
                } else if (response.status === 500) {
                    errorMessage = "Lỗi server. Vui lòng thử lại sau.";
                }
            }
            
            // Hiển thị thông báo lỗi
            if (response.status == exceptionCode || response.status === 417) {
                toastr.warning(errorMessage);
            } else {
                toastr.error(errorMessage);
            }
            
            swal({
                title: "Thông báo",
                text: errorMessage,
                type: "error"
            });
        }
    } catch (error) {
        console.error("Error deleting plant:", error);
        toastr.error("Có lỗi xảy ra khi xóa cây dược liệu. Vui lòng thử lại sau.");
        swal({
            title: "Lỗi",
            text: "Có lỗi xảy ra khi xóa cây dược liệu. Vui lòng thử lại sau.",
            type: "error"
        });
    }
}



async function deleteImageDT(id) {
    var con = confirm("Bạn muốn xóa ảnh này?");
    if (con == false) {
        return;
    }
    var url = '/api/plant/admin/delete-image?id=' + id;
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
        toastr.success("xóa ảnh thành công!");
        document.getElementById("imgdathem" + id).style.display = 'none';
    }
    if (response.status == exceptionCode) {
        var result = await response.json()
        toastr.warning(result.defaultMessage);
    }
}

const uploadedImages = []; // lưu link ảnh đã upload
let uploading = false;     // trạng thái đang upload

function initImageUpload() {
    const chooseInput = document.getElementById('choosefile');
    const previewContainer = document.getElementById('preview');
    const btnSave = $("#btnSave");

    // Kiểm tra các element có tồn tại không (có thể không có trên một số trang)
    if (!chooseInput || !previewContainer) {
        console.log("Image upload elements not found, skipping initialization");
        return;
    }

    // =================== add preview =====================
    function addPreview(file, idx) {
        const div = document.createElement('div');
        div.className = 'col-md-3 col-sm-6 col-6 mb-2';
        div.style.height = '150px';
        div.style.position = 'relative';
        previewContainer.appendChild(div);

        // image
        const img = document.createElement('img');
        img.src = URL.createObjectURL(file);
        img.style.height = '100px';
        img.style.width = '100%';
        img.className = 'img-thumbnail';
        div.appendChild(img);

        // progress bar
        const progressWrapper = document.createElement('div');
        progressWrapper.className = 'progress mt-1';
        progressWrapper.style.height = '6px';
        div.appendChild(progressWrapper);

        const progress = document.createElement('div');
        progress.className = 'progress-bar progress-bar-striped progress-bar-animated';
        progress.style.width = '0%';
        progressWrapper.appendChild(progress);

        // button xóa
        const btn = document.createElement('button');
        btn.className = 'btn btn-danger btn-sm';
        btn.innerText = 'Xóa';
        btn.style.position = 'absolute';
        btn.style.bottom = '5px';
        btn.style.left = '5px';
        div.appendChild(btn);

        btn.addEventListener('click', () => {
            div.remove();
            // xóa ảnh khỏi array tạm
            uploadedImages.splice(idx, 1);
        });

        return {progressEl: progress, divEl: div};
    }

    // =================== handle upload =====================
    async function handleFiles(files) {
        if (!files.length) return;

        uploading = true;
        btnSave.prop("disabled", true).text("Đang tải ảnh... ⏳");

        // show preview và khởi tạo vị trí trong array tạm
        const previewElements = [];
        for (let i = 0; i < files.length; i++) {
            uploadedImages.push(null); // placeholder
            previewElements.push(addPreview(files[i], i));
        }

        const formData = new FormData();
        files.forEach(f => formData.append("file", f));

        try {
            const res = await fetch("/api/public/upload-multiple-file-order-response", {
                method: "POST",
                body: formData
            });

            if (!res.ok) throw new Error("Upload thất bại");

            const data = await res.json(); // [{id:0, link:"..."}, ...] đã đúng thứ tự
            data.forEach(item => {
                uploadedImages[item.id] = item.link;
                // update progress full
                const progress = previewElements[item.id].progressEl;
                progress.style.width = '100%';
                progress.classList.remove('progress-bar-animated', 'progress-bar-striped');

                // update img src sang link thực tế nếu muốn
                const imgEl = previewElements[item.id].divEl.querySelector('img');
                imgEl.src = item.link;
            });

        } catch (err) {
            console.error("Upload lỗi:", err);
            data?.forEach(item => {
                const progress = previewElements[item.id]?.progressEl;
                if (progress) {
                    progress.style.backgroundColor = '#dc3545';
                    progress.style.width = '100%';
                    progress.classList.remove('progress-bar-animated', 'progress-bar-striped');
                }
            });
            toastr.error("Tải ảnh thất bại!");
        } finally {
            uploading = false;
            btnSave.prop("disabled", false).text("Lưu");
        }
    }

    // =================== event =====================
    chooseInput.addEventListener('change', (e) => {
        const files = Array.from(e.target.files);
        handleFiles(files);
        chooseInput.value = '';
    });

    // Drag & drop
    previewContainer.addEventListener('dragover', (e) => {
        e.preventDefault();
        previewContainer.classList.add('border-primary');
    });
    previewContainer.addEventListener('dragleave', (e) => {
        e.preventDefault();
        previewContainer.classList.remove('border-primary');
    });
    previewContainer.addEventListener('drop', (e) => {
        e.preventDefault();
        previewContainer.classList.remove('border-primary');
        const files = Array.from(e.dataTransfer.files);
        handleFiles(files);
    });

    // Copy paste
    document.addEventListener('paste', (e) => {
        const items = e.clipboardData.items;
        const files = [];
        for (let i = 0; i < items.length; i++) {
            if (items[i].kind === 'file') {
                files.push(items[i].getAsFile());
            }
        }
        if (files.length) handleFiles(files);
    });
}

$(document).ready(function() {
    initImageUpload();

    // Xóa tài liệu hiện tại
    const btnRemoveDoc = document.getElementById('btn-remove-doc');
    if (btnRemoveDoc) {
        btnRemoveDoc.addEventListener('click', function () {
            if (!confirm('Bạn có chắc muốn xóa tài liệu hiện tại của cây này?')) {
                return;
            }
            // Đánh dấu xóa tài liệu: gửi chuỗi rỗng lên backend
            window.documentPlant = "";
            const noti = document.getElementById('noti-choosefile');
            if (noti) {
                noti.innerText = 'Tài liệu sẽ được xóa sau khi bạn bấm Lưu.';
            }
            btnRemoveDoc.style.display = 'none';
        });
    }
});


function renderPagination(currentPage, totalPages) {
    let maxVisiblePages = 3; // số trang hiển thị chính giữa
    let mainpage = '';

    // nút prev
    if (currentPage > 0) {
        mainpage += `<li class="page-item pointer" onclick="loadAllPlant(${currentPage - 1})"><a class="page-link">&laquo;</a></li>`;
    }

    // trang đầu
    if (currentPage > 1) {
        mainpage += `<li class="page-item pointer" onclick="loadAllPlant(0)"><a class="page-link">1</a></li>`;
    }

    // dấu ...
    if (currentPage > 2) {
        mainpage += `<li class="page-item disabled"><a class="page-link">...</a></li>`;
    }

    // trang ở giữa
    for (let i = currentPage - 1; i <= currentPage + 1; i++) {
        if (i >= 0 && i < totalPages) {
            mainpage += `<li onclick="loadAllPlant(${i})" class="page-item pointer ${i === currentPage ? 'active' : ''}"><a class="page-link">${i + 1}</a></li>`;
        }
    }

    // dấu ...
    if (currentPage < totalPages - 3) {
        mainpage += `<li class="page-item disabled"><a class="page-link">...</a></li>`;
    }

    // trang cuối
    if (currentPage < totalPages - 2) {
        mainpage += `<li class="page-item pointer" onclick="loadAllPlant(${totalPages - 1})"><a class="page-link">${totalPages}</a></li>`;
    }

    // nút next
    if (currentPage < totalPages - 1) {
        mainpage += `<li class="page-item pointer" onclick="loadAllPlant(${currentPage + 1})"><a class="page-link">&raquo;</a></li>`;
    }

    document.getElementById("pageable").innerHTML = mainpage;
}