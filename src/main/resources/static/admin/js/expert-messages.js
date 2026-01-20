var currentConversationUserId = null;
var currentConversationUserName = null;
var expertMessagesStompClient = null;
var currentExpertUser = null;

try {
    currentExpertUser = JSON.parse(localStorage.getItem("user"));
} catch (e) {
    currentExpertUser = null;
}

function connectExpertMessagesWebSocket() {
    if (!token || !currentExpertUser) {
        console.error("No token or user found for expert messages");
        return;
    }

    if (expertMessagesReconnectTimer) {
        clearTimeout(expertMessagesReconnectTimer);
        expertMessagesReconnectTimer = null;
    }

    if (expertMessagesStompClient && expertMessagesStompClient.connected) {
        expertMessagesStompClient.disconnect();
    }

    var socket = new SockJS('/ws');
    expertMessagesStompClient = Stomp.over(socket);
    
    expertMessagesStompClient.debug = function(str) {
        if (str && str.indexOf('ERROR') !== -1) {
            console.error('STOMP: ' + str);
        }
    };
    
    expertMessagesStompClient.connect({
        'Authorization': 'Bearer ' + token
    }, function(frame) {
        expertMessagesReconnectAttempts = 0;
        expertMessagesReconnectDelay = 1000;
        
        expertMessagesStompClient.subscribe('/topic/chat/' + currentExpertUser.id, function(message) {
            var data = JSON.parse(message.body);
            if (data.error) {
                toastr.error(data.error);
            } else {
                if (currentConversationUserId && 
                    (String(data.senderId) === String(currentConversationUserId) || 
                     String(data.receiverId) === String(currentConversationUserId))) {
                    displayNewExpertMessage(data);
                    if (String(data.receiverId) === String(currentExpertUser.id)) {
                        markMessageAsRead(data.id);
                    }
                }
                loadConversationPartners();
                loadUnreadCount();
            }
        });
    }, function(error) {
        console.error('Expert Messages WebSocket connection error:', error);
        
        if (expertMessagesReconnectAttempts < expertMessagesMaxReconnectAttempts) {
            expertMessagesReconnectAttempts++;
            expertMessagesReconnectDelay = Math.min(expertMessagesReconnectDelay * 2, 30000);
            
            expertMessagesReconnectTimer = setTimeout(function() {
                connectExpertMessagesWebSocket();
            }, expertMessagesReconnectDelay);
        } else {
            toastr.error("Không thể kết nối chat. Vui lòng refresh trang.");
        }
    });
}

async function loadConversationPartners() {
    try {
        const response = await fetch('/api/message/expert/conversation-partners', {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            }
        });
        
        if (checkResponseError(response)) {
            return;
        }
        
        if (response.ok) {
            const partners = await response.json();
            displayConversationPartners(partners);
        } else {
            toastr.error("Không thể tải danh sách người dùng");
        }
    } catch (error) {
        console.error("Error loading partners:", error);
        toastr.error("Lỗi kết nối");
    }
}

// Hiển thị danh sách người đã nhắn tin
function displayConversationPartners(partners) {
    const partnersList = document.getElementById('conversationPartnersList');
    partnersList.innerHTML = '';
    
    if (partners.length === 0) {
        partnersList.innerHTML = '<div class="text-center text-muted p-3">Chưa có tin nhắn nào</div>';
        return;
    }
    
    partners.forEach(partner => {
        const partnerDiv = document.createElement('div');
        partnerDiv.className = 'list-group-item list-group-item-action cursor-pointer';
        partnerDiv.innerHTML = `
            <div class="d-flex justify-content-between align-items-center">
                <div>
                    <h6 class="mb-0">${partner.fullname || partner.username}</h6>
                    <small class="text-muted">${partner.email}</small>
                </div>
                <button class="btn btn-sm btn-primary" onclick="openConversation(${partner.id}, '${partner.fullname || partner.username}')">
                    Xem
                </button>
            </div>
        `;
        partnersList.appendChild(partnerDiv);
    });
}

