// Sử dụng token và currentUser từ main.js (đã được load trước)
// exceptionCode cũng đã được khai báo trong main.js

// Load thông tin user
async function loadUserInfo() {
    try {
        // Kiểm tra token (sử dụng biến từ main.js)
        if (!token) {
            toastr.error("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
            setTimeout(() => {
                window.location.href = "/login";
            }, 2000);
            return;
        }
        
        const response = await fetch("/api/user/current", {
            method: "GET",
            headers: {
                "Authorization": "Bearer " + token,
                "Content-Type": "application/json"
            }
        });
        
        if (response.ok) {
            const user = await response.json();
            // Lưu lại user vào localStorage và cập nhật biến global
            localStorage.setItem("user", JSON.stringify(user));
            if (typeof currentUser !== 'undefined') {
                currentUser = user;
            }
            displayUserInfo(user);
        } else {
            const errorText = await response.text().catch(() => "Unknown error");
            
            if (response.status === 401 || response.status === 403) {
                toastr.error("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
                setTimeout(() => {
                    window.location.href = "/login";
                }, 2000);
            } else {
                toastr.error("Không thể tải thông tin người dùng. Mã lỗi: " + response.status);
            }
        }
    } catch (error) {
        console.error("Error loading user info:", error);
        toastr.error("Lỗi kết nối đến server: " + error.message);
    }
}

// Lưu user data để reset form
let savedUserData = null;

// Hiển thị thông tin user vào form
function displayUserInfo(user) {
    if (!user) {
        toastr.error("Không có dữ liệu người dùng");
        return;
    }
    
    // Lưu lại để reset form
    savedUserData = user;
    
    // Xác định vai trò
    const userRole = user.authorities?.name;
    let roleLabel = "Người dùng thường";
    if (userRole === "ROLE_ADMIN") {
        roleLabel = "Quản trị viên";
    } else if (userRole === "ROLE_EXPERT") {
        roleLabel = "Chuyên gia";
    }
    
    // Set giá trị cho các input
    const editUsername = document.getElementById("editUsername");
    const editEmail = document.getElementById("editEmail");
    const editFullname = document.getElementById("editFullname");
    const editPhone = document.getElementById("editPhone");
    const editAddress = document.getElementById("editAddress");
    const viewRole = document.getElementById("viewRole");
    const viewStatus = document.getElementById("viewStatus");
    
    if (editUsername) editUsername.value = user.username || "";
    if (editEmail) editEmail.value = user.email || "";
    if (editFullname) editFullname.value = user.fullname || "";
    if (editPhone) editPhone.value = user.phone || "";
    if (editAddress) editAddress.value = user.address || "";
    if (viewRole) viewRole.value = roleLabel;
    if (viewStatus) viewStatus.value = user.actived ? "Hoạt động" : "Đã khóa";
    
    // Clear password fields
    const editOldPassword = document.getElementById("editOldPassword");
    const editNewPassword = document.getElementById("editNewPassword");
    const editConfirmPassword = document.getElementById("editConfirmPassword");
    if (editOldPassword) editOldPassword.value = "";
    if (editNewPassword) editNewPassword.value = "";
    if (editConfirmPassword) editConfirmPassword.value = "";
}

// Reset form về giá trị ban đầu
function resetForm() {
    if (savedUserData) {
        displayUserInfo(savedUserData);
        toastr.info("Đã hủy thay đổi");
    }
}

// Update profile
async function updateProfile() {
    const fullname = document.getElementById("editFullname").value.trim();
    const phone = document.getElementById("editPhone").value.trim();
    const address = document.getElementById("editAddress").value.trim();
    const oldPassword = document.getElementById("editOldPassword").value;
    const newPassword = document.getElementById("editNewPassword").value.trim();
    const confirmPassword = document.getElementById("editConfirmPassword").value.trim();
    
    // Validate
    if (!fullname) {
        toastr.error("Vui lòng nhập họ và tên");
        document.getElementById("editFullname").focus();
        return;
    }
    
    if (!oldPassword) {
        toastr.error("Vui lòng nhập mật khẩu hiện tại để xác nhận");
        document.getElementById("editOldPassword").focus();
        return;
    }
    
    // Nếu có nhập mật khẩu mới thì phải xác nhận
    if (newPassword) {
        if (newPassword.length < 6) {
            toastr.error("Mật khẩu mới phải có ít nhất 6 ký tự");
            document.getElementById("editNewPassword").focus();
            return;
        }
        if (newPassword !== confirmPassword) {
            toastr.error("Mật khẩu mới và xác nhận không khớp");
            document.getElementById("editConfirmPassword").focus();
            return;
        }
    }
    
    // Disable button và hiển thị loading
    const submitButton = document.getElementById("submitBtn");
    const originalButtonText = submitButton.innerHTML;
    submitButton.disabled = true;
    submitButton.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Đang lưu...';
    
    try {
        const data = {
            fullname: fullname,
            phone: phone,
            address: address,
            oldPassword: oldPassword
        };
        
        // Chỉ gửi newPassword nếu có nhập
        if (newPassword) {
            data.newPassword = newPassword;
        }
        
        const response = await fetch("/api/user/update-profile", {
            method: "POST",
            headers: {
                "Authorization": "Bearer " + token,
                "Content-Type": "application/json"
            },
            body: JSON.stringify(data)
        });
        
        if (response.ok) {
            const updatedUser = await response.json();
            toastr.success("Cập nhật thông tin thành công");
            
            // Update localStorage
            localStorage.setItem("user", JSON.stringify(updatedUser));
            if (typeof currentUser !== 'undefined') {
                currentUser = updatedUser;
            }
            
            // Reload và hiển thị lại
            displayUserInfo(updatedUser);
        } else {
            const errorText = await response.text().catch(() => "Unknown error");
            toastr.error(errorText || "Cập nhật thất bại");
        }
    } catch (error) {
        console.error("Error updating profile:", error);
        toastr.error("Lỗi kết nối đến server: " + error.message);
    } finally {
        // Re-enable button
        submitButton.disabled = false;
        submitButton.innerHTML = originalButtonText;
    }
}


// Load khi trang được tải
$(document).ready(function() {
    // Kiểm tra token trước khi load
    if (!token) {
        window.location.href = "/login";
        return;
    }
    
    // Đợi DOM sẵn sàng rồi load thông tin
    if (document.getElementById("editUsername")) {
        loadUserInfo();
    } else {
        // Đợi một chút nếu element chưa render
        setTimeout(loadUserInfo, 100);
    }
});

