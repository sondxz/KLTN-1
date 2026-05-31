var token = localStorage.getItem("token");
var currentUser = null;
try {
    currentUser = JSON.parse(localStorage.getItem("user"));
} catch (e) {
    currentUser = null;
}

if (!token || !currentUser || !currentUser.authorities) {
    window.location.href = "/login";
} else {
    const role = currentUser.authorities.name;
    if (role !== "ROLE_ADMIN" && role !== "ROLE_EXPERT") {
        window.location.href = "/login";
    }
}

const exceptionCode = 417;
$( document ).ready(function() {
    const isAdmin = currentUser && currentUser.authorities && currentUser.authorities.name === "ROLE_ADMIN";
    const isExpert = currentUser && currentUser.authorities && currentUser.authorities.name === "ROLE_EXPERT";
    
    var adminMenuItems = `
        <a href="/index" class="nav-link"><i data-lucide="home" class="me-2"></i>Trang chủ</a>
        <a href="/admin/statistics" class="nav-link"><i data-lucide="bar-chart-2" class="me-2"></i>Thống kê</a>
        <a href="/admin/list-plant" class="nav-link"><i data-lucide="leaf" class="me-2"></i>Cây dược liệu</a>
        <a href="/admin/list-folk-remedies" class="nav-link"><i data-lucide="clipboard-list" class="me-2"></i>Bài thuốc dân gian</a>
        <a href="/admin/list-article" class="nav-link"><i data-lucide="file-text" class="me-2"></i>Bài viết</a>
        <a href="/admin/pending-approval" class="nav-link"><i data-lucide="check-circle" class="me-2"></i>Chờ duyệt</a>
        <a href="/admin/list-comment" class="nav-link"><i data-lucide="message-circle" class="me-2"></i>Bình luận</a>
        <a href="/admin/list-research" class="nav-link"><i data-lucide="book-open" class="me-2"></i>Nghiên cứu</a>
        <a href="/admin/list-expert" class="nav-link"><i data-lucide="user-circle" class="me-2"></i>Chuyên gia</a>
        <a href="/admin/list-user" class="nav-link"><i data-lucide="users" class="me-2"></i>Người dùng</a>
    `;
    
    var expertMenuItems = `
        <a href="/index" class="nav-link"><i data-lucide="home" class="me-2"></i>Trang chủ</a>
        <a href="/admin/list-plant" class="nav-link"><i data-lucide="leaf" class="me-2"></i>Cây dược liệu</a>
        <a href="/admin/list-folk-remedies" class="nav-link"><i data-lucide="clipboard-list" class="me-2"></i>Bài thuốc dân gian</a>
        <a href="/admin/list-article" class="nav-link"><i data-lucide="file-text" class="me-2"></i>Bài viết</a>
        <a href="/admin/pending-approval" class="nav-link"><i data-lucide="check-circle" class="me-2"></i>Chờ duyệt</a>
        <a href="/admin/list-research" class="nav-link"><i data-lucide="book-open" class="me-2"></i>Nghiên cứu</a>
        <a href="/admin/expert-messages" class="nav-link"><i data-lucide="message-square" class="me-2"></i>Tin nhắn <span class="badge bg-danger" id="unreadMessageBadge" style="display: none;">0</span></a>
    `;
    
    var menuItems = isAdmin ? adminMenuItems : expertMenuItems;
    
    var headerAdmin =
`<div class="sidebar d-none d-lg-flex flex-column">
      <div class="d-flex align-items-center p-3 border-bottom">
        <div class="brand-circle">DL</div>
        <span class="ms-2 brand">DuocLieuVN</span>
      </div>
      <nav class="flex-grow-1 p-2" id="navmain">
        ${menuItems}
        <div class="accordion" id="menuAccordion">
          <div class="accordion-item border-0">
            <h2 class="accordion-header">
              <button class="nav-link w-100 d-flex align-items-center collapsed" 
                      type="button" 
                      data-bs-toggle="collapse" 
                      data-bs-target="#submenu1">
                <i data-lucide="list" class="me-2"></i>Danh mục
              </button>
            </h2>
            <div id="submenu1" class="accordion-collapse collapse" data-bs-parent="#menuAccordion">
              <div class="accordion-body py-2 px-0 submenu">
                <a href="/admin/list-diseases" class="nav-link">Công dụng chữa bệnh</a>
                <a href="/admin/list-families" class="nav-link">Họ thực vật</a>
              </div>
            </div>
          </div>
        </div>

      </nav>
      </div>` 
document.getElementById("header-admin").innerHTML = headerAdmin

var navbarAdmin = 
`<header class="topbar py-2 px-3 d-flex justify-content-between align-items-center">
        <div class="d-flex align-items-center">
          <button class="btn btn-link d-lg-none p-0 me-2"><i data-lucide="menu"></i></button>
          <a href="/index" class="btn btn-outline-primary me-3">
            <i data-lucide="home" class="me-1"></i>Trang chủ
          </a>
          <div class="search-box">
            <i data-lucide="search" class="icon"></i>
            <input type="text" class="form-control" placeholder="Tìm kiếm...">
          </div>
        </div>
        <div class="d-flex align-items-center">
          <button class="btn btn-light rounded-circle me-3"><i data-lucide="bell"></i></button>
          <div class="dropdown">
            <button class="btn btn-light rounded-circle" data-bs-toggle="dropdown">
              <img src="https://via.placeholder.com/32" alt="Admin" class="rounded-circle">
            </button>
            <ul class="dropdown-menu dropdown-menu-end">
              <li><h6 class="dropdown-header">Tài khoản của tôi</h6></li>
              <li><a class="dropdown-item" href="/my-account"><i data-lucide="user-circle" class="me-2"></i>Hồ sơ</a></li>
              <li><a class="dropdown-item" href="#"><i data-lucide="settings" class="me-2"></i>Cài đặt</a></li>
              <li><hr class="dropdown-divider"></li>
              <li><a class="dropdown-item text-danger" href="#" onclick="logout()"><i data-lucide="log-out" class="me-2"></i>Đăng xuất</a></li>
            </ul>
          </div>
        </div>
      </header>`
document.getElementById("navbar-admin").innerHTML = navbarAdmin
lucide.createIcons();
    setActiveMenu();
    
    // ===== KẾT NỐI WebSocket TOÀN CỤC =====
    // Mục đích: ExpertStatusService ghi nhận online ngay khi expert/admin đăng nhập vào bất kỳ trang admin nào.
    if (token != null && currentUser && currentUser.id) {
        initGlobalAdminWebSocket();
    }
});