function connectExpertMessagesWebSocket() {
    if (!token || !currentExpertUser) {
        console.error("No token or user found for expert messages");
        return;
    }

    if (expertMessagesStompClient && expertMessagesStompClient.connected) {
        expertMessagesStompClient.disconnect();
    }

    var socket = new SockJS('/ws');
    expertMessagesStompClient = Stomp.over(socket);
    
    expertMessagesStompClient.connect({
        'Authorization': 'Bearer ' + token
    }, function(frame) {
        expertMessagesStompClient.subscribe('/topic/chat/' + currentExpertUser.id, function(message) {
            var data = JSON.parse(message.body);
            if (data.error) {
                toastr.error(data.error);
            } else {
                if (currentConversationUserId && 
                    (data.senderId === currentConversationUserId || data.receiverId === currentConversationUserId)) {
                    displayNewExpertMessage(data);
                    if (data.receiverId === currentExpertUser.id) {
                        markMessageAsRead(data.id);
                        loadUnreadCount();
                    }
                }
            }
        });
    }, function(error) {
        console.error('Expert Messages WebSocket connection error:', error);
    });
}

async function openConversation(userId, userName) {
    currentConversationUserId = userId;
    currentConversationUserName = userName;
    
    document.getElementById('conversationTitle').textContent = `Tin nhắn với ${userName}`;
    
    const conversationContent = document.getElementById('conversationContent');
    if (!conversationContent) {
        console.error("conversationContent not found!");
        return;
    }
    
    const replyInput = `
        <div id="expertReplyMessagesContainer" style="max-height: 400px; overflow-y: auto; background-color: #f8f9fa; padding: 1rem; min-height: 200px;">
            <div class="text-center text-muted p-3">
                <i class="bi bi-arrow-repeat spin"></i> Đang tải tin nhắn...
            </div>
        </div>
        <div class="border-top p-3">
            <div id="expertReplyFilePreview" class="mb-2 p-2 bg-light rounded" style="display: none; border: 2px dashed #dee2e6;">
                <button type="button" class="btn-close float-end" onclick="clearExpertReplyFilePreview()"></button>
                <div id="expertReplyPreviewContent"></div>
            </div>
            <div class="input-group">
                <input type="file" id="expertReplyFileInput" accept="image/*,.pdf,.doc,.docx,.txt,.xls,.xlsx,.ppt,.pptx,.zip,.rar" style="display: none;" onchange="handleExpertReplyFileSelect(event)">
                <button class="btn btn-outline-secondary" type="button" onclick="document.getElementById('expertReplyFileInput').click()" title="Gửi file/hình ảnh">
                    <i class="bi bi-paperclip fs-5"></i>
                </button>
                <input type="text" class="form-control" id="expertReplyInput" placeholder="Nhập tin nhắn hoặc chọn file để gửi...">
                <button class="btn btn-primary" type="button" id="expertReplySendBtn">
                    <i class="bi bi-send-fill"></i> Gửi
                </button>
            </div>
            <small class="text-muted d-block mt-2">
                <i class="bi bi-info-circle"></i> Click icon <i class="bi bi-paperclip"></i> để gửi hình ảnh hoặc tài liệu
            </small>
        </div>
    `;
    conversationContent.innerHTML = replyInput;
    
    if (!expertMessagesStompClient || !expertMessagesStompClient.connected) {
        connectExpertMessagesWebSocket();
    }
    
    $('#expertReplySendBtn').off('click').on('click', sendDirectReply);
    $('#expertReplyInput').off('keypress').on('keypress', function(e) {
        if (e.which === 13) {
            sendDirectReply();
        }
    });
    
    await loadConversationWithUser();
}

