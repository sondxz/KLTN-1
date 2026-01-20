// User quản lý tin nhắn với experts
var currentConversationExpertId = null;
var currentConversationExpertName = null;
var userMessagesStompClient = null;
var currentUser = null;
var userMessagesReconnectAttempts = 0;
var userMessagesMaxReconnectAttempts = 5;
var userMessagesReconnectDelay = 1000;
var userMessagesReconnectTimer = null;

// Lấy thông tin user hiện tại
try {
    currentUser = JSON.parse(localStorage.getItem("user"));
} catch (e) {
    currentUser = null;
}

// Kết nối WebSocket với auto-reconnect
function connectUserMessagesWebSocket() {
    if (!token || !currentUser) {
        console.error("No token or user found");
        return;
    }

    // Clear previous reconnect timer
    if (userMessagesReconnectTimer) {
        clearTimeout(userMessagesReconnectTimer);
        userMessagesReconnectTimer = null;
    }

    // Disconnect nếu đã kết nối
    if (userMessagesStompClient && userMessagesStompClient.connected) {
        userMessagesStompClient.disconnect();
    }

    // Tự động detect protocol: wss:// cho HTTPS, ws:// cho HTTP
    var wsProtocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
    var wsUrl = wsProtocol + '//' + window.location.host + '/ws';
    
    console.log('Connecting to WebSocket:', wsUrl);
    var socket = new SockJS(wsUrl);
    userMessagesStompClient = Stomp.over(socket);
    
    // Disable debug logging
    userMessagesStompClient.debug = function(str) {
        if (str && str.indexOf('ERROR') !== -1) {
            console.error('STOMP: ' + str);
        }
    };
    
    userMessagesStompClient.connect({
        'Authorization': 'Bearer ' + token
    }, function(frame) {
        userMessagesReconnectAttempts = 0;
        userMessagesReconnectDelay = 1000;
        
        // Subscribe để nhận tin nhắn real-time
        userMessagesStompClient.subscribe('/topic/chat/' + currentUser.id, function(message) {
            var data = JSON.parse(message.body);
            if (data.error) {
                toastr.error(data.error);
            } else {
                // Nếu đang xem conversation với expert này, hiển thị tin nhắn mới
                if (currentConversationExpertId && 
                    (String(data.senderId) === String(currentConversationExpertId) || 
                     String(data.receiverId) === String(currentConversationExpertId))) {
                    displayNewUserMessage(data);
                    // Đánh dấu đã đọc nếu là tin nhắn nhận được
                    if (String(data.receiverId) === String(currentUser.id)) {
                        markMessageAsRead(data.id);
                    }
                }
                // Reload danh sách experts để cập nhật số tin nhắn
                loadExpertsList();
                loadUnreadCount();
            }
        });
    }, function(error) {
        console.error('User Messages WebSocket connection error:', error);
        
        // Auto-reconnect
        if (userMessagesReconnectAttempts < userMessagesMaxReconnectAttempts) {
            userMessagesReconnectAttempts++;
            userMessagesReconnectDelay = Math.min(userMessagesReconnectDelay * 2, 30000);
            
            userMessagesReconnectTimer = setTimeout(function() {
                connectUserMessagesWebSocket();
            }, userMessagesReconnectDelay);
        } else {
            toastr.error("Không thể kết nối chat. Vui lòng refresh trang.");
        }
    });
}

