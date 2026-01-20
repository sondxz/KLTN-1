var size = 10;
async function loadAllUser(page) {
    var param = document.getElementById("param").value
    var url = '/api/admin/get-user-by-role?page=' + page + '&size=' + size + '&q=' + param;
    var role = document.getElementById("role").value
    if (role != "") {
        url += '&role=' + role
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
    
    var result = await response.json();
    // Không log toàn bộ result vì có thể chứa thông tin nhạy cảm
    var listUser = result.content;
    var totalPage = result.totalPages;
    const totalElements = result.totalElements || 0;
    const numberOfElements = result.numberOfElements || 0;
    const start = totalElements === 0 ? 0 : page * size + 1;
    const end = totalElements === 0 ? 0 : page * size + numberOfElements;   

    var main = '';
    for (i = 0; i < listUser.length; i++) {
        main += `<tr>
                    <td>${listUser[i].id}</td>
                    <td>${listUser[i].fullname}</td>
                    <td>${listUser[i].username} ${listUser[i].userType == 'EMAIL'?'':'<img src="/image/google.png" class="img-icon">'}</td>
                    <td>${listUser[i].email}</td>
                    <td>${listUser[i].authorities.name}</td>
                    <td><span class="badge p-2 ${listUser[i].actived == true?'badge-active':'badge-inactive'}">
                    ${listUser[i].actived == true ?'Hoạt động':'Đang khóa'}
                    </span></td>
                    <td>${listUser[i].createdDate}</td>
                    <td class="text-center">
                        <a href="/admin/create-user?id=${listUser[i].id}" class="btn btn-primary btn-sm" title="Sửa"><i class="fa-solid fa-pencil"></i></a>
                        <button onclick="deleteAccount(${listUser[i].id})" class="btn btn-danger btn-sm " title="Xóa"><i class="fa-solid fa-xmark"></i></button>
                    </td>
                </tr>`
    }
    document.getElementById("listuser").innerHTML = main
    var mainpage = ''
    for (i = 1; i <= totalPage; i++) {
        mainpage += `<li onclick="loadAllUser(${(Number(i) - 1)})" class="page-item"><a class="page-link" href="#listsp">${i}</a></li>`
    }
    document.getElementById("pageable").innerHTML = mainpage
    document.getElementById("numElm").innerText = `Đang hiển thị ${start} - ${end} trong ${result.totalElements} kết quả`
}


async function loadAuthority() {
    var url = '/api/admin/authority';
    const response = await fetch(url, {
        headers: new Headers({
            'Authorization': 'Bearer ' + token
        })
    });
    
    // Xử lý 401/403 - redirect về login nếu token hết hạn
    if (checkResponseError(response)) {
        return; // Đã redirect, không cần xử lý tiếp
    }
    
    var list = await response.json();

    var main = '';
    for (i = 0; i < list.length; i++) {
        main += `<option value="${list[i].name}">${list[i].name}</option>`
    }
    document.getElementById("role").innerHTML = main
}


async function addAccount(event) {
    // Ngăn form submit mặc định
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }
    
    var uls = new URL(document.URL)
    var id = uls.searchParams.get("id");

    const passwordElement = document.getElementById("password");
    const confirmPasswordElement = document.getElementById("confirm-password");
    
    if (!passwordElement) {
        toastr.error("Không tìm thấy trường mật khẩu");
        return false;
    }
    
    // Lấy giá trị password
    const passwordRaw = passwordElement.value || "";
    const password = passwordRaw.trim();
    const confirmPasswordRaw = confirmPasswordElement ? (confirmPasswordElement.value || "") : "";
    const confirmPassword = confirmPasswordRaw.trim();

    // tạo mới: bắt buộc có mật khẩu + xác nhận đúng
    if (!id) {
        // Kiểm tra password không rỗng (sau khi trim)
        if (!password || password.length === 0) {
            // Focus vào password field để user biết
            passwordElement.focus();
            swal({
                title: "Thông báo",
                text: "Mật khẩu không được để trống khi tạo tài khoản mới",
                type: "error"
            });
            return false;
        }
        // Kiểm tra password có ít nhất 6 ký tự
        if (password.length < 6) {
            passwordElement.focus();
            swal({
                title: "Thông báo",
                text: "Mật khẩu phải có ít nhất 6 ký tự",
                type: "error"
            });
            return false;
        }
        if (password !== confirmPassword) {
            confirmPasswordElement.focus();
            toastr.error("Mật khẩu không trùng khớp"); 
            return false;
        }
    } else {
        // sửa: không cần xác nhận mật khẩu, chỉ cần nhập mật khẩu mới nếu muốn đổi
        // Nếu có nhập mật khẩu mới thì dùng, không có thì giữ nguyên mật khẩu cũ
        // Nếu có nhập password mới thì phải có ít nhất 6 ký tự
        if (password && password.length > 0 && password.length < 6) {
            passwordElement.focus();
            swal({
                title: "Thông báo",
                text: "Mật khẩu mới phải có ít nhất 6 ký tự",
                type: "error"
            });
            return false;
        }
    }

    var user = {
        "id":id,
        "fullname": document.getElementById("fullname").value,
        "phone": document.getElementById("phone").value,
        "email": document.getElementById("email").value,
        "username": document.getElementById("username").value,
        "address": document.getElementById("address").value,
        "actived": document.getElementById("actived").checked,
        "authorities": {
            "name":document.getElementById("role").value
        },
    }
    // chỉ gửi password khi tạo mới hoặc khi admin nhập mật khẩu mới
    if (!id) {
        // Tạo mới: bắt buộc có password
        user.password = password;
    } else if (password && password.length > 0) {
        // Sửa: chỉ gửi password nếu có nhập
        user.password = password;
    }
    
    // Tạo user object an toàn để log (không có password)
    const safeUserForLog = { ...user };
    delete safeUserForLog.password;
    // Chỉ log trong development mode (có thể bật/tắt)
    if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
        console.log("DEBUG - User object (password hidden):", JSON.stringify(safeUserForLog, null, 2));
    }
    
    try {
        const res = await fetch('/api/admin/addaccount', {
            method: 'POST',
            headers: new Headers({
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            }),
            body: JSON.stringify(user)
        });
        
        // Xử lý 401/403 - redirect về login nếu token hết hạn
        if (checkResponseError(res)) {
            return; // Đã redirect, không cần xử lý tiếp
        }
        
        if (res.status < 300) {
            var message = id ? "Cập nhật tài khoản thành công!" : "Tạo tài khoản thành công!";
            swal({
                    title: "Thông báo",
                    text: message,
                    type: "success"
                },
                function() {
                    window.location.href = '/admin/list-user';
                });
        } else {
            // Đọc error message từ response
            let errorMessage = "Có lỗi xảy ra";
            try {
                const result = await res.json();
                errorMessage = result.defaultMessage || result.message || result.error || "Có lỗi xảy ra";
            } catch (e) {
                const text = await res.text();
                errorMessage = text || "Có lỗi xảy ra";
            }
            
            swal({
                title: "Thông báo",
                text: errorMessage,
                type: "error"
            });
        }
    } catch (error) {
        console.error("Error adding account:", error);
        swal({
            title: "Thông báo",
            text: "Không thể kết nối đến server. Vui lòng thử lại.",
            type: "error"
        });
    }
    
    return false; // Ngăn form submit
}