function displayNewExpertMessage(message) {
    let messagesContainer = document.getElementById('expertReplyMessagesContainer');
    if (!messagesContainer) {
        messagesContainer = document.getElementById('expertReplyMessages');
    }
    
    if (!messagesContainer) return;
    
    // Kiểm tra xem message đã tồn tại chưa (tránh duplicate)
    if (document.getElementById('expert-reply-message-' + message.id)) {
        return;
    }
    
    const currentUser = JSON.parse(localStorage.getItem('user'));
    const currentExpertId = currentUser ? currentUser.id : null;
    const isFromExpert = String(message.senderId) === String(currentExpertId);
    
    const messageDiv = document.createElement('div');
    messageDiv.className = `d-flex mb-3 ${isFromExpert ? 'justify-content-end' : 'justify-content-start'}`;
    messageDiv.id = 'expert-reply-message-' + message.id;
    
    const senderName = isFromExpert ? 'Bạn' : (currentConversationUserName || (message.senderName || 'Người dùng'));
    
    let createdAt = 'N/A';
    if (message.createdAt) {
        try {
            const date = new Date(message.createdAt);
            if (!isNaN(date.getTime())) {
                const hours = String(date.getHours()).padStart(2, '0');
                const minutes = String(date.getMinutes()).padStart(2, '0');
                const day = String(date.getDate()).padStart(2, '0');
                const month = String(date.getMonth() + 1).padStart(2, '0');
                const year = date.getFullYear();
                createdAt = `${hours}:${minutes} ${day}/${month}/${year}`;
            }
        } catch (e) {
            createdAt = message.createdAt || 'N/A';
        }
    }
    
    let messageBody = '';
    if (message.messageType === 'image' && message.fileUrl) {
        messageBody = `
            <img src="${message.fileUrl}" style="max-width: 300px; max-height: 300px; border-radius: 8px; cursor: pointer; display: block;" 
                 onclick="window.open('${message.fileUrl}', '_blank')" alt="${escapeHtml(message.fileName || 'Image')}">
            ${message.content ? '<p class="mt-2 mb-0">' + escapeHtml(message.content) + '</p>' : ''}
        `;
    } else if (message.messageType === 'file' && message.fileUrl) {
        messageBody = `
            <a href="${message.fileUrl}" target="_blank" class="${isFromExpert ? 'text-white' : 'text-primary'}" style="text-decoration: none; display: inline-block;">
                <i class="bi bi-file-earmark me-2"></i>
                <strong>${escapeHtml(message.fileName || 'File')}</strong>
            </a>
            ${message.content ? '<p class="mt-2 mb-0">' + escapeHtml(message.content) + '</p>' : ''}
        `;
    } else {
        messageBody = escapeHtml(message.content || '');
    }
    
    const messageContent = `
        <div class="message-bubble ${isFromExpert ? 'bg-primary text-white' : 'bg-light'} p-3 rounded" style="max-width: 70%;">
            <div class="small mb-1 fw-bold">${escapeHtml(senderName)}</div>
            <div>${messageBody}</div>
            <div class="small mt-1 ${isFromExpert ? 'text-white-50' : 'text-muted'}">${escapeHtml(createdAt)}</div>
        </div>
    `;
    
    messageDiv.innerHTML = messageContent;
    messagesContainer.appendChild(messageDiv);
    
    setTimeout(() => {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }, 100);
}

