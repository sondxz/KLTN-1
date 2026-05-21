var token = localStorage.getItem("token");
var currentUser = null;
try {
    currentUser = JSON.parse(localStorage.getItem("user"));
} catch (e) {
    currentUser = null;
}
const exceptionCode = 417;
$( document ).ready(function() {
    var auth = `<a href="/login" class="btn btn-light btn-custom-login">
            <i class="bi bi-box-arrow-in-right me-1"></i>Đăng nhập
          </a>
          <a href="/regis" class="btn btn-custom-register">
            <i class="bi bi-person-plus me-1"></i>Đăng ký
          </a>`
    if(token != null){
        var userRole = currentUser && currentUser.authorities ? currentUser.authorities.name : null;
        var isExpert = userRole === "ROLE_EXPERT";
        var isAdmin = userRole === "ROLE_ADMIN";
        
        var currentPath = window.location.pathname;
        var showUploadButton =
          !isExpert &&
          !isAdmin &&
          (currentPath === "/plant" ||
            currentPath.startsWith("/plant") ||
            currentPath === "/articles" ||
            currentPath.startsWith("/articles") ||
            currentPath === "/plant-detail" ||
            currentPath.startsWith("/plant-detail") ||
            currentPath === "/article-detail" ||
            currentPath.startsWith("/article-detail") ||
            currentPath === "/folk-remedies" ||
            currentPath.startsWith("/folk-remedies"));
        
        var uploadButton = '';
        if(showUploadButton) {
            uploadButton = `
            <div class="dropdown">
              <button class="btn btn-outline-success dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false">
                <i class="bi bi-plus-lg me-1"></i>Đăng bài
              </button>
              <ul class="dropdown-menu dropdown-menu-end">
                <li><a class="dropdown-item" href="/create-plant"><i class="bi bi-flower1 me-2 text-success"></i>Đăng cây dược liệu</a></li>
                <li><a class="dropdown-item" href="/create-article"><i class="bi bi-file-text me-2 text-success"></i>Đăng bài viết</a></li>
                <li><a class="dropdown-item" href="/create-folk-remedy"><i class="bi bi-journal-medical me-2 text-success"></i>Đăng bài thuốc</a></li>
              </ul>
            </div>`;
        }
        
        var managementButton = '';
        if(isExpert || isAdmin) {
            var managementUrl = isAdmin ? '/admin/list-plant' : '/admin/pending-approval';
            var managementLabel = isAdmin ? 'Quản trị' : 'Duyệt bài';
            managementButton = `
            <div class="dropdown">
                <a href="${managementUrl}" class="btn btn-outline-secondary dropdown-toggle" role="button" data-bs-toggle="dropdown" aria-expanded="false">
                    ${managementLabel}
                </a>
                <ul class="dropdown-menu dropdown-menu-end">
                    ${isAdmin ? `
                    <li><a class="dropdown-item" href="/admin/list-plant"><i class="bi bi-flower1 me-2"></i>Cây dược liệu</a></li>
                    <li><a class="dropdown-item" href="/admin/list-folk-remedies"><i class="bi bi-journal-medical me-2"></i>Bài thuốc</a></li>
                    <li><a class="dropdown-item" href="/admin/list-article"><i class="bi bi-file-text me-2"></i>Bài viết</a></li>
                    <li><a class="dropdown-item" href="/admin/list-user"><i class="bi bi-people me-2"></i>Người dùng</a></li>
                    <li><hr class="dropdown-divider"></li>
                    ` : ''}
                    <li><a class="dropdown-item" href="/admin/pending-approval"><i class="bi bi-clock-history me-2"></i>Chờ duyệt</a></li>
                    ${isAdmin ? `
                    <li><a class="dropdown-item" href="/admin/list-expert"><i class="bi bi-person-badge me-2"></i>Chuyên gia</a></li>
                    ` : ''}
                </ul>
            </div>`;
        }
        
        var messageButton = '';
        if(userRole === "ROLE_USER" || userRole === "ROLE_EXPERT") {
            var messageUrl = userRole === "ROLE_USER" ? '/user/messages' : '/admin/expert-messages';
            var unreadCount = parseInt(localStorage.getItem('unreadMessageCount') || '0');
            var badgeHtml = unreadCount > 0 ? `<span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger" id="headerUnreadBadge" style="font-size: 0.7rem;">${unreadCount > 99 ? '99+' : unreadCount}</span>` : '<span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger" id="headerUnreadBadge" style="display: none;">0</span>';
            messageButton = `
            <a href="${messageUrl}" class="btn btn-outline-primary position-relative" title="Tin nhắn">
                <i class="bi bi-envelope fs-5"></i>
                ${badgeHtml}
            </a>`;
        }
        
        auth = uploadButton + managementButton + messageButton + `
        <a href="#" onclick="logout()" class="btn btn-light btn-custom-login">
            <i class="bi bi-box-arrow-right me-1"></i>Đăng xuất
          </a>
          <a href="/my-account" class="btn btn-custom-register">
            <i class="bi bi-person-circle me-1"></i>Tài khoản
          </a>
        `
    }
    var headerUser = 
    `<nav class="navbar navbar-expand-lg bg-white py-3">
    <div class="container">
      <a class="navbar-brand d-flex align-items-center" href="/">
        <div class="logo-circle">DL</div>
        <span>DuocLieuVN</span>
      </a>

      <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#mainMenu">
        <span class="navbar-toggler-icon"></span>
      </button>

      <div class="collapse navbar-collapse" id="mainMenu">
        <ul class="navbar-nav mx-auto mb-2 mb-lg-0 gap-lg-4">
          <li class="nav-item"><a class="nav-link active text-success fw-semibold border-bottom border-2 border-success" href="/index">Trang chủ</a></li>
          <li class="nav-item"><a class="nav-link" href="/plant">Cây dược liệu</a></li>
          <li class="nav-item"><a class="nav-link" href="/folk-remedies">Bài thuốc</a></li>
          <li class="nav-item"><a class="nav-link" href="/articles">Bài viết</a></li>
          <li class="nav-item"><a class="nav-link" href="/research">Nghiên cứu</a></li>
          <li class="nav-item"><a class="nav-link" href="/experts">Chuyên gia</a></li>
          <li class="nav-item"><a class="nav-link" href="/about">Giới thiệu</a></li>
        </ul>
        <div class="d-flex gap-2">
        ${auth}
        </div>
      </div>
    </div>
  </nav>`
  document.getElementById("header-main").innerHTML = headerUser

  var footerUser = 
  `<div class="container">
    <div class="row g-4">
      <div class="col-md-4">
        <div class="d-flex align-items-center mb-3">
          <div class="footer-logo">DL</div>
          <span class="fs-5 fw-bold">DuocLieuVN</span>
        </div>
        <p>Hệ thống quản lý thông tin cây dược liệu Việt Nam, cung cấp thông tin đầy đủ và chính xác.</p>
        <div class="d-flex gap-3">
          <a href="#"><i class="bi bi-facebook"></i></a>
          <a href="#"><i class="bi bi-twitter"></i></a>
          <a href="#"><i class="bi bi-instagram"></i></a>
        </div>
      </div>
      <div class="col-md-2">
        <h3 class="mb-3">Liên kết nhanh</h3>
        <ul class="list-unstyled">
          <li><a href="/">Trang chủ</a></li>
          <li><a href="/plants">Cây dược liệu</a></li>
          <li><a href="/folk-remedies">Bài thuốc</a></li>
          <li><a href="/articles">Bài viết</a></li>
          <li><a href="/research">Nghiên cứu</a></li>
          <li><a href="/experts">Chuyên gia</a></li>
          <li><a href="/about">Giới thiệu</a></li>
        </ul>
      </div>
      <div class="col-md-3">
        <h3 class="mb-3">Danh mục</h3>
        <ul class="list-unstyled">
          <li><a href="#">Thảo dược</a></li>
          <li><a href="#">Cây thuốc</a></li>
          <li><a href="#">Nấm dược liệu</a></li>
          <li><a href="#">Hoa dược liệu</a></li>
          <li><a href="#">Rễ dược liệu</a></li>
          <li><a href="#">Quả dược liệu</a></li>
        </ul>
      </div>
      <div class="col-md-3">
        <h3 class="mb-3">Liên hệ</h3>
        <ul class="list-unstyled">
          <li><i class="bi bi-telephone-fill me-2"></i> 0916893835</li>
          <li><i class="bi bi-envelope-fill me-2"></i> htha@vnua.edu.vn</li>
        </ul>
      </div>
    </div>
    <div class="text-center border-top mt-4 pt-3">
      © 2025 DuocLieuVN. Tất cả các quyền được bảo lưu.
    </div>
  </div>
</div>
`

  document.getElementById("footer-main").innerHTML = footerUser

    // Load unread message count
    function loadUnreadMessageCount() {
        var apiUrl = userRole === "ROLE_USER" ? '/api/message/user/unread-count' : '/api/message/expert/unread-count';
        fetch(apiUrl, {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        })
        .then(response => {
            if (response.ok) {
                return response.json();
            }
            return 0;
        })
        .then(count => {
            localStorage.setItem('unreadMessageCount', count);
            updateUnreadBadge(count);
        })
        .catch(error => {
            console.error("Error loading unread count:", error);
        });
    }
    
    // Update unread badge
    function updateUnreadBadge(count) {
        var badge = document.getElementById('headerUnreadBadge');
        if (badge) {
            if (count > 0) {
                badge.textContent = count > 99 ? '99+' : count;
                badge.style.display = 'inline';
            } else {
                badge.style.display = 'none';
            }
        }
    }
    
    // Listen for unread count updates from other pages
    window.addEventListener('unreadCountUpdated', function(event) {
        updateUnreadBadge(event.detail);
    });
    
    function setActiveMenu() {
        // 1. Lấy đường dẫn URL hiện tại
        // Ví dụ: /plant-detail/xa-den -> sẽ lấy /plant-detail/xa-den
        // Ví dụ: /plant -> sẽ lấy /plant
        var currentPath = window.location.pathname;

        // 2. Tùy chỉnh: Nếu URL là chi tiết (vd: /plant-detail/...) thì ta muốn đánh dấu trang chủ đề (/plant) là active.
        // Lấy phần đầu tiên của URL (ví dụ: /plant-detail/xa-den -> /plant)
        var currentBaseUrl = currentPath.split('/')[1];

        // Nếu đường dẫn trống (trang chủ) thì dùng 'index'
        if (currentBaseUrl === '') {
            currentBaseUrl = 'index';
        }

        // 3. Xóa tất cả các class active hiện có
        $('#mainMenu .nav-link').removeClass('active text-success fw-semibold border-bottom border-2 border-success');

        // 4. Lặp qua tất cả các liên kết và kiểm tra
        $('#mainMenu .nav-link').each(function() {
            var linkHref = $(this).attr('href'); // Lấy href, ví dụ: /plant

            // Lấy phần href cơ bản (ví dụ: /plant -> plant, /index -> index)
            var linkBaseUrl = linkHref.split('/')[1] || 'index';

            // So sánh URL cơ bản của link với URL cơ bản hiện tại
            if (linkBaseUrl === currentBaseUrl) {
                $(this).addClass('active text-success fw-semibold border-bottom border-2 border-success');

                // Thoát khỏi vòng lặp sau khi đã tìm thấy liên kết phù hợp
                return false;
            }
        });
    }

    // Gọi hàm sau khi header đã được chèn vào DOM
    setActiveMenu();
    
    // Load unread message count nếu là user hoặc expert
    if(token != null && (userRole === "ROLE_USER" || userRole === "ROLE_EXPERT")) {
        loadUnreadMessageCount();
    }
    
    // ===== KẾT NỐI WebSocket TOÀN CỤC =====
    // Mục đích: ExpertStatusService ghi nhận online ngay khi user/expert đăng nhập vào bất kỳ trang nào.
    // Chỉ cần connect, không cần subscribe — SessionConnectedEvent tự động kích hoạt tracking.
    if (token != null && currentUser && currentUser.id) {
        initGlobalWebSocket();
    }
});

