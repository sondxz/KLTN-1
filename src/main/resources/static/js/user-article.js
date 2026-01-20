async function loadDiseasesList() {
    const res = await fetch("/api/diseases/public/get-all-list");
    var list = await res.json();
    var main = '<option value="">Chọn công dụng</option>';
    list.forEach(s => {
        main += `<option value="${s.id}">${s.name}</option>`;
    });
    document.getElementById("diseases").innerHTML = main;
}