function sendDirectReply() {
    const input = document.getElementById('expertReplyInput');
    const content = input.value.trim();
    const previewDiv = document.getElementById('expertReplyFilePreview');
    const fileUrl = previewDiv ? previewDiv.dataset.fileUrl : null;
    const fileName = previewDiv ? previewDiv.dataset.fileName : null;
    const fileType = previewDiv ? previewDiv.dataset.fileType : null;
    
    if (!content && !fileUrl) {
        toastr.warning("Vui lòng nhập tin nhắn hoặc chọn file");
        return;
    }
    
    if (!currentConversationUserId) return;
    
    if (expertMessagesStompClient && expertMessagesStompClient.connected) {
        // Lấy tin nhắn cuối cùng từ user để reply (vẫn dùng REST API để lấy messageId)
        fetch(`/api/message/expert/conversation?userId=${currentConversationUserId}`, {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            }
        })
        .then(conversationResponse => conversationResponse.json())
        .then(messages => {
            const userMessages = messages.filter(m => m.sender.id === currentConversationUserId && !m.isRead);
            const lastMessageId = userMessages.length > 0 ? userMessages[userMessages.length - 1].id : messages.filter(m => m.sender.id === currentConversationUserId).pop()?.id;
            
            if (lastMessageId) {
                const message = {
                    receiverId: currentConversationUserId,
                    content: content || '',
                    messageType: fileUrl ? (fileType === 'image' ? 'image' : 'file') : 'text',
                    fileUrl: fileUrl || null,
                    fileName: fileName || null,
                    fileType: fileType || null
                };
                
                expertMessagesStompClient.send("/app/chat.sendMessage", {}, JSON.stringify(message));
                
                markExpertMessageAsReadViaWebSocket(lastMessageId);
                
                input.value = '';
                clearExpertReplyFilePreview();
                toastr.success("Đã gửi trả lời");
                
                loadConversationPartners();
                loadUnreadCount();
            } else {
                toastr.error("Không tìm thấy tin nhắn để trả lời");
            }
        })
        .catch(error => {
            console.error("Error getting conversation:", error);
            toastr.error("Lỗi khi lấy thông tin tin nhắn");
        });
    } else {
        toastr.error("Chưa kết nối WebSocket. Đang thử kết nối lại...");
        connectExpertMessagesWebSocket();
        setTimeout(() => {
            if (expertMessagesStompClient && expertMessagesStompClient.connected) {
                sendDirectReply();
            }
        }, 1000);
    }
}

// Hiển thị tin nhắn mới (real-time)
function displayNewExpertMessage(message) {
    let messagesContainer = document.getElementById('expertReplyMessagesContainer');
    if (!messagesContainer) {
        messagesContainer = document.getElementById('expertReplyMessages');
    }
    if (!messagesContainer) return;
    
    // Kiểm tra xem message đã tồn tại chưa (tránh duplicate)
    if (document.getElementById('message-' + message.id)) {
        return;
    }
    
    const isFromExpert = message.senderId === currentExpertUser.id;
    const messageDiv = document.createElement('div');
    messageDiv.className = `d-flex mb-3 ${isFromExpert ? 'justify-content-end' : 'justify-content-start'}`;
    messageDiv.id = 'message-' + message.id;
    
    const senderName = isFromExpert ? 'Bạn' : (currentConversationUserName || (message.senderName || 'Người dùng'));
    
    let createdAt = 'N/A';
    if (message.createdAt) {
        try {
            const date = new Date(message.createdAt);
            if (!isNaN(date.getTime())) {
                const hours = String(date.getHours()).padStart(2, '0');
                const minutes = String(date.getMinutes()).padStart(2, '0');
                const day = String(date.getDate()).padStart(2, '0');
                const month = String(date.getMonth() + 1).padStart(2, '0');
                const year = date.getFullYear();
                createdAt = `${hours}:${minutes} ${day}/${month}/${year}`;
            }
        } catch (e) {
            createdAt = message.createdAt || 'N/A';
        }
    }
    
    let messageBody = '';
    if (message.messageType === 'image' && message.fileUrl) {
        messageBody = `
            <img src="${message.fileUrl}" style="max-width: 300px; max-height: 300px; border-radius: 8px; cursor: pointer; display: block;" 
                 onclick="window.open('${message.fileUrl}', '_blank')" alt="${escapeHtml(message.fileName || 'Image')}">
            ${message.content ? '<p class="mt-2 mb-0">' + escapeHtml(message.content) + '</p>' : ''}
        `;
    } else if (message.messageType === 'file' && message.fileUrl) {
        messageBody = `
            <a href="${message.fileUrl}" target="_blank" class="${isFromExpert ? 'text-white' : 'text-primary'}" style="text-decoration: none; display: inline-block;">
                <i class="bi bi-file-earmark me-2"></i>
                <strong>${escapeHtml(message.fileName || 'File')}</strong>
            </a>
            ${message.content ? '<p class="mt-2 mb-0">' + escapeHtml(message.content) + '</p>' : ''}
        `;
    } else {
        messageBody = escapeHtml(message.content || '');
    }
    
    const messageContent = `
        <div class="message-bubble ${isFromExpert ? 'bg-primary text-white' : 'bg-light'} p-3 rounded" style="max-width: 70%;">
            <div class="small mb-1 fw-bold">${escapeHtml(senderName)}</div>
            <div>${messageBody}</div>
            <div class="small mt-1 ${isFromExpert ? 'text-white-50' : 'text-muted'}">${escapeHtml(createdAt)}</div>
        </div>
    `;
    
    messageDiv.innerHTML = messageContent;
    messagesContainer.appendChild(messageDiv);
    
    setTimeout(() => {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }, 100);
}

