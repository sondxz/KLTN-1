var size = 10;

const quillExpertEditors = {
    education: null,
    bio: null,
    achievements: null,
};

function initExpertEditors() {
    const toolbar = [
        [{ header: [1, 2, 3, false] }],
        ["bold", "italic", "underline", "strike"],
        [{ list: "ordered" }, { list: "bullet" }],
        ["link"],
        ["clean"],
    ];
    if (typeof Quill === "undefined") return;
    const eduEl = document.getElementById("educationEditor");
    const bioEl = document.getElementById("bioEditor");
    const achEl = document.getElementById("achievementsEditor");
    if (eduEl && !quillExpertEditors.education) {
        quillExpertEditors.education = new Quill(eduEl, { theme: "snow", placeholder: "Nhập quá trình học vấn...", modules: { toolbar } });
    }
    if (bioEl && !quillExpertEditors.bio) {
        quillExpertEditors.bio = new Quill(bioEl, { theme: "snow", placeholder: "Nhập tiểu sử chi tiết...", modules: { toolbar } });
    }
    if (achEl && !quillExpertEditors.achievements) {
        quillExpertEditors.achievements = new Quill(achEl, { theme: "snow", placeholder: "Nhập các thành tựu nổi bật...", modules: { toolbar } });
    }
}

async function loadAllExpert(page) {
    const param = document.getElementById("param").value || "";
    var url = `/api/expert/admin/all?page=${page}&size=${size}&q=${param}`;
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
        toastr.error("Lỗi khi tải dữ liệu chuyên gia");
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
    if (list.length === 0) {
        main = '<tr><td colspan="7" class="text-center text-muted py-4">Không có chuyên gia nào</td></tr>';
    } else {
        for (let i = 0; i < list.length; i++) {
            const d = list[i];
            const avatarUrl = d.avatar || 'https://via.placeholder.com/50x50?text=No+Image';
            main += `
              <tr>
                <td>${d.id || ''}</td>
                <td><img src="${avatarUrl}" class="img-table" style="width: 50px; height: 50px; object-fit: cover;"></td>
                <td>${d.name || ''}</td>
                <td>Email: ${d.contactEmail || d.email || 'N/A'}<br>SĐT: ${d.phone || 'N/A'}</td>
                <td>${d.specialization || ''}</td>
                <td>${d.institution || ''}</td>
                <td class="text-center">
                    <a href="/admin/create-expert?id=${d.id}" class="btn btn-primary btn-sm" title="Sửa"><i class="fa-solid fa-pencil"></i></a>
                    <button onclick="deleteExpert(${d.id})" class="btn btn-danger btn-sm " title="Xóa"><i class="fa-solid fa-xmark"></i></button>
                </td>
            </tr>`;
        }
    }

    document.getElementById("listData").innerHTML = main;
    document.getElementById("numElm").innerText = `Đang hiển thị ${start}-${end} trong ${totalElements} kết quả`;

    // Pagination
    let pageHtml = '';
    for (let i = 1; i <= totalPage; i++) {
        pageHtml += `<li class="page-item ${i === page + 1 ? 'active' : ''}">
                   <a class="page-link" href="#" onclick="loadAllExpert(${i - 1})">${i}</a>
                 </li>`;
    }
    document.getElementById("pageable").innerHTML = pageHtml;
}

