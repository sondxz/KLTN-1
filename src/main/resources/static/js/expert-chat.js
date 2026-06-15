// Helper function để hiển thị thông báo lỗi (dùng toastr nếu có, nếu không thì dùng console và alert)
function showExpertChatError(message, type) {
    if (typeof toastr !== 'undefined') {
        if (type === 'error') {
            toastr.error(message);
        } else if (type === 'warning') {
            toastr.warning(message);
        } else if (type === 'success') {
            toastr.success(message);
        } else {
            toastr.info(message);
        }
    } else {
        // Fallback nếu toastr chưa load
        console.error('Expert Chat Error:', message);
        if (type === 'error') {
            alert('Lỗi: ' + message);
        } else if (type === 'warning') {
            alert('Cảnh báo: ' + message);
        }
    }
}

// Chat với Expert - User gửi tin nhắn cho Expert
var currentExpertUserId = null;
var currentExpertName = null;
var expertChatStompClient = null;
var currentUser = null;
var expertChatReconnectAttempts = 0;
var expertChatMaxReconnectAttempts = 5;
var expertChatReconnectDelay = 1000;
var expertChatReconnectTimer = null;

// Lấy thông tin user hiện tại
try {
    currentUser = JSON.parse(localStorage.getItem("user"));
} catch (e) {
    currentUser = null;
}

// Hàm format date
function formatDate(dateString) {
    if (!dateString) return 'N/A';
    try {
        const date = new Date(dateString);
        if (isNaN(date.getTime())) {
            // Nếu không parse được, thử giữ nguyên format nếu đã đúng
            if (typeof dateString === 'string' && dateString.includes('/')) {
                return dateString;
            }
            return 'N/A';
        }
        // Format: "hh:mm dd/MM/yyyy"
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const year = date.getFullYear();
        return `${hours}:${minutes} ${day}/${month}/${year}`;
    } catch (e) {
        return dateString || 'N/A';
    }
}

// Kết nối WebSocket cho modal chat với auto-reconnect
function connectExpertChatWebSocket() {
    if (!token || !currentUser) {
        console.error("No token or user found");
        return;
    }

    // Clear previous reconnect timer
    if (expertChatReconnectTimer) {
        clearTimeout(expertChatReconnectTimer);
        expertChatReconnectTimer = null;
    }

    // Disconnect nếu đã kết nối
    if (expertChatStompClient && expertChatStompClient.connected) {
        expertChatStompClient.disconnect();
    }

    // Tự động detect protocol: wss:// cho HTTPS, ws:// cho HTTP
    var socket = new SockJS('/ws');
    expertChatStompClient = Stomp.over(socket);
    
    // Disable debug logging
    expertChatStompClient.debug = function(str) {
        if (str && str.indexOf('ERROR') !== -1) {
            console.error('STOMP: ' + str);
        }
    };
    
    expertChatStompClient.connect({
        'Authorization': 'Bearer ' + token
    }, function(frame) {
        expertChatReconnectAttempts = 0;
        expertChatReconnectDelay = 1000;
        
        // Subscribe để nhận tin nhắn real-time
        expertChatStompClient.subscribe('/topic/chat/' + currentUser.id, function(message) {
            var data = JSON.parse(message.body);
            if (data.error) {
                showExpertChatError(data.error, 'error');
            } else {
                // Chỉ hiển thị tin nhắn từ expert hiện tại
                if (currentExpertUserId && 
                    (String(data.senderId) === String(currentExpertUserId) || 
                     String(data.receiverId) === String(currentExpertUserId))) {
                    displayNewMessage(data);
                    // Đánh dấu đã đọc nếu là tin nhắn nhận được
                    if (String(data.receiverId) === String(currentUser.id)) {
                        markExpertChatMessageAsRead(data.id);
                    }
                }
            }
        });
    }, function(error) {
        console.error('Expert Chat WebSocket connection error:', error);
        
        // Auto-reconnect
        if (expertChatReconnectAttempts < expertChatMaxReconnectAttempts) {
            expertChatReconnectAttempts++;
            expertChatReconnectDelay = Math.min(expertChatReconnectDelay * 2, 30000);
            
            expertChatReconnectTimer = setTimeout(function() {
                connectExpertChatWebSocket();
            }, expertChatReconnectDelay);
        }
    });
}