function markExpertMessageAsReadViaWebSocket(messageId) {
    if (expertMessagesStompClient && expertMessagesStompClient.connected) {
        expertMessagesStompClient.send("/app/chat.markRead", {}, JSON.stringify({
            messageId: messageId
        }));
    }
}

function handleExpertReplyFileSelect(event) {
    const file = event.target.files[0];
    if (!file) return;

    if (file.size > 10 * 1024 * 1024) {
        toastr.error("File không được vượt quá 10MB");
        return;
    }

    const previewDiv = document.getElementById('expertReplyFilePreview');
    const previewContent = document.getElementById('expertReplyPreviewContent');
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
    uploadExpertReplyFile(file);
}

// Upload file lên server
function uploadExpertReplyFile(file) {
    if (!file) {
        toastr.error("Không có file để upload");
        return;
    }

    // Show loading
    const previewContent = document.getElementById('expertReplyPreviewContent');
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
    formData.append('receiverId', currentConversationUserId);

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
            clearExpertReplyFilePreview();
        } else {
            // Lưu thông tin file vào preview
            const previewDiv = document.getElementById('expertReplyFilePreview');
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
            
            toastr.success("File đã được upload thành công. Nhấn Gửi để gửi tin nhắn.");
        }
    })
    .catch(error => {
        console.error('Upload error:', error);
        toastr.error("Lỗi khi upload file: " + error.message);
        clearExpertReplyFilePreview();
    });
}

// Clear file preview
function clearExpertReplyFilePreview() {
    const previewDiv = document.getElementById('expertReplyFilePreview');
    if (previewDiv) {
        previewDiv.style.display = 'none';
        document.getElementById('expertReplyPreviewContent').innerHTML = '';
        document.getElementById('expertReplyFileInput').value = '';
        delete previewDiv.dataset.fileUrl;
        delete previewDiv.dataset.fileName;
        delete previewDiv.dataset.fileType;
    }
}

