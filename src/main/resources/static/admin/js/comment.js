var size = 10;

async function loadAllComments(page) {
    const param = document.getElementById("param").value || "";
    const status = document.getElementById("status").value || "";
    const articleId = document.getElementById("articleId").value || "";
    const userId = document.getElementById("userId").value || "";
    
    let url = `/api/comments/admin/all?page=${page}&size=${size}`;
    
    if (param) {
        url += `&q=${encodeURIComponent(param)}`;
    }
    if (status) {
        url += `&status=${status}`;
    }
    if (articleId) {
        url += `&articleId=${articleId}`;
    }
    if (userId) {
        url += `&userId=${userId}`;
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
            toastr.error("Lỗi khi tải dữ liệu bình luận");
            return;
        }
        
        const result = await response.json();
        const listComment = result.content || [];
        const totalPage = result.totalPages || 0;
        const totalElements = result.totalElements || 0;
        const numberOfElements = result.numberOfElements || 0;
        const start = totalElements === 0 ? 0 : page * size + 1;
        const end = totalElements === 0 ? 0 : page * size + numberOfElements;
        
        let main = '';
        if (listComment.length === 0) {
            main = '<tr><td colspan="7" class="text-center text-muted py-4">Không có bình luận nào</td></tr>';
        } else {
            for (let i = 0; i < listComment.length; i++) {
                const comment = listComment[i];
                const userName = comment.user?.fullname || comment.user?.username || 'Người dùng';
                const articleTitle = comment.article?.title || 'N/A';
                const statusBadge = comment.status === 1 
                    ? '<span class="badge bg-success">Đã duyệt</span>'
                    : '<span class="badge bg-warning">Chờ duyệt</span>';
                
                // Rút ngắn nội dung nếu quá dài
                let content = comment.content || '';
                if (content.length > 100) {
                    content = content.substring(0, 100) + '...';
                }
                
                main += `
                    <tr>
                        <td>${comment.id}</td>
                        <td>
                            <div style="max-width: 300px;">
                                ${escapeHtml(content)}
                                ${comment.parent ? '<small class="text-muted d-block mt-1"><i class="bi bi-reply"></i> Trả lời comment #' + comment.parent.id + '</small>' : ''}
                            </div>
                        </td>
                        <td>
                            <div>
                                <strong>${escapeHtml(userName)}</strong>
                                <small class="text-muted d-block">ID: ${comment.user?.id || 'N/A'}</small>
                            </div>
                        </td>
                        <td>
                            <div style="max-width: 200px;">
                                <strong>${escapeHtml(articleTitle)}</strong>
                                <small class="text-muted d-block">ID: ${comment.article?.id || 'N/A'}</small>
                            </div>
                        </td>
                        <td>${statusBadge}</td>
                        <td>${comment.createdAt || 'N/A'}</td>
                        <td class="text-center">
                            <div class="btn-group" role="group">
                                ${comment.status !== 1 ? `
                                    <button onclick="approveComment(${comment.id})" class="btn btn-success btn-sm" title="Duyệt">
                                        <i class="fa-solid fa-check"></i>
                                    </button>
                                ` : ''}
                                <button onclick="deleteComment(${comment.id})" class="btn btn-danger btn-sm" title="Xóa">
                                    <i class="fa-solid fa-trash"></i>
                                </button>
                            </div>
                        </td>
                    </tr>
                `;
            }
        }
        
        document.getElementById("listcomment").innerHTML = main;
        
        // Render pagination
        let mainpage = '';
        if (totalPage > 0) {
            // Previous button
            if (page > 0) {
                mainpage += `<li class="page-item"><a class="page-link" href="#" onclick="loadAllComments(${page - 1}); return false;">&laquo;</a></li>`;
            }
            
            // Page numbers
            for (let i = 0; i < totalPage; i++) {
                if (i === 0 || i === totalPage - 1 || (i >= page - 2 && i <= page + 2)) {
                    mainpage += `<li class="page-item ${i === page ? 'active' : ''}"><a class="page-link" href="#" onclick="loadAllComments(${i}); return false;">${i + 1}</a></li>`;
                } else if (i === page - 3 || i === page + 3) {
                    mainpage += `<li class="page-item disabled"><a class="page-link">...</a></li>`;
                }
            }
            
            // Next button
            if (page < totalPage - 1) {
                mainpage += `<li class="page-item"><a class="page-link" href="#" onclick="loadAllComments(${page + 1}); return false;">&raquo;</a></li>`;
            }
        }
        
        document.getElementById("pageable").innerHTML = mainpage;
        document.getElementById("numElm").innerText = `Đang hiển thị ${start} - ${end} trong ${totalElements} kết quả`;
    } catch (error) {
        console.error("Error loading comments:", error);
        toastr.error("Có lỗi xảy ra khi tải dữ liệu");
    }
}

async function approveComment(id) {
    if (!confirm("Bạn có chắc chắn muốn duyệt bình luận này?")) {
        return;
    }
    
    try {
        const response = await fetch(`/api/comments/admin/approve?id=${id}`, {
            method: 'POST',
            headers: new Headers({
                'Authorization': 'Bearer ' + token
            })
        });
        
        // Xử lý 401/403 - redirect về login nếu token hết hạn
        if (checkResponseError(response)) {
            return; // Đã redirect, không cần xử lý tiếp
        }
        
        const result = await response.json();
        
        if (response.ok) {
            toastr.success("Duyệt bình luận thành công!");
            loadAllComments(0);
        } else {
            toastr.error(result.error || "Có lỗi xảy ra");
        }
    } catch (error) {
        console.error("Error approving comment:", error);
        toastr.error("Có lỗi xảy ra khi duyệt bình luận");
    }
}

async function deleteComment(id) {
    if (!confirm("Bạn có chắc chắn muốn xóa bình luận này? Hành động này không thể hoàn tác.")) {
        return;
    }
    
    try {
        const response = await fetch(`/api/comments/admin/delete?id=${id}`, {
            method: 'DELETE',
            headers: new Headers({
                'Authorization': 'Bearer ' + token
            })
        });
        
        // Xử lý 401/403 - redirect về login nếu token hết hạn
        if (checkResponseError(response)) {
            return; // Đã redirect, không cần xử lý tiếp
        }
        
        const result = await response.json();
        
        if (response.ok) {
            toastr.success("Xóa bình luận thành công!");
            loadAllComments(0);
        } else {
            toastr.error(result.error || "Có lỗi xảy ra");
        }
    } catch (error) {
        console.error("Error deleting comment:", error);
        toastr.error("Có lỗi xảy ra khi xóa bình luận");
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

