// Load danh sách experts đã chat
async function loadExpertsList() {
    try {
        const response = await fetch('/api/message/user/conversation-partners', {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            const experts = await response.json();
            displayExpertsList(experts);
        } else {
            toastr.error("Không thể tải danh sách chuyên gia");
        }
    } catch (error) {
        console.error("Error loading experts:", error);
        toastr.error("Lỗi kết nối");
    }
}

// Hiển thị danh sách experts
function displayExpertsList(experts) {
    const expertsList = document.getElementById('expertsList');
    if (!expertsList) return;
    
    if (experts.length === 0) {
        expertsList.innerHTML = `
            <div class="text-center text-muted p-3">
                <i class="bi bi-inbox"></i>
                <p class="mt-2 mb-0">Chưa có tin nhắn nào</p>
            </div>
        `;
        return;
    }
    
    let html = '';
    experts.forEach(expert => {
        html += `
            <a href="#" class="list-group-item list-group-item-action" onclick="openConversation(${expert.id}, '${escapeHtml(expert.fullname || expert.username)}')">
                <div class="d-flex w-100 justify-content-between align-items-center">
                    <div class="d-flex align-items-center">
                        <i class="bi bi-person-circle me-2 fs-4 text-primary"></i>
                        <div>
                            <h6 class="mb-0">${escapeHtml(expert.fullname || expert.username)}</h6>
                            <small class="text-muted">Chuyên gia</small>
                        </div>
                    </div>
                </div>
            </a>
        `;
    });
    
    expertsList.innerHTML = html;
}

// Mở conversation với expert
async function openConversation(expertId, expertName) {
    currentConversationExpertId = expertId;
    currentConversationExpertName = expertName;
    
    // Cập nhật title
    document.getElementById('conversationTitle').textContent = `Tin nhắn với ${expertName}`;
    
    // Hiển thị input để gửi tin nhắn
    const conversationContent = document.getElementById('conversationContent');
    if (!conversationContent) return;
    
    const chatUI = `
        <div id="userMessagesContainer" class="chat-messages" style="max-height: 400px; overflow-y: auto; background-color: #f8f9fa; padding: 1rem; min-height: 200px;">
            <div class="text-center text-muted p-3">
                <i class="bi bi-arrow-repeat spin"></i> Đang tải tin nhắn...
            </div>
        </div>
        <div class="border-top p-3">
            <div id="userFilePreview" class="file-preview mb-2" style="display: none;">
                <button type="button" class="btn-close float-end" onclick="clearUserFilePreview()"></button>
                <div id="userPreviewContent"></div>
            </div>
            <div class="d-flex gap-2 align-items-center">
                <input type="file" id="userFileInput" accept="image/*,.pdf,.doc,.docx,.txt,.xls,.xlsx,.ppt,.pptx,.zip,.rar" style="display: none;" onchange="handleUserFileSelect(event)">
                <button type="button" class="btn btn-outline-secondary" onclick="document.getElementById('userFileInput').click()" title="Gửi file/hình ảnh" style="min-width: 45px;">
                    <i class="bi bi-paperclip fs-5"></i>
                </button>
                <input type="text" class="form-control" id="userMessageInput" placeholder="Nhập tin nhắn hoặc chọn file để gửi..." onkeypress="handleUserKeyPress(event)">
                <button type="button" class="btn btn-primary" id="userSendBtn" onclick="sendUserMessage()">
                    <i class="bi bi-send"></i> Gửi
                </button>
            </div>
            <small class="text-muted mt-2 d-block text-end">Click icon 📎 để gửi hình ảnh hoặc tài liệu</small>
        </div>
    `;
    conversationContent.innerHTML = chatUI;
    
    // Kết nối WebSocket nếu chưa kết nối
    if (!userMessagesStompClient || !userMessagesStompClient.connected) {
        connectUserMessagesWebSocket();
    }
    
    // Load conversation
    await loadConversationWithExpert();
}

// Load conversation với expert
async function loadConversationWithExpert() {
    if (!currentConversationExpertId) return;
    
    try {
        const response = await fetch(`/api/message/user/conversation?expertUserId=${currentConversationExpertId}`, {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            const messages = await response.json();
            displayUserMessages(messages);
        } else {
            toastr.error("Không thể tải tin nhắn");
        }
    } catch (error) {
        console.error("Error loading conversation:", error);
        toastr.error("Lỗi kết nối");
    }
}

// Hiển thị tin nhắn
function displayUserMessages(messages) {
    const messagesContainer = document.getElementById('userMessagesContainer');
    if (!messagesContainer) return;
    
    messagesContainer.innerHTML = '';
    
    // Đánh dấu tất cả tin nhắn chưa đọc là đã đọc
    const unreadMessages = messages.filter(m => 
        String(m.receiver.id) === String(currentUser.id) && !m.isRead
    );
    
    // Đánh dấu đã đọc tất cả tin nhắn chưa đọc
    if (unreadMessages.length > 0) {
        unreadMessages.forEach(message => {
            markMessageAsRead(message.id);
        });
        // Reload unread count sau khi đánh dấu
        setTimeout(() => {
            loadUnreadCount();
        }, 500);
    }
    
    messages.forEach(message => {
        const isSender = String(message.sender.id) === String(currentUser.id);
        const messageDiv = document.createElement('div');
        messageDiv.className = `message-item ${isSender ? 'sent' : 'received'}`;
        
        let messageBody = '';
        if (message.messageType === 'image' && message.fileUrl) {
            messageBody = `
                <img src="${message.fileUrl}" class="chat-image" alt="${escapeHtml(message.fileName || 'Image')}" onclick="openImageModal('${message.fileUrl}')">
                ${message.content ? '<p class="mt-2 mb-0">' + escapeHtml(message.content) + '</p>' : ''}
            `;
        } else if (message.messageType === 'file' && message.fileUrl) {
            messageBody = `
                <a href="${message.fileUrl}" target="_blank" class="chat-file">
                    <i class="bi bi-file-earmark me-2"></i>
                    <span>${escapeHtml(message.fileName || 'File')}</span>
                </a>
                ${message.content ? '<p class="mt-2 mb-0">' + escapeHtml(message.content) + '</p>' : ''}
            `;
        } else {
            messageBody = escapeHtml(message.content || '');
        }
        
        const messageBubble = `
            <div class="message-bubble">
                <div>${messageBody}</div>
                <div class="small mt-1 ${isSender ? 'text-white-50' : 'text-muted'}">${formatDate(message.createdAt)}</div>
            </div>
        `;
        
        messageDiv.innerHTML = messageBubble;
        messagesContainer.appendChild(messageDiv);
    });
    
    // Scroll to bottom
    setTimeout(() => {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }, 100);
}

// Hiển thị tin nhắn mới (real-time)
function displayNewUserMessage(message) {
    const messagesContainer = document.getElementById('userMessagesContainer');
    if (!messagesContainer) return;
    
    // Kiểm tra xem message đã tồn tại chưa
    if (document.getElementById('user-message-' + message.id)) {
        return;
    }
    
    const isSender = String(message.senderId) === String(currentUser.id);
    const messageDiv = document.createElement('div');
    messageDiv.className = `message-item ${isSender ? 'sent' : 'received'}`;
    messageDiv.id = 'user-message-' + message.id;
    
    let messageBody = '';
    if (message.messageType === 'image' && message.fileUrl) {
        messageBody = `
            <img src="${message.fileUrl}" class="chat-image" alt="${escapeHtml(message.fileName || 'Image')}" onclick="openImageModal('${message.fileUrl}')">
            ${message.content ? '<p class="mt-2 mb-0">' + escapeHtml(message.content) + '</p>' : ''}
        `;
    } else if (message.messageType === 'file' && message.fileUrl) {
        messageBody = `
            <a href="${message.fileUrl}" target="_blank" class="chat-file">
                <i class="bi bi-file-earmark me-2"></i>
                <span>${escapeHtml(message.fileName || 'File')}</span>
            </a>
            ${message.content ? '<p class="mt-2 mb-0">' + escapeHtml(message.content) + '</p>' : ''}
        `;
    } else {
        messageBody = escapeHtml(message.content || '');
    }
    
    const messageBubble = `
        <div class="message-bubble">
            <div>${messageBody}</div>
            <div class="small mt-1 ${isSender ? 'text-white-50' : 'text-muted'}">${formatDate(message.createdAt)}</div>
        </div>
    `;
    
    messageDiv.innerHTML = messageBubble;
    messagesContainer.appendChild(messageDiv);
    
    // Scroll to bottom
    setTimeout(() => {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }, 100);
}

// Gửi tin nhắn
function sendUserMessage() {
    const messageInput = document.getElementById('userMessageInput');
    const content = messageInput.value.trim();
    const filePreviewDiv = document.getElementById('userFilePreview');
    const fileUrl = filePreviewDiv ? filePreviewDiv.dataset.fileUrl : null;
    const fileName = filePreviewDiv ? filePreviewDiv.dataset.fileName : null;
    const fileType = filePreviewDiv ? filePreviewDiv.dataset.fileType : null;
    
    if (!content && !fileUrl) {
        toastr.warning("Vui lòng nhập tin nhắn hoặc chọn file để gửi");
        return;
    }
    
    if (!currentConversationExpertId) return;
    
    if (userMessagesStompClient && userMessagesStompClient.connected) {
        const message = {
            receiverId: currentConversationExpertId,
            content: content || '',
            messageType: fileUrl ? (fileType === 'image' ? 'image' : 'file') : 'text',
            fileUrl: fileUrl || null,
            fileName: fileName || null,
            fileType: fileType || null
        };
        
        userMessagesStompClient.send("/app/chat.sendMessage", {}, JSON.stringify(message));
        
        // Clear input
        messageInput.value = '';
        clearUserFilePreview();
    } else {
        toastr.error("Chưa kết nối WebSocket. Đang thử kết nối lại...");
        connectUserMessagesWebSocket();
    }
}

// File upload
function handleUserFileSelect(event) {
    const file = event.target.files[0];
    if (!file) return;
    
    if (file.size > 10 * 1024 * 1024) {
        toastr.error("File không được vượt quá 10MB");
        return;
    }
    
    uploadUserFile(file);
}

function uploadUserFile(file) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('receiverId', currentConversationExpertId);
    
    const previewContent = document.getElementById('userPreviewContent');
    previewContent.innerHTML = `<div class="spinner-border spinner-border-sm me-2" role="status"></div> Đang tải lên...`;
    document.getElementById('userFilePreview').style.display = 'block';
    
    fetch('/api/message/upload-file', {
        method: 'POST',
        headers: {
            'Authorization': 'Bearer ' + token
        },
        body: formData
    })
    .then(response => response.json())
    .then(data => {
        if (data.error) {
            toastr.error(data.error);
            clearUserFilePreview();
        } else {
            const previewDiv = document.getElementById('userFilePreview');
            previewDiv.dataset.fileUrl = data.fileUrl;
            previewDiv.dataset.fileName = data.fileName;
            previewDiv.dataset.fileType = data.fileType;
            
            if (data.fileType === 'image') {
                previewContent.innerHTML = `
                    <img src="${data.fileUrl}" class="chat-image" alt="Preview">
                    <p class="mt-2 mb-0"><strong>${data.fileName}</strong></p>
                `;
            } else {
                previewContent.innerHTML = `
                    <i class="bi bi-file-earmark fs-4 me-2"></i>
                    <p class="mt-2 mb-0"><strong>${data.fileName}</strong></p>
                `;
            }
            toastr.success("File đã được upload thành công");
        }
    })
    .catch(error => {
        console.error('Upload error:', error);
        toastr.error("Lỗi khi upload file");
        clearUserFilePreview();
    });
}

function clearUserFilePreview() {
    document.getElementById('userFilePreview').style.display = 'none';
    document.getElementById('userPreviewContent').innerHTML = '';
    document.getElementById('userFilePreview').dataset.fileUrl = '';
    document.getElementById('userFilePreview').dataset.fileName = '';
    document.getElementById('userFilePreview').dataset.fileType = '';
}

function handleUserKeyPress(event) {
    if (event.which === 13) {
        sendUserMessage();
    }
}

// Đánh dấu đã đọc
async function markMessageAsRead(messageId) {
    try {
        const response = await fetch(`/api/message/mark-read?messageId=${messageId}`, {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        });
        
        if (response.ok) {
            // Reload unread count sau khi đánh dấu
            loadUnreadCount();
        }
    } catch (error) {
        console.error("Error marking as read:", error);
    }
}

// Load unread count
async function loadUnreadCount() {
    try {
        const response = await fetch('/api/message/user/unread-count', {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        });
        
        if (response.ok) {
            const count = await response.json();
            localStorage.setItem('unreadMessageCount', count);
            
            // Update header badge
            const badge = document.getElementById('headerUnreadBadge');
            if (badge) {
                if (count > 0) {
                    badge.textContent = count > 99 ? '99+' : count;
                    badge.style.display = 'inline';
                } else {
                    badge.style.display = 'none';
                }
            }
            
            // Trigger custom event để main.js có thể cập nhật badge
            window.dispatchEvent(new CustomEvent('unreadCountUpdated', { detail: count }));
        }
    } catch (error) {
        console.error("Error loading unread count:", error);
    }
}

// Format date
function formatDate(dateString) {
    if (!dateString) return 'N/A';
    try {
        const date = new Date(dateString);
        if (isNaN(date.getTime())) return dateString;
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

// Escape HTML
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Open image modal
function openImageModal(imageUrl) {
    document.getElementById('modalImage').src = imageUrl;
    const imageModal = new bootstrap.Modal(document.getElementById('imageModal'));
    imageModal.show();
}

// Load khi trang được tải
$(document).ready(function() {
    // Kết nối WebSocket
    connectUserMessagesWebSocket();
    
    // Load danh sách experts
    loadExpertsList();
    
    // Load unread count
    loadUnreadCount();
    
    // Auto refresh unread count mỗi 30 giây
    setInterval(function() {
        loadUnreadCount();
    }, 30000);
    
    // Disconnect khi đóng trang
    window.addEventListener('beforeunload', function() {
        if (userMessagesReconnectTimer) {
            clearTimeout(userMessagesReconnectTimer);
            userMessagesReconnectTimer = null;
        }
        if (userMessagesStompClient && userMessagesStompClient.connected) {
            userMessagesStompClient.disconnect();
        }
    });
});