// Load conversation với user cụ thể
async function loadConversationWithUser() {
    if (!currentConversationUserId) return;
    
    try {
        const response = await fetch(`/api/message/expert/conversation?userId=${currentConversationUserId}`, {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            const messages = await response.json();
            displayExpertMessages(messages);
        } else {
            const errorText = await response.text();
            toastr.error("Không thể tải tin nhắn: " + errorText);
        }
    } catch (error) {
        toastr.error("Lỗi kết nối: " + error.message);
    }
}

// Hiển thị tin nhắn cho expert
function displayExpertMessages(messages) {
    let messagesContainer = document.getElementById('expertReplyMessagesContainer') || document.getElementById('expertReplyMessages');
    if (!messagesContainer) {
        const conversationContent = document.getElementById('conversationContent');
        if (conversationContent) {
            const newContainer = document.createElement('div');
            newContainer.id = 'expertReplyMessagesContainer';
            newContainer.style.cssText = 'max-height: 400px; overflow-y: auto; background-color: #f8f9fa; padding: 1rem;';
            conversationContent.insertBefore(newContainer, conversationContent.firstChild);
            messagesContainer = newContainer;
        } else {
            return;
        }
    }

    messagesContainer.innerHTML = '';
    
    // Đánh dấu tất cả tin nhắn chưa đọc là đã đọc khi expert mở conversation
    const unreadMessages = messages.filter(m => 
        String(m.receiver.id) === String(currentExpertUser.id) && !m.isRead
    );
    
    if (unreadMessages.length > 0) {
        unreadMessages.forEach(message => {
            markMessageAsRead(message.id);
        });
        // Reload unread count sau khi đánh dấu
        setTimeout(() => {
            loadUnreadCount();
        }, 500);
    }
    
    if (!messages || messages.length === 0) {
        messagesContainer.innerHTML = '<div class="text-center text-muted p-3">Chưa có tin nhắn nào</div>';
        return;
    }
    
    // Lấy current user từ localStorage
    const currentUser = JSON.parse(localStorage.getItem('user'));
    const currentExpertId = currentUser ? currentUser.id : null;
    
    // Sắp xếp theo thời gian
    messages.sort((a, b) => {
        const dateA = a.createdAt ? new Date(a.createdAt) : new Date(0);
        const dateB = b.createdAt ? new Date(b.createdAt) : new Date(0);
        return dateA - dateB;
    });
    
    messages.forEach((message, index) => {
        if (!message.sender || !message.sender.id) {
            return;
        }
        
        const senderId = message.sender.id;
        const isFromExpert = String(senderId) === String(currentExpertId);
        
        const messageDiv = document.createElement('div');
        messageDiv.className = `d-flex mb-3 ${isFromExpert ? 'justify-content-end' : 'justify-content-start'}`;
        
        const senderName = isFromExpert ? 'Bạn' : (currentConversationUserName || (message.sender.fullname || message.sender.username || 'Người dùng'));
        
        // Format date đúng cách
        let createdAt = 'N/A';
        if (message.createdAt) {
            try {
                const date = new Date(message.createdAt);
                if (!isNaN(date.getTime())) {
                    // Format: "hh:mm dd/MM/yyyy"
                    const hours = String(date.getHours()).padStart(2, '0');
                    const minutes = String(date.getMinutes()).padStart(2, '0');
                    const day = String(date.getDate()).padStart(2, '0');
                    const month = String(date.getMonth() + 1).padStart(2, '0');
                    const year = date.getFullYear();
                    createdAt = `${hours}:${minutes} ${day}/${month}/${year}`;
                } else {
                    // Nếu không parse được, thử giữ nguyên format nếu đã đúng
                    if (typeof message.createdAt === 'string' && message.createdAt.includes('/')) {
                        createdAt = message.createdAt;
                    } else {
                        createdAt = 'N/A';
                    }
                }
            } catch (e) {
                createdAt = message.createdAt || 'N/A';
            }
        }
        
        // Escape HTML để tránh XSS
        const escapeHtml = (text) => {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        };
        
        // Xử lý hiển thị file/hình ảnh
        let messageBody = '';
        if (message.messageType === 'image' && message.fileUrl) {
            messageBody = `
                <img src="${message.fileUrl}" style="max-width: 300px; max-height: 300px; border-radius: 8px; cursor: pointer; display: block;" 
                     onclick="window.open('${message.fileUrl}', '_blank')" alt="${escapeHtml(message.fileName || 'Image')}">
                ${message.content ? '<p class="mt-2 mb-0">' + escapeHtml(message.content) + '</p>' : ''}
            `;
        } else if (message.messageType === 'file' && message.fileUrl) {
            messageBody = `
                <a href="${message.fileUrl}" target="_blank" class="${isFromExpert ? 'text-white' : 'text-primary'}" style="text-decoration: none; display: inline-block;">
                    <i class="bi bi-file-earmark me-2"></i>
                    <strong>${escapeHtml(message.fileName || 'File')}</strong>
                </a>
                ${message.content ? '<p class="mt-2 mb-0">' + escapeHtml(message.content) + '</p>' : ''}
            `;
        } else {
            messageBody = escapeHtml(message.content || '');
        }
        
        const messageContent = `
            <div class="message-bubble ${isFromExpert ? 'bg-primary text-white' : 'bg-light'} p-3 rounded" style="max-width: 70%;">
                <div class="small mb-1 fw-bold">${escapeHtml(senderName)}</div>
                <div>${messageBody}</div>
                <div class="small mt-1 ${isFromExpert ? 'text-white-50' : 'text-muted'}">${escapeHtml(createdAt)}</div>
                ${!isFromExpert && !message.isRead ? '<span class="badge bg-warning ms-2">Mới</span>' : ''}
            </div>
        `;
        
        messageDiv.innerHTML = messageContent;
        messagesContainer.appendChild(messageDiv);
        
        // Đánh dấu đã đọc nếu là tin nhắn từ user
        if (!isFromExpert && !message.isRead) {
            markMessageAsRead(message.id);
        }
    });
    
    // Scroll to bottom
    setTimeout(() => {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }, 100);
}

// Trả lời tin nhắn
async function replyToMessage(messageId) {
    const replyContent = prompt("Nhập nội dung trả lời:");
    if (!replyContent || !replyContent.trim()) return;
    
    try {
        const response = await fetch('/api/message/expert/reply', {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                messageId: messageId,
                content: replyContent.trim()
            })
        });
        
        if (response.ok) {
            toastr.success("Đã gửi trả lời");
            // Reload conversation
            await loadConversationWithUser();
            // Reload danh sách partners
            await loadConversationPartners();
            // Reload unread count
            await loadUnreadCount();
        } else {
            const errorText = await response.text();
            toastr.error(errorText || "Không thể gửi trả lời");
        }
    } catch (error) {
        console.error("Error replying:", error);
        toastr.error("Lỗi kết nối");
    }
}