/** Kết nối WebSocket toàn cục (dùng cho mọi trang) */
var globalStompClient = null;
var globalReconnectTimer = null;
var globalReconnectAttempts = 0;
var globalMaxReconnectAttempts = 10;

function initGlobalWebSocket() {
    if (globalReconnectTimer) {
        clearTimeout(globalReconnectTimer);
        globalReconnectTimer = null;
    }
    if (globalStompClient && globalStompClient.connected) return;
    
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
            globalStompClient = Stomp.over(socket);
            globalStompClient.debug = null;
            
            globalStompClient.connect(
                { 'Authorization': 'Bearer ' + token },
                function onConnect() {
                    globalReconnectAttempts = 0;
                    console.log('✅ Global WebSocket CONNECTED (userId=' + currentUser.id + ')');
                },
                function onError(err) {
                    console.error('Global WebSocket error:', err);
                    if (globalReconnectAttempts < globalMaxReconnectAttempts) {
                        globalReconnectAttempts++;
                        var delay = Math.min(globalReconnectAttempts * 2000, 30000);
                        globalReconnectTimer = setTimeout(initGlobalWebSocket, delay);
                    }
                }
            );
        } catch(e) {
            console.error('Global WebSocket init error:', e);
        }
    };
    
    if (loadScripts.length > 0) {
        $.when.apply($, loadScripts).done(doConnect).fail(function() {
            console.error('Không thể tải SockJS/Stomp cho WebSocket toàn cục');
        });
    } else {
        doConnect();
    }
}


function logout(){
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    window.location.href = '/logout'
}