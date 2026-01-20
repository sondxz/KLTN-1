// Helper function để hiển thị thông báo lỗi (dùng toastr nếu có, nếu không thì dùng error-section)
function showError(message, type) {
    if (typeof toastr !== 'undefined') {
        if (type === 'error') {
            toastr.error(message);
        } else if (type === 'warning') {
            toastr.warning(message);
        } else {
            toastr.info(message);
        }
    }
    // Luôn hiển thị trong error-section để đảm bảo user thấy được
    var errorSection = document.getElementById("error-section");
    if (errorSection) {
        errorSection.style.display = 'block';
        var errorMess = document.getElementById("errormess");
        if (errorMess) {
            errorMess.innerText = message;
        }
    }
}

function handleCredentialResponse(response) {
    sendLoginRequestToBackend(response.credential);
}

async function sendLoginRequestToBackend(accessToken) {
    var response = await fetch('/api/login/google', {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain'
        },
        body: accessToken
    })
    var result;
    try {
        result = await response.json();
    } catch (e) {
        showError("Lỗi phản hồi từ server", "error");
        return;
    }

    if (response.status < 300) {
        localStorage.setItem("user", JSON.stringify(result.user));
        localStorage.setItem("token", result.token);
        if (result.user.authorities && result.user.authorities.name === "ROLE_ADMIN") {
            window.location.href = '/admin/list-plant';
        } else if (result.user.authorities && result.user.authorities.name === "ROLE_EXPERT") {
            window.location.href = '/index';
        } else if (result.user.authorities && result.user.authorities.name === "ROLE_USER") {
            window.location.href = '/index';
        } else {
            window.location.href = '/index';
        }
    }
    if (response.status == exceptionCode) {
        if (result && result.errorCode == 300) {
            swal({
                title: "Thông báo",
                text: "Tài khoản chưa được kích hoạt, đi tới kích hoạt tài khoản!",
                type: "warning"
            }, function() {
                var email = result.user && result.user.email ? result.user.email : '';
                window.location.href = 'confirm?email=' + email
            });
        } else {
            showError(result.defaultMessage || "Đăng nhập thất bại", "warning");
        }
    }
}


async function login() {
    var url = '/api/login'
    var username = document.getElementById("username").value
    var password = document.getElementById("password").value
    var user = {
        "username": username,
        "password": password,
    }
    const response = await fetch(url, {
        method: 'POST',
        headers: new Headers({
            'Content-Type': 'application/json'
        }),
        body: JSON.stringify(user)
    });
    var result;
    try {
        result = await response.json();
    } catch (e) {
        showError("Lỗi kết nối đến server", "error");
        return;
    }
    
    if (response.status < 300) {
        localStorage.setItem("user", JSON.stringify(result.user));
        localStorage.setItem("token", result.token);
        if (result.user.authorities && result.user.authorities.name === "ROLE_ADMIN") {
            window.location.href = '/admin/list-plant';
        } else {
            window.location.href = '/index';
        }
    } else if (response.status == exceptionCode) {
        // Xử lý exception code (417)
        if (result && result.errorCode == 300) {
            swal({
                title: "Thông báo",
                text: "Tài khoản chưa được kích hoạt, đi tới kích hoạt tài khoản!",
                type: "warning"
            }, function() {
                window.location.href = 'confirm?email=' + username
            });
        } else {
            const errorMsg = result?.defaultMessage || "Đăng nhập thất bại";
            showError(errorMsg, "warning");
        }
    } else if (response.status >= 400) {
        console.error("Login failed, status:", response.status);
        const errorMsg = result?.defaultMessage || result?.message || "Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin.";
        showError(errorMsg, "error");
    }
}

async function regis() {
    var url = '/api/regis'
    if(document.getElementById("password").value != document.getElementById("passwordConfirm").value){
        toastr.error("Mật khẩu không trùng khớp"); return;
    }
    var user = {
        "fullname": document.getElementById("fullname").value,
        "email": document.getElementById("email").value,
        "phone": document.getElementById("phone").value,
        "password": document.getElementById("password").value
    }
    const response = await fetch(url, {
        method: 'POST',
        headers: new Headers({
            'Content-Type': 'application/json'
        }),
        body: JSON.stringify(user)
    });
    var result = await response.json();
    if (response.status < 300) {
        swal({
                title: "Thông báo",
                text: "đăng ký thành công! hãy check email của bạn!",
                type: "success"
            },
            function() {
                window.location.href = 'confirm?email=' + result.email
            });
    }
    if (response.status == exceptionCode) {
        toastr.warning(result.defaultMessage);
        document.getElementById("error-section").style.display = 'block';
        document.getElementById("errormess").innerText = result.defaultMessage;
    }
}


async function confirmAccount() {
    var uls = new URL(document.URL)
    var email = uls.searchParams.get("email");
    var key = document.getElementById("maxacthuc").value;
    var url = '/api/active-account?email=' + email + '&key=' + key
    const res = await fetch(url, {
        method: 'POST'
    });
    if (res.status < 300) {
        swal({
                title: "Thông báo",
                text: "Xác nhận tài khoản thành công!",
                type: "success"
            },
            function() {
                window.location.href = 'login'
            });
    }
    if (res.status == exceptionCode) {
        var result = await res.json()
        toastr.warning(result.defaultMessage);
        document.getElementById("error-section").style.display = 'block';
        document.getElementById("errormess").innerText = result.defaultMessage;
    }
}

