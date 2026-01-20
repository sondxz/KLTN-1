package com.web.api;

import com.web.dto.CommentDto;
import com.web.entity.Comment;
import com.web.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comments")
@CrossOrigin
public class CommentApi {

    @Autowired
    private CommentService commentService;

    /**
     * Lấy danh sách comment của một article (có phân trang)
     */
    @GetMapping("/public/by-article")
    public ResponseEntity<Page<Comment>> getCommentsByArticle(
            @RequestParam Long articleId,
            Pageable pageable) {
        Page<Comment> comments = commentService.getCommentsByArticle(articleId, pageable);
        return ResponseEntity.ok(comments);
    }

    /**
     * Lấy danh sách comment của một article kèm replies (dạng tree, không phân trang)
     */
    @GetMapping("/public/by-article-tree")
    public ResponseEntity<List<CommentDto>> getCommentsWithReplies(
            @RequestParam Long articleId) {
        List<CommentDto> comments = commentService.getCommentsWithReplies(articleId);
        return ResponseEntity.ok(comments);
    }

    /**
     * Tạo comment mới hoặc reply (yêu cầu đăng nhập)
     */
    @PostMapping("/user/create")
    public ResponseEntity<?> createComment(@RequestBody Map<String, Object> request) {
        try {
            Long articleId = Long.valueOf(request.get("articleId").toString());
            String content = request.get("content").toString();
            Long parentId = null;
            
            if (request.get("parentId") != null) {
                parentId = Long.valueOf(request.get("parentId").toString());
            }

            CommentDto comment = commentService.createComment(articleId, content, parentId);
            return ResponseEntity.status(HttpStatus.CREATED).body(comment);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Xóa comment (yêu cầu đăng nhập)
     */
    @DeleteMapping("/user/delete")
    public ResponseEntity<?> deleteComment(@RequestParam Long id) {
        try {
            commentService.deleteComment(id);
            return ResponseEntity.ok(Map.of("message", "Xóa bình luận thành công"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ========== ADMIN ENDPOINTS ==========

    /**
     * Admin: Lấy tất cả comments với filter
     */
    @GetMapping("/admin/all")
    public ResponseEntity<Page<Comment>> getAllCommentsForAdmin(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long articleId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer status,
            Pageable pageable) {
        Page<Comment> comments = commentService.getAllCommentsForAdmin(q, articleId, userId, status, pageable);
        return ResponseEntity.ok(comments);
    }

    /**
     * Admin: Duyệt comment
     */
    @PostMapping("/admin/approve")
    public ResponseEntity<?> approveComment(@RequestParam Long id) {
        try {
            commentService.approveComment(id);
            return ResponseEntity.ok(Map.of("message", "Duyệt bình luận thành công"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }


    /**
     * Admin: Xóa comment
     */
    @DeleteMapping("/admin/delete")
    public ResponseEntity<?> deleteCommentByAdmin(@RequestParam Long id) {
        try {
            commentService.deleteCommentByAdmin(id);
            return ResponseEntity.ok(Map.of("message", "Xóa bình luận thành công"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}