// Khởi tạo chat với expert
function initExpertChat(expertUserId, expertName) {
    currentExpertUserId = expertUserId;
    currentExpertName = expertName;
    
    // Kiểm tra đăng nhập
    if (!token) {
        showExpertChatError("Vui lòng đăng nhập để gửi tin nhắn", 'warning');
        window.location.href = "/login";
        return;
    }
    
    // Hiển thị modal chat
    const chatModal = new bootstrap.Modal(document.getElementById('expertChatModal'));
    chatModal.show();
    
    // Kết nối WebSocket
    connectExpertChatWebSocket();
    
    // Load conversation (lịch sử)
    loadConversation();
    
    // Disconnect khi đóng modal
    $('#expertChatModal').on('hidden.bs.modal', function() {
        if (expertChatReconnectTimer) {
            clearTimeout(expertChatReconnectTimer);
            expertChatReconnectTimer = null;
        }
        if (expertChatStompClient && expertChatStompClient.connected) {
            expertChatStompClient.disconnect();
        }
    });
}

// Load conversation giữa user và expert
async function loadConversation() {
    if (!currentExpertUserId) return;
    
    try {
        const response = await fetch(`/api/message/user/conversation?expertUserId=${currentExpertUserId}`, {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            const messages = await response.json();
            displayMessages(messages);
        } else {
            showExpertChatError("Không thể tải tin nhắn", 'error');
        }
    } catch (error) {
        console.error("Error loading conversation:", error);
        showExpertChatError("Lỗi kết nối", 'error');
    }
}

// Hiển thị tin nhắn (lịch sử)
function displayMessages(messages) {
    const messagesContainer = document.getElementById('expertChatMessages');
    if (!messagesContainer) return;
    
    messagesContainer.innerHTML = '';
    
    messages.forEach(message => {
        // Kiểm tra xem message đã tồn tại chưa (tránh duplicate với WebSocket)
        if (document.getElementById('expert-message-' + message.id)) {
            return;
        }
        
        const messageDiv = document.createElement('div');
        const isSender = message.sender.id === currentUser.id;
        
        messageDiv.className = `d-flex mb-3 ${isSender ? 'justify-content-end' : 'justify-content-start'}`;
        messageDiv.id = 'expert-message-' + message.id;
        
        // Xử lý hiển thị file/hình ảnh
        let messageBody = '';
        if (message.messageType === 'image' && message.fileUrl) {
            messageBody = `
                <img src="${message.fileUrl}" style="max-width: 300px; max-height: 300px; border-radius: 8px; cursor: pointer;" 
                     onclick="window.open('${message.fileUrl}', '_blank')" alt="${escapeHtml(message.fileName || 'Image')}">
                ${message.content ? '<p class="mt-2 mb-0">' + escapeHtml(message.content) + '</p>' : ''}
            `;
        } else if (message.messageType === 'file' && message.fileUrl) {
            messageBody = `
                <a href="${message.fileUrl}" target="_blank" class="${isSender ? 'text-white' : 'text-primary'}" style="text-decoration: none;">
                    <i class="bi bi-file-earmark me-2"></i>
                    <strong>${escapeHtml(message.fileName || 'File')}</strong>
                </a>
                ${message.content ? '<p class="mt-2 mb-0">' + escapeHtml(message.content) + '</p>' : ''}
            `;
        } else {
            messageBody = escapeHtml(message.content || '');
        }
        
        const messageContent = `
            <div class="message-bubble ${isSender ? 'bg-primary text-white' : 'bg-light'} p-3 rounded" style="max-width: 70%;">
                <div class="small ${isSender ? 'text-white-50' : 'text-muted'} mb-1">${isSender ? 'Bạn' : currentExpertName}</div>
                <div>${messageBody}</div>
                <div class="small ${isSender ? 'text-white-50' : 'text-muted'} mt-1">${formatDate(message.createdAt)}</div>
            </div>
        `;
        
        messageDiv.innerHTML = messageContent;
        messagesContainer.appendChild(messageDiv);
    });
    
    // Scroll xuống cuối
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

// Gửi tin nhắn qua WebSocket
function sendMessageToExpert() {
    const messageInput = document.getElementById('expertChatInput');
    const content = messageInput.value.trim();
    const previewDiv = document.getElementById('expertChatFilePreview');
    const fileUrl = previewDiv ? previewDiv.dataset.fileUrl : null;
    const fileName = previewDiv ? previewDiv.dataset.fileName : null;
    const fileType = previewDiv ? previewDiv.dataset.fileType : null;
    
    if (!content && !fileUrl) {
        showExpertChatError("Vui lòng nhập tin nhắn hoặc chọn file", 'warning');
        return;
    }
    
    if (!currentExpertUserId) return;
    
    if (expertChatStompClient && expertChatStompClient.connected) {
        const message = {
            receiverId: currentExpertUserId,
            content: content || '',
            messageType: fileUrl ? (fileType === 'image' ? 'image' : 'file') : 'text',
            fileUrl: fileUrl || null,
            fileName: fileName || null,
            fileType: fileType || null
        };
        
        expertChatStompClient.send("/app/chat.sendMessage", {}, JSON.stringify(message));
        
        // Clear input
        messageInput.value = '';
        clearExpertChatFilePreview();
    } else {
        showExpertChatError("Chưa kết nối WebSocket. Đang thử kết nối lại...", 'error');
        connectExpertChatWebSocket();
        // Retry sau 1 giây
        setTimeout(() => {
            if (expertChatStompClient && expertChatStompClient.connected) {
                sendMessageToExpert();
            }
        }, 1000);
    }
}

// Hiển thị tin nhắn mới (real-time)
function displayNewMessage(message) {
    const messagesContainer = document.getElementById('expertChatMessages');
    if (!messagesContainer) return;
    
    // Kiểm tra xem message đã tồn tại chưa (tránh duplicate)
    if (document.getElementById('expert-message-' + message.id)) {
        return;
    }
    
    const isSender = String(message.senderId) === String(currentUser.id);
    const messageDiv = document.createElement('div');
    messageDiv.className = `d-flex mb-3 ${isSender ? 'justify-content-end' : 'justify-content-start'}`;
    messageDiv.id = 'expert-message-' + message.id;
    
    // Xử lý hiển thị file/hình ảnh
    let messageBody = '';
    if (message.messageType === 'image' && message.fileUrl) {
        messageBody = `
            <img src="${message.fileUrl}" style="max-width: 300px; max-height: 300px; border-radius: 8px; cursor: pointer;" 
                 onclick="window.open('${message.fileUrl}', '_blank')" alt="${escapeHtml(message.fileName || 'Image')}">
            ${message.content ? '<p class="mt-2 mb-0">' + escapeHtml(message.content) + '</p>' : ''}
        `;
    } else if (message.messageType === 'file' && message.fileUrl) {
        messageBody = `
            <a href="${message.fileUrl}" target="_blank" class="${isSender ? 'text-white' : 'text-primary'}" style="text-decoration: none;">
                <i class="bi bi-file-earmark me-2"></i>
                <strong>${escapeHtml(message.fileName || 'File')}</strong>
            </a>
            ${message.content ? '<p class="mt-2 mb-0">' + escapeHtml(message.content) + '</p>' : ''}
        `;
    } else {
        messageBody = escapeHtml(message.content || '');
    }
    
    const messageContent = `
        <div class="message-bubble ${isSender ? 'bg-primary text-white' : 'bg-light'} p-3 rounded" style="max-width: 70%;">
            <div class="small ${isSender ? 'text-white-50' : 'text-muted'} mb-1">${isSender ? 'Bạn' : currentExpertName}</div>
            <div>${messageBody}</div>
            <div class="small ${isSender ? 'text-white-50' : 'text-muted'} mt-1">${formatDate(message.createdAt)}</div>
        </div>
    `;
    
    messageDiv.innerHTML = messageContent;
    messagesContainer.appendChild(messageDiv);
    
    // Scroll xuống cuối
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

// Đánh dấu đã đọc qua WebSocket
function markExpertChatMessageAsRead(messageId) {
    if (expertChatStompClient && expertChatStompClient.connected) {
        expertChatStompClient.send("/app/chat.markRead", {}, JSON.stringify({
            messageId: messageId
        }));
    }
}

// Xử lý nhấn Enter
function handleExpertChatKeyPress(event) {
    if (event.key === 'Enter') {
        sendMessageToExpert();
    }
}

// Chọn file
function handleExpertChatFileSelect(event) {
    const file = event.target.files[0];
    if (!file) return;

    // Validate file size (max 10MB)
    if (file.size > 10 * 1024 * 1024) {
        showExpertChatError("File không được vượt quá 10MB", 'error');
        return;
    }

    // Show preview
    const previewDiv = document.getElementById('expertChatFilePreview');
    const previewContent = document.getElementById('expertChatPreviewContent');
    previewDiv.style.display = 'block';

    if (file.type.startsWith('image/')) {
        const reader = new FileReader();
        reader.onload = function(e) {
            previewContent.innerHTML = `
                <img src="${e.target.result}" style="max-width: 200px; max-height: 200px; border-radius: 8px;" alt="Preview">
                <p class="mt-2 mb-0"><strong>${file.name}</strong></p>
            `;
        };
        reader.readAsDataURL(file);
    } else {
        previewContent.innerHTML = `
            <i class="bi bi-file-earmark fs-1"></i>
            <p class="mt-2 mb-0"><strong>${file.name}</strong></p>
            <small class="text-muted">${(file.size / 1024).toFixed(2)} KB</small>
        `;
    }

    // Upload file
    uploadExpertChatFile(file);
}

// Upload file lên server
function uploadExpertChatFile(file) {
    if (!file) {
        showExpertChatError("Không có file để upload", 'error');
        return;
    }

    // Show loading
    const previewContent = document.getElementById('expertChatPreviewContent');
    previewContent.innerHTML = `
        <div class="text-center">
            <div class="spinner-border spinner-border-sm" role="status">
                <span class="visually-hidden">Đang upload...</span>
            </div>
            <p class="mt-2 mb-0">Đang upload file...</p>
        </div>
    `;

    const formData = new FormData();
    formData.append('file', file);
    formData.append('receiverId', currentExpertUserId);

    fetch('/api/message/upload-file', {
        method: 'POST',
        headers: {
            'Authorization': 'Bearer ' + token
        },
        body: formData
    })
    .then(response => {
        if (!response.ok) {
            return response.json().then(err => {
                throw new Error(err.error || 'Upload failed');
            });
        }
        return response.json();
    })
    .then(data => {
        if (data.error) {
            toastr.error(data.error);
            clearExpertChatFilePreview();
        } else {
            // Lưu thông tin file vào preview
            const previewDiv = document.getElementById('expertChatFilePreview');
            previewDiv.dataset.fileUrl = data.fileUrl;
            previewDiv.dataset.fileName = data.fileName;
            previewDiv.dataset.fileType = data.fileType;
            
            // Restore preview với file info
            if (file.type.startsWith('image/')) {
                const reader = new FileReader();
                reader.onload = function(e) {
                    previewContent.innerHTML = `
                        <img src="${e.target.result}" style="max-width: 200px; max-height: 200px; border-radius: 8px;" alt="Preview">
                        <p class="mt-2 mb-0"><strong>${data.fileName}</strong></p>
                        <small class="text-success"><i class="bi bi-check-circle"></i> Đã upload thành công</small>
                    `;
                };
                reader.readAsDataURL(file);
            } else {
                previewContent.innerHTML = `
                    <i class="bi bi-file-earmark fs-1"></i>
                    <p class="mt-2 mb-0"><strong>${data.fileName}</strong></p>
                    <small class="text-success"><i class="bi bi-check-circle"></i> Đã upload thành công</small>
                `;
            }
            
            showExpertChatError("File đã được upload thành công. Nhấn Gửi để gửi tin nhắn.", 'success');
        }
    })
    .catch(error => {
        console.error('Upload error:', error);
        showExpertChatError("Lỗi khi upload file: " + error.message, 'error');
        clearExpertChatFilePreview();
    });
}

// Clear file preview
function clearExpertChatFilePreview() {
    document.getElementById('expertChatFilePreview').style.display = 'none';
    document.getElementById('expertChatPreviewContent').innerHTML = '';
    document.getElementById('expertChatFileInput').value = '';
    delete document.getElementById('expertChatFilePreview').dataset.fileUrl;
    delete document.getElementById('expertChatFilePreview').dataset.fileName;
    delete document.getElementById('expertChatFilePreview').dataset.fileType;
}

// Escape HTML
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Event listener khi Enter
$(document).ready(function() {
    $('#expertChatSendBtn').on('click', sendMessageToExpert);
    
    // Xử lý nút liên hệ tư vấn
    $('#contactExpertBtn').on('click', function() {
        const expertId = $(this).data('expert-id');
        const expertName = $(this).data('expert-name');
        
        if (!expertId) {
            showExpertChatError("Chuyên gia này chưa có tài khoản đăng nhập", 'error');
            return;
        }
        
        initExpertChat(expertId, expertName);
    });
});