async function loadAExpert() {
    initExpertEditors();
    var id = window.location.search.split('=')[1];

    if (id != null) {
        var url = '/api/expert/public/find-by-id?id=' + id;

        try {
            const response = await fetch(url, {
                method: 'GET'
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            var result = await response.json();

            // Lưu ID của chuyên gia vào biến toàn cục để dùng khi cập nhật
            window.currentExpertId = result.id;

            // Lưu URL avatar vào biến toàn cục
            window.uploadedExpertAvatar = result.avatar;

            // =======================================================
            // KHỞI TẠO GIÁ TRỊ CÁC TRƯỜNG FORM
            // =======================================================

            // --- Thông tin cơ bản ---
            document.getElementById("expertName").value = result.name || '';
            document.getElementById("slug").value = result.slug || '';
            document.getElementById("expertTitle").value = result.title || '';
            document.getElementById("specialization").value = result.specialization || '';
            document.getElementById("institution").value = result.institution || '';

            // --- Thông tin Liên hệ ---
            document.getElementById("expertEmail").value = result.email || '';
            document.getElementById("contactEmail").value = result.contactEmail || '';
            document.getElementById("expertPhone").value = result.phone || '';
            
            // Ẩn yêu cầu password khi sửa
            document.getElementById("passwordRequired").style.display = "none";
            document.getElementById("passwordHint").textContent = "Để trống nếu không muốn đổi mật khẩu";
            document.getElementById("expertPassword").required = false;

            if (quillExpertEditors.education) quillExpertEditors.education.root.innerHTML = result.education || '';
            if (quillExpertEditors.bio) quillExpertEditors.bio.root.innerHTML = result.bio || '';
            if (quillExpertEditors.achievements) quillExpertEditors.achievements.root.innerHTML = result.achievements || '';

            // --- Ảnh đại diện (Avatar) ---
            if (result.avatar) {
                const previewWrapper = document.getElementById("previewWrapper");
                previewWrapper.innerHTML = `<img src="${result.avatar}" class="img-fluid rounded" style="object-fit: cover; width: 100%; height: 100%;" />`;
                document.getElementById("removeImage").disabled = false;
            } else {
                document.getElementById("removeImage").disabled = true;
            }

            toastr.success("Tải dữ liệu chuyên gia thành công!");

        } catch (error) {
            console.error('Lỗi khi tải dữ liệu chuyên gia:', error);
            toastr.error("Không thể tải dữ liệu chuyên gia!");
        }
    } else {
        console.log("Không có ID chuyên gia trong URL, chuẩn bị cho việc thêm mới.");
    }
}
async function saveExpert() {
    initExpertEditors();
    // 1. Lấy dữ liệu từ các input cơ bản
    const name = document.getElementById('expertName').value.trim();
    const slug = document.getElementById('slug').value.trim();
    const title = document.getElementById('expertTitle').value.trim();
    const specialization = document.getElementById('specialization').value.trim();
    const institution = document.getElementById('institution').value.trim();
    const email = document.getElementById('expertEmail').value.trim();
    const contactEmail = document.getElementById('contactEmail').value.trim();
    const phone = document.getElementById('expertPhone').value.trim();
    const avatar = window.uploadedExpertAvatar || null; // Lấy URL ảnh đã upload
    const password = document.getElementById('expertPassword').value.trim();

    const education = quillExpertEditors.education ? quillExpertEditors.education.root.innerHTML : '';
    const bio = quillExpertEditors.bio ? quillExpertEditors.bio.root.innerHTML : '';
    const achievements = quillExpertEditors.achievements ? quillExpertEditors.achievements.root.innerHTML : '';

    // 3. Kiểm tra dữ liệu bắt buộc
    if (!name || !slug || !specialization) {
        toastr.error("Vui lòng nhập đầy đủ Tên, Slug và Chuyên môn.");
        return;
    }

    // Kiểm tra email
    const expertEmail = email || contactEmail;
    if (!expertEmail) {
        toastr.error("Vui lòng nhập Email hoặc Email liên hệ.");
        return;
    }

    // Kiểm tra password (bắt buộc khi tạo mới, không bắt buộc khi sửa)
    const expertId = window.currentExpertId;
    if (!expertId && (!password || password.length < 6)) {
        toastr.error("Vui lòng nhập mật khẩu (tối thiểu 6 ký tự) để tạo tài khoản chuyên gia.");
        return;
    }

    const expertData = {
        name: name,
        slug: slug,
        title: title,
        email: email,
        phone: phone,
        specialization: specialization,
        institution: institution,
        avatar: avatar,
        education: education,
        bio: bio,
        achievements: achievements,
        contactEmail: contactEmail,
        password: password || null
    };

    // Thêm ID nếu đang sửa
    if (expertId) {
        expertData.id = expertId;
    }

    // 4. Gửi dữ liệu lên API
    console.log("Dữ liệu chuyên gia chuẩn bị gửi:", expertData);

    const response = await fetch('/api/expert/admin/create', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + token
        },
        body: JSON.stringify(expertData)
    })

    // Xử lý 401/403 - redirect về login nếu token hết hạn
    if (checkResponseError(response)) {
        return; // Đã redirect, không cần xử lý tiếp
    }

    if (response.ok) {
        const result = await response.json();
        swal({
            title: "Thành công",
            text: expertId ? "Cập nhật thông tin chuyên gia thành công!" : "Lưu thông tin chuyên gia thành công!",
            type: "success"
        }, function () {
            window.location.href = '/admin/list-expert'
        });
    } else {
        const errorText = await response.text().catch(() => "Lỗi không xác định");
        if (response.status === exceptionCode) {
            toastr.error(errorText || "Lỗi dữ liệu!");
        } else {
            toastr.error(errorText || "Chuyên gia này đã có trong database");
        }
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

async function deleteExpert(id) {
    var con = confirm("Xác nhận xóa chuyên gia này?")
    if (con == false) {
        return;
    }
    var url = '/api/expert/admin/delete?id=' + id;
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
                text: "xóa chuyên gia thành công!",
                type: "success"
            },
            function() {
                loadAllExpert(0)
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

