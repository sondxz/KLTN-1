async function loadDiseasesSelect() {
    const res = await fetch("/api/diseases/public/get-all-list");
    var list = await res.json();
    var main = '';
    list.forEach(s => {
        main += `<option value="${s.id}">${s.name}</option>`;
    });
    document.getElementById("diseases").innerHTML = main;
    $("#diseases").select2({
        placeholder: "Chọn các công dụng chữa bệnh",
    });
}

async function loadFamiliesSelect() {
    try {
        // Lấy danh sách tất cả họ cây
        const res = await fetch("/api/families/public/all-list");
        var list = await res.json();
        
        if (!list || list.length === 0) {
            console.error("Không có họ cây nào trong danh sách");
            return;
        }
        
        // Tìm họ cây mặc định "Chưa phân loại" trong danh sách
        let defaultFamilyId = null;
        const defaultFamilyName = "Chưa phân loại";
        
        // Tìm theo tên chính xác hoặc gần giống
        const defaultFamily = list.find(f => 
            f.name === defaultFamilyName || 
            f.name.toLowerCase().includes("chưa phân loại") ||
            f.name.toLowerCase().includes("chua phan loai")
        );
        
        if (defaultFamily) {
            defaultFamilyId = defaultFamily.id;
        } else {
            // Nếu không tìm thấy, chọn họ cây đầu tiên
            defaultFamilyId = list[0].id;
        }
        
        var main = '';
        list.forEach(s => {
            const isDefault = s.id === defaultFamilyId;
            main += `<option value="${s.id}" ${isDefault ? 'selected' : ''}>${s.name}${isDefault ? ' (Mặc định)' : ''}</option>`;
        });
        
        document.getElementById("families").innerHTML = main;
        
        // Đảm bảo giá trị được set
        if (defaultFamilyId) {
            document.getElementById("families").value = defaultFamilyId;
        }
        
        console.log("Đã load họ cây, mặc định chọn:", defaultFamilyId);
    } catch (error) {
        console.error("Lỗi khi load họ cây:", error);
        // Fallback: lấy danh sách và chọn cái đầu tiên
        try {
            const res = await fetch("/api/families/public/all-list");
            var list = await res.json();
            var main = '';
            list.forEach(s => {
                main += `<option value="${s.id}">${s.name}</option>`;
            });
            document.getElementById("families").innerHTML = main;
            if (list.length > 0) {
                document.getElementById("families").value = list[0].id;
                console.log("Fallback: chọn họ cây đầu tiên:", list[0].id);
            }
        } catch (e) {
            console.error("Lỗi khi load danh sách họ cây:", e);
        }
    }
}













