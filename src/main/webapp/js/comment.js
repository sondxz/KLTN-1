var token = localStorage.getItem("token");

/**
 * Lấy articleId từ URL hoặc từ biến global
 */
function getArticleId() {
    // Thử lấy từ URL nếu có dạng /article-detail/{id} hoặc ?id=...
    const urlParams = new URLSearchParams(window.location.search);
    const idFromUrl = urlParams.get('id');
    if (idFromUrl) {
        return idFromUrl;
    }
    
    // Hoặc lấy từ biến global nếu được set
    if (typeof articleId !== 'undefined') {
        return articleId;
    }
    
    // Hoặc lấy từ data attribute trong HTML
    const articleElement = document.querySelector('[data-article-id]');
    if (articleElement) {
        return articleElement.getAttribute('data-article-id');
    }
    
    return null;
}

/**
 * Load tất cả comments của article
 */
async function loadComments() {
    const articleId = getArticleId();
    if (!articleId) {
        console.error("Không tìm thấy articleId");
        return;
    }

    try {
        const response = await fetch(`/api/comments/public/by-article-tree?articleId=${articleId}`);
        if (!response.ok) {
            throw new Error("Lỗi khi tải bình luận");
        }
        
        const comments = await response.json();
        renderComments(comments);
        updateCommentCount(comments);
    } catch (error) {
        console.error("Error loading comments:", error);
        document.getElementById("comments-list").innerHTML = 
            '<div class="alert alert-warning">Không thể tải bình luận. Vui lòng thử lại sau.</div>';
    }
}

/**
 * Render comments ra HTML (dạng tree với replies)
 */
function renderComments(comments) {
    const commentsList = document.getElementById("comments-list");
    if (!commentsList) return;

    if (!comments || comments.length === 0) {
        commentsList.innerHTML = '<p class="text-muted text-center py-4">Chưa có bình luận nào. Hãy là người đầu tiên bình luận!</p>';
        return;
    }

    let html = '';
    comments.forEach(comment => {
        html += renderCommentItem(comment, 0);
    });
    
    commentsList.innerHTML = html;
}

/**
 * Render một comment item (có thể có replies)
 */
