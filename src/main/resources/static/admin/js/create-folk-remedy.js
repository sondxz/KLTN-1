let quillUsage = null;
let quillPreparation = null;
let quillContraindication = null;

function initEditors() {
  if (!quillUsage) {
      quillUsage = new Quill("#editor-usage", {
        theme: "snow",
        placeholder: "Nhập hướng dẫn sử dụng...",
        modules: { toolbar: [ [{ header: [1, 2, 3, false] }], ["bold", "italic", "underline"], [{ list: "ordered" }, { list: "bullet" }], ["clean"] ] }
      });
  }
  if (!quillPreparation) {
      quillPreparation = new Quill("#editor-preparation", {
        theme: "snow",
        placeholder: "Nhập cách bào chế...",
        modules: { toolbar: [ [{ header: [1, 2, 3, false] }], ["bold", "italic", "underline"], [{ list: "ordered" }, { list: "bullet" }], ["clean"] ] }
      });
  }
  if (!quillContraindication) {
      quillContraindication = new Quill("#editor-contraindication", {
        theme: "snow",
        placeholder: "Nhập chống chỉ định...",
        modules: { toolbar: [ [{ header: [1, 2, 3, false] }], ["bold", "italic", "underline"], [{ list: "ordered" }, { list: "bullet" }], ["clean"] ] }
      });
  }
}

async function loadDiseasesList() {
  const res = await fetch("/api/diseases/public/get-all-list");
  const list = await res.json();
  let main = '';
  list.forEach(s => {
    main += `<option value="${s.id}">${s.name}</option>`;
  });
  document.getElementById("diseases").innerHTML = main;
}

async function loadPlantsList() {
  const res = await fetch("/api/plant/public/all-list");
  if(res.ok) {
    const list = await res.json();
    let main = '';
    list.forEach(p => {
        main += `<option value="${p.id}">${p.name}</option>`;
    });
    document.getElementById("plants").innerHTML = main;
  }
}

async function loadRemedyData() {
    initEditors();
    var uls = new URL(document.URL);
    var id = uls.searchParams.get("id");
    if (id) {
        var url = '/api/folk-remedies/admin/detail?id=' + id;
        const response = await fetch(url, {
            method: 'GET',
            headers: new Headers({ 'Authorization': 'Bearer ' + token })
        });
        
        if(response.ok) {
            var result = await response.json();
            document.getElementById("name").value = result.name || "";
            document.getElementById("slug").value = result.slug || "";
            document.getElementById("description").value = result.description || "";
            document.getElementById("source").value = result.source || "";
            document.getElementById("status").value = result.status || "pending";
            
            if (quillUsage) quillUsage.root.innerHTML = result.usageInstruction || '';
            if (quillPreparation) quillPreparation.root.innerHTML = result.preparation || '';
            if (quillContraindication) quillContraindication.root.innerHTML = result.contraindication || '';
            
            // Set selected plants
            if (result.plants && result.plants.length > 0) {
                const pIds = result.plants.map(p => p.id.toString());
                $("#plants").val(pIds).trigger("change");
            }
            
            // Set selected diseases
            if (result.diseases && result.diseases.length > 0) {
                const dIds = result.diseases.map(d => d.id.toString());
                $("#diseases").val(dIds).trigger("change");
            }
        }
    }
}

async function saveRemedy() {
  const uls = new URL(document.URL);
  const id = uls.searchParams.get("id");
  
  const name = $("#name").val().trim();
  const slug = $("#slug").val().trim();
  const description = $("#description").val().trim();
  const source = $("#source").val().trim();
  const status = $("#status").val();
  
  const usageInstruction = quillUsage ? quillUsage.root.innerHTML : '';
  const preparation = quillPreparation ? quillPreparation.root.innerHTML : '';
  const contraindication = quillContraindication ? quillContraindication.root.innerHTML : '';
  
  // Get selected multiple
  const plantIds = $("#plants").val() ? $("#plants").val().map(v => parseInt(v)) : [];
  const diseaseIds = $("#diseases").val() ? $("#diseases").val().map(v => parseInt(v)) : [];

  if (!name || usageInstruction === '<p><br></p>' || !usageInstruction) {
    toastr.error("Vui lòng nhập tên bài thuốc và hướng dẫn sử dụng!");
    return;
  }

  const payload = {
    name, slug, description, source, status,
    usageInstruction, preparation, contraindication,
    plantIds, diseaseIds
  };

  const url = id ? `/api/folk-remedies/admin/update?id=${id}` : `/api/folk-remedies/admin/create`;
  const method = id ? "PUT" : "POST";

  try {
    const res = await fetch(url, {
      method: method,
      headers: {
        'Authorization': 'Bearer ' + token,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });

    if (checkResponseError(res)) return;

    if (res.ok) {
        swal({
            title: "Thông báo",
            text: id ? "Cập nhật bài thuốc thành công!" : "Thêm bài thuốc mới thành công!",
            type: "success"
        }, function() {
            window.location.href = "/admin/list-folk-remedies";
        });
    } else {
      const err = await res.text();
      toastr.error("Lỗi khi lưu bài thuốc: " + err);
    }
  } catch (error) {
    console.error(error);
    toastr.error("Không thể kết nối đến server!");
  }
}