async function loadAUser() {
    var id = window.location.search.split('=')[1];
    if (id != null) {
        document.getElementById("note-pass").style.display = 'block';
        const response = await fetch('/api/admin/find-user-by-id?id=' + id, {
            headers: new Headers({
                'Authorization': 'Bearer ' + token
            })
        });
        
        // Xử lý 401/403 - redirect về login nếu token hết hạn
        if (checkResponseError(response)) {
            return; // Đã redirect, không cần xử lý tiếp
        }
        
        var user = await response.json();
        document.getElementById("username").value = user.username
        document.getElementById("email").value = user.email
        document.getElementById("address").value = user.address
        document.getElementById("phone").value = user.phone
        document.getElementById("fullname").value = user.fullname
        document.getElementById("role").value = user.authorities.name
        document.getElementById("actived").checked = user.actived

        // Ẩn trường xác nhận mật khẩu khi sửa
        const confirmPasswordRow = document.getElementById("confirm-password").closest('.col-md-6');
        if (confirmPasswordRow) {
            confirmPasswordRow.style.display = 'none';
        }
        
        // Cập nhật label mật khẩu
        const passwordLabel = document.querySelector('label[for="create-user-password"]');
        if (passwordLabel) {
            passwordLabel.innerText = "Mật khẩu mới (để trống nếu không đổi)";
        }
        
        // Cập nhật note
        const notePass = document.getElementById("note-pass");
        if (notePass) {
            notePass.innerText = "Bỏ trống mật khẩu để giữ nguyên mật khẩu cũ";
            notePass.style.display = 'block';
        }

        // cập nhật text giao diện cho trường hợp sửa người dùng
        const title = document.querySelector("h1.h4");
        if (title) {
            title.innerText = "Cập nhật người dùng";
        }
        const subtitle = document.querySelector("p.text-muted.small");
        if (subtitle) {
            subtitle.innerText = "Chỉnh sửa thông tin tài khoản người dùng";
        }
        const primaryBtn = document.querySelector(".create-user-btn-primary");
        if (primaryBtn) {
            primaryBtn.innerText = "Lưu";
        }
    }
}



async function deleteAccount(id) {
    var con = confirm("Xác nhận xóa tài khoản này?")
    if (con == false) {
        return;
    }
    var url = '/api/admin/delete-user-by-id?id=' + id;
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
            text: "xóa tài khoản thành công!",
            type: "success"
        },
        function() {
            window.location.reload();
        });
    }
    else {
        // Đọc thông báo lỗi từ response body
        let errorMessage = "Có lỗi xảy ra khi xóa tài khoản";
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
        if (response.status == 417 || response.status == 400) {
            toastr.warning(errorMessage);
            swal({
                title: "Thông báo",
                text: errorMessage,
                type: "error"
            },
            function() {
            });
        } else {
            toastr.error(errorMessage);
        }
    }
}

/**
 * Xuất danh sách người dùng ra CSV, áp dụng bộ lọc hiện tại.
 */
async function exportUsers() {
    const param = document.getElementById("param").value || "";
    const role = document.getElementById("role").value || "";

    let url = `/api/admin/export-users?q=${encodeURIComponent(param)}`;
    if (role !== "") {
        url += `&role=${encodeURIComponent(role)}`;
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
        a.download = 'users_export.csv';
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(downloadUrl);
    } catch (e) {
        console.error(e);
        toastr.error("Có lỗi xảy ra khi xuất Excel");
    }
}