function renderCommentItem(comment, depth) {
    const marginLeft = depth * 30;
    const isReply = depth > 0;
    const canDelete = token && (comment.userId == getCurrentUserId() || isAdmin());
    
    let html = `
        <div class="comment-item mb-3 ${isReply ? 'comment-reply' : ''}" style="margin-left: ${marginLeft}px;" data-comment-id="${comment.id}">
            <div class="card ${isReply ? 'border-start border-3' : ''}">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start mb-2">
                        <div>
                            <strong class="text-primary">${escapeHtml(comment.userName || 'Người dùng')}</strong>
                            <small class="text-muted ms-2">${comment.createdAt || ''}</small>
                        </div>
                        ${canDelete ? `
                            <button class="btn btn-sm btn-outline-danger" onclick="deleteComment(${comment.id})" title="Xóa">
                                <i class="bi bi-trash"></i>
                            </button>
                        ` : ''}
                    </div>
                    <p class="mb-2">${escapeHtml(comment.content)}</p>
                    ${token ? `
                        <button class="btn btn-sm btn-link text-primary p-0" onclick="showReplyForm(${comment.id})">
                            <i class="bi bi-reply"></i> Trả lời
                        </button>
                    ` : ''}
                    <div id="reply-form-${comment.id}" style="display: none;" class="mt-3">
                        <textarea class="form-control mb-2" id="reply-content-${comment.id}" rows="2" placeholder="Viết bình luận..."></textarea>
                        <div class="d-flex gap-2">
                            <button class="btn btn-primary btn-sm" onclick="postReply(${comment.id})">Gửi</button>
                            <button class="btn btn-secondary btn-sm" onclick="cancelReply(${comment.id})">Hủy</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;

    // Render replies nếu có
    if (comment.replies && comment.replies.length > 0) {
        comment.replies.forEach(reply => {
            html += renderCommentItem(reply, depth + 1);
        });
    }

    return html;
}

/**
 * Hiển thị form reply
 */
function showReplyForm(commentId) {
    const replyForm = document.getElementById(`reply-form-${commentId}`);
    if (replyForm) {
        replyForm.style.display = replyForm.style.display === 'none' ? 'block' : 'none';
    }
}

/**
 * Ẩn form reply
 */
function cancelReply(commentId) {
    const replyForm = document.getElementById(`reply-form-${commentId}`);
    const textarea = document.getElementById(`reply-content-${commentId}`);
    if (replyForm) {
        replyForm.style.display = 'none';
    }
    if (textarea) {
        textarea.value = '';
    }
}

/**
 * Post một reply
 */
async function postReply(parentId) {
    const content = document.getElementById(`reply-content-${parentId}`).value.trim();
    
    if (!content) {
        alert("Vui lòng nhập nội dung bình luận");
        return;
    }

    if (!token) {
        alert("Bạn cần đăng nhập để bình luận");
        window.location.href = "/login";
        return;
    }

    await postCommentWithContent(content, parentId);
}

/**
 * Post một comment mới (không phải reply) - được gọi từ button
 */
async function postComment() {
    const content = document.getElementById("comment-content").value.trim();
    await postCommentWithContent(content, null);
}

/**
 * Post một comment mới hoặc reply (internal function)
 */
async function postCommentWithContent(content, parentId = null) {
    const articleId = getArticleId();
    if (!articleId) {
        alert("Không tìm thấy bài viết");
        return;
    }

    if (!content || content.trim() === '') {
        alert("Vui lòng nhập nội dung bình luận");
        return;
    }

    if (!token) {
        alert("Bạn cần đăng nhập để bình luận");
        window.location.href = "/login";
        return;
    }

    try {
        const requestBody = {
            articleId: articleId,
            content: content.trim()
        };
        
        if (parentId) {
            requestBody.parentId = parentId;
        }

        const response = await fetch('/api/comments/user/create', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + token
            },
            body: JSON.stringify(requestBody)
        });

        const result = await response.json();

        if (response.ok) {
            // Xóa nội dung form
            if (parentId) {
                cancelReply(parentId);
            } else {
                document.getElementById("comment-content").value = '';
            }
            
            // Reload comments
            loadComments();
            
            // Hiển thị thông báo thành công
            if (typeof toastr !== 'undefined') {
                toastr.success("Bình luận đã được gửi thành công!");
            } else {
                alert("Bình luận đã được gửi thành công!");
            }
        } else {
            const errorMsg = result.error || "Có lỗi xảy ra khi gửi bình luận";
            if (typeof toastr !== 'undefined') {
                toastr.error(errorMsg);
            } else {
                alert(errorMsg);
            }
        }
    } catch (error) {
        console.error("Error posting comment:", error);
        alert("Có lỗi xảy ra. Vui lòng thử lại sau.");
    }
}

/**
 * Xóa comment
 */
async function deleteComment(commentId) {
    if (!confirm("Bạn có chắc chắn muốn xóa bình luận này?")) {
        return;
    }

    if (!token) {
        alert("Bạn cần đăng nhập");
        return;
    }

    try {
        const response = await fetch(`/api/comments/user/delete?id=${commentId}`, {
            method: 'DELETE',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        });

        const result = await response.json();

        if (response.ok) {
            loadComments();
            if (typeof toastr !== 'undefined') {
                toastr.success("Xóa bình luận thành công!");
            } else {
                alert("Xóa bình luận thành công!");
            }
        } else {
            const errorMsg = result.error || "Có lỗi xảy ra khi xóa bình luận";
            if (typeof toastr !== 'undefined') {
                toastr.error(errorMsg);
            } else {
                alert(errorMsg);
            }
        }
    } catch (error) {
        console.error("Error deleting comment:", error);
        alert("Có lỗi xảy ra. Vui lòng thử lại sau.");
    }
}

/**
 * Lấy user ID hiện tại từ token (nếu có)
 */
function getCurrentUserId() {
    // Có thể decode JWT token để lấy userId
    // Hoặc lưu trong localStorage khi login
    const userId = localStorage.getItem("userId");
    return userId ? parseInt(userId) : null;
}

/**
 * Kiểm tra xem user có phải admin không
 */
function isAdmin() {
    // Có thể check từ token hoặc localStorage
    const userRole = localStorage.getItem("userRole");
    return userRole === "ROLE_ADMIN";
}

/**
 * Cập nhật số lượng comment
 */
function updateCommentCount(comments) {
    const commentCountEl = document.getElementById("comment-count");
    if (commentCountEl) {
        let totalCount = 0;
        if (comments && comments.length > 0) {
            comments.forEach(comment => {
                totalCount++; // Đếm comment cha
                if (comment.replies && comment.replies.length > 0) {
                    totalCount += comment.replies.length; // Đếm replies
                }
            });
        }
        commentCountEl.textContent = totalCount;
    }
}

/**
 * Escape HTML để tránh XSS
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