// Đánh dấu tin nhắn đã đọc
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

// Load số tin nhắn chưa đọc
async function loadUnreadCount() {
    try {
        const response = await fetch('/api/message/expert/unread-count', {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        });
        
        if (response.ok) {
            const count = await response.json();
            localStorage.setItem('unreadMessageCount', count);
            
            // Update badge trong trang
            const badge = document.getElementById('unreadMessageBadge');
            if (badge) {
                if (count > 0) {
                    badge.textContent = count > 99 ? '99+' : count;
                    badge.style.display = 'inline';
                } else {
                    badge.style.display = 'none';
                }
            }
            
            // Update header badge
            const headerBadge = document.getElementById('headerUnreadBadge');
            if (headerBadge) {
                if (count > 0) {
                    headerBadge.textContent = count > 99 ? '99+' : count;
                    headerBadge.style.display = 'inline';
                } else {
                    headerBadge.style.display = 'none';
                }
            }
            
            // Trigger custom event để main.js có thể cập nhật badge
            window.dispatchEvent(new CustomEvent('unreadCountUpdated', { detail: count }));
        }
    } catch (error) {
        console.error("Error loading unread count:", error);
    }
}

// Load khi trang được tải
$(document).ready(function() {
    // Kết nối WebSocket
    connectExpertMessagesWebSocket();
    
    loadConversationPartners();
    loadUnreadCount();
    
    // Auto refresh danh sách partners và unread count (không cần reload conversation vì đã có WebSocket real-time)
    setInterval(function() {
        loadConversationPartners();
        loadUnreadCount();
        // Không cần reload conversation vì WebSocket đã real-time
    }, 30000); // 30 giây để sync danh sách partners và unread count
});