async function sendNewOtp() {
    var uls = new URL(document.URL)
    var email = uls.searchParams.get("email");
    var url = '/api/send-new-otp?email=' + email
    const res = await fetch(url, {
        method: 'POST'
    });
    if (res.status < 300) {
        swal({
                title: "Thông báo",
                text: "Gửi mã xác thực mới thành công!",
                type: "success"
            },
            function() {
            });
    }
    if (res.status == exceptionCode) {
        var result = await res.json()
        toastr.warning(result.defaultMessage);
        document.getElementById("error-section").style.display = 'block';
        document.getElementById("errormess").innerText = result.defaultMessage;
    }
}


async function forgorPassword() {
    var email = document.getElementById("email").value
    var url = '/api/forgot-password?email=' + email
    const res = await fetch(url, {
        method: 'POST'
    });
    if (res.status < 300) {
        swal({
                title: "",
                text: "Link đặt lại mật khẩu mới đã được gửi về email của bạn",
                type: "success"
            },
            function() {
                window.location.replace("login")
            });
    }
    if (res.status == exceptionCode) {
        var result = await res.json()
        toastr.warning(result.defaultMessage);
    }
}

async function changePassword() {
    var token = localStorage.getItem("token");
    var oldpass = document.getElementById("oldpass").value
    var newpass = document.getElementById("newpass").value
    var renewpass = document.getElementById("renewpass").value
    var url = '/api/user/change-password';
    if (newpass != renewpass) {
        alert("mật khẩu mới không trùng khớp");
        return;
    }
    var passw = {
        "oldPass": oldpass,
        "newPass": newpass
    }
    const response = await fetch(url, {
        method: 'POST',
        headers: new Headers({
            'Authorization': 'Bearer ' + token,
            'Content-Type': 'application/json'
        }),
        body: JSON.stringify(passw)
    });
    if (response.status < 300) {
        swal({
                title: "Thông báo",
                text: "cập nhật mật khẩu thành công, hãy đăng nhập lại",
                type: "success"
            },
            function() {
                window.location.reload();
            });
    }
    if (response.status == exceptionCode) {
        var result = await response.json()
        toastr.warning(result.defaultMessage);
    }
}

async function datLaiMatKhau() {
    var password = document.getElementById("newPassword").value
    var repassword = document.getElementById("confirmPassword").value
    if(password != repassword){
        toastr.warning("Mật khẩu không trùng khớp");
        return;
    }
    if(!password || password.trim() === ""){
        toastr.warning("Vui lòng nhập mật khẩu mới");
        return;
    }
    var uls = new URL(document.URL)
    var email = uls.searchParams.get("email");
    var key = uls.searchParams.get("key");
    
    // Debug logging
    console.log("=== DEBUG RESET PASSWORD (Frontend) ===");
    console.log("Full URL:", document.URL);
    console.log("Email from URL:", email);
    console.log("Key from URL:", key);
    console.log("Email type:", typeof email);
    console.log("Key type:", typeof key);
    console.log("Email length:", email ? email.length : "null");
    console.log("Key length:", key ? key.length : "null");
    
    if(!email || !key){
        console.error("ERROR: Thiếu email hoặc key!");
        toastr.error("Thiếu thông tin email hoặc key. Vui lòng sử dụng link từ email.");
        return;
    }
    
    console.log("=== END DEBUG ===");
    
    var url = '/api/public/dat-lai-mat-khau'
    try {
        const res = await fetch(url, {
            method: 'POST',
            headers: new Headers({
                'Content-Type': 'application/json'
            }),
            body: JSON.stringify({email: email, key: key, password: password})
        });
        
        if (res.status < 300) {
            swal({
                    title: "",
                    text: "Đặt lại mật khẩu thành công",
                    type: "success"
                },
                function() {
                    window.location.replace("login")
                });
        } else {
            // Xử lý lỗi - bao gồm cả 417 (EXPECTATION_FAILED) từ RestErrorHandler
            var errorMessage = "Đặt lại mật khẩu thất bại";
            try {
                var result = await res.json();
                // RestErrorHandler trả về MessageException object với defaultMessage
                if (result.defaultMessage) {
                    errorMessage = result.defaultMessage;
                } else if (result.message) {
                    errorMessage = result.message;
                } else if (typeof result === 'string') {
                    errorMessage = result;
                }
            } catch (e) {
                // Nếu không parse được JSON, dùng message mặc định
                if (res.status === 400) {
                    errorMessage = "Thông tin không hợp lệ. Vui lòng kiểm tra lại.";
                } else if (res.status === 404) {
                    errorMessage = "Không tìm thấy tài khoản hoặc key không hợp lệ.";
                } else if (res.status === 417) {
                    errorMessage = "Mã xác thực không chính xác hoặc đã hết hạn. Vui lòng yêu cầu đặt lại mật khẩu mới.";
                } else if (res.status === 500) {
                    errorMessage = "Lỗi server. Vui lòng thử lại sau.";
                }
            }
            toastr.error(errorMessage);
        }
    } catch (error) {
        console.error("Error:", error);
        toastr.error("Có lỗi xảy ra. Vui lòng thử lại sau.");
    }
}