function setActiveMenu() {
    var currentPath = window.location.pathname;

    var currentBaseUrl = currentPath
    $('#navmain .nav-link').removeClass('active');
    $('#navmain .nav-link').each(function() {
        var linkHref = $(this).attr('href');
        var linkBaseUrl = linkHref
        if (linkBaseUrl === currentBaseUrl) {
            $(this).addClass('active');
            return false;
        }
    });
}

function logout(){
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    sessionStorage.removeItem("chat_history"); // Xóa lịch sử chat
    window.location.href = '/logout'
}

function handleAuthError(response) {
    if (response.status === 401 || response.status === 403) {
        toastr.error("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        localStorage.removeItem("token");
        localStorage.removeItem("user");
        setTimeout(() => {
            window.location.href = "/login";
        }, 1500);
        return true; // Đã xử lý
    }
    return false;
}

function checkResponseError(response) {
    if (handleAuthError(response)) {
        return true;
    }
    return false;
}

// ===== KẾT NỐI WebSocket TOÀN CỤC (Admin/Expert) =====
var globalAdminStompClient = null;
var globalAdminReconnectTimer = null;
var globalAdminReconnectAttempts = 0;
var globalAdminMaxReconnectAttempts = 10;

function initGlobalAdminWebSocket() {
    if (globalAdminReconnectTimer) {
        clearTimeout(globalAdminReconnectTimer);
        globalAdminReconnectTimer = null;
    }
    if (globalAdminStompClient && globalAdminStompClient.connected) return;
    
    // Dynamic load SockJS + Stomp nếu chưa có
    var loadScripts = [];
    if (typeof SockJS === 'undefined') {
        loadScripts.push($.getScript('https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js'));
    }
    if (typeof Stomp === 'undefined') {
        loadScripts.push($.getScript('https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js'));
    }
    
    var doConnect = function() {
        try {
            var socket = new SockJS('/ws');
            globalAdminStompClient = Stomp.over(socket);
            globalAdminStompClient.debug = null;
            
            globalAdminStompClient.connect(
                { 'Authorization': 'Bearer ' + token },
                function onConnect() {
                    globalAdminReconnectAttempts = 0;
                    console.log('✅ Global Admin WebSocket CONNECTED (userId=' + currentUser.id + ')');
                },
                function onError(err) {
                    console.error('Global Admin WebSocket error:', err);
                    if (globalAdminReconnectAttempts < globalAdminMaxReconnectAttempts) {
                        globalAdminReconnectAttempts++;
                        var delay = Math.min(globalAdminReconnectAttempts * 2000, 30000);
                        globalAdminReconnectTimer = setTimeout(initGlobalAdminWebSocket, delay);
                    }
                }
            );
        } catch(e) {
            console.error('Global Admin WebSocket init error:', e);
        }
    };
    
    if (loadScripts.length > 0) {
        $.when.apply($, loadScripts).done(doConnect).fail(function() {
            console.error('Không thể tải SockJS/Stomp cho WebSocket Admin');
        });
    } else {
        doConnect();
    }
}