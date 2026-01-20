async function loadPlantIndex() {
    var url = '/api/plant/public/cay-noi-bat-index' 
    const response = await fetch('/api/plant/public/cay-noi-bat-index' , {
    });
    var list = await response.json();

    var main = '';
    for (i = 0; i < list.length; i++) {
        main += `<div class="col-sm-6 col-lg-3">
                    <div class="card h-100 shadow-sm">
                    <img onclick="window.location.href='/plant-detail/${list[i].slug}'" class="pointer" src="${list[i].image}" alt="${list[i].name}">
                    <div class="card-body">
                        <h5 class="card-title">${list[i].name}</h5>
                        <p class="card-text">${list[i].description}</p>
                        <p class="text-muted small">${list[i].medicinalUses}</p>
                    </div>
                    <div class="card-footer bg-transparent border-0">
                        <a href="/plant-detail/${list[i].slug}" class="btn btn-outline-success w-100">Xem chi tiết</a>
                    </div>
                    </div>
                </div>`
    }
    document.getElementById("listcaynoibat").innerHTML = main
}

async function loadDiseasesSelect() {
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

async function loadFamiliesSelect() {
    const res = await fetch("/api/families/public/all-list");
    var list = await res.json();
    var main = '';
    list.forEach(s => {
        main += `<option value="${s.id}">${s.name}</option>`;
    });
    document.getElementById("families").innerHTML = main
    const ser = $("#families");
    ser.select2({
        placeholder: "Chọn các họ cây",
    });
}

var size = 8;
async function loadAllPlant(page) {
  const param = document.getElementById("param").value || "";
  const nameSearch = document.getElementById("nameSearch").value || "";
  const diseases = $("#diseases").val()
  const families = $("#families").val()
  const sort = document.getElementById("sort").value
  var url = `/api/plant/public/all?page=${page}&size=${size}&sort=${sort}`;
  var payload = {
    "search":param,
    "nameSearch":nameSearch,
    "familiesId":families,
    "diseases":diseases,
  }
  console.log(payload);
  
  const response = await fetch(url, {
    method: 'POST',
    headers: new Headers({
       'Content-Type': 'application/json'
    }),
    body: JSON.stringify(payload)
  });
  const result = await response.json();
  const list = result.content || [];
  const totalPage = result.totalPages || 0;

  let main = '';
  let mainGrid = '';
  for (let i = 0; i < list.length; i++) {
    const d = list[i];
    main += 
    `
    <div class="col-12 col-sm-6 col-lg-4 col-xl-3">
        <div class="card h-100 shadow-sm">
        <div class="ratio ratio-4x3 bg-light d-flex align-items-center justify-content-center">
            <a href="/plant-detail/${list[i].slug}"><img src="${d.image}" class="img-blog-list"></a>
        </div>
        <div class="card-body">
            <h5 class="card-title">${d.name}</h5>
            <p class="card-text text-muted mb-1">Họ: ${d.families?.name || 'Không có thông tin'}</p>
            <p class="card-text text-muted">Tên khoa học: ${d.scientificName == null || d.scientificName == '' ? 'Không có thông tin' : d.scientificName}</p>
        </div>
        <div class="card-footer bg-white">
            <a href="/plant-detail/${list[i].slug}" class="btn btn-outline-primary w-100">Xem chi tiết</a>
        </div>
        </div>
    </div>
    `;

    mainGrid += 
    `
     <div class="card shadow-sm">
        <div class="row g-0">
        <div class="col-sm-4 bg-light d-flex align-items-center justify-content-center" style="min-height:200px;">
             <a href="/plant-detail/${list[i].slug}"><img src="${d.image}" style="width:100%"></a>
        </div>
        <div class="col-sm-8">
            <div class="card-body">
            <h5 class="card-title">${d.name}</h5>
            <p class="card-text text-muted">Tên khoa học: ${d.scientificName == null || d.scientificName == '' ? 'Không có thông tin' : d.scientificName}</p>
            <div class="row mb-3">
                <div class="col-6 small text-muted">Họ: ${d.families?.name || 'Không có thông tin'}</div>
                <div class="col-6 small text-muted">Chi: ${d.genus == null || d.genus == '' ? 'Không có thông tin' : d.genus}</div>
                <div class="col-6 small text-muted">Bộ phận dùng: ${d.partsUsed == null || d.partsUsed == '' ? 'Không có thông tin' : d.partsUsed}</div>
            </div>
            <div class="text-end">
                <a href="/plant-detail/${list[i].slug}" class="btn btn-outline-primary">Xem chi tiết</a>
            </div>
            </div>
        </div>
        </div>
    </div>
    `
  }
  document.getElementById("listData").innerHTML = main;
  document.getElementById("listDataGrid").innerHTML = mainGrid;


  renderPagination(page, totalPage)
}

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

function clickSelect(){
    $("#diseases").select2('open');
    $("#families").select2('open');
    $("#diseases").select2('close');
    $("#families").select2('close');
}