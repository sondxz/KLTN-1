package com.web.service;

import com.web.dto.CommentDto;
import com.web.entity.Article;
import com.web.entity.Comment;
import com.web.entity.User;
import com.web.exception.MessageException;
import com.web.repository.ArticleRepository;
import com.web.repository.CommentRepository;
import com.web.repository.UserRepository;
import com.web.utils.UserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CommentService {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUtils userUtils;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    /**
     * Lấy tất cả comment của một article (chỉ comment cha, không có reply) - chỉ lấy đã approved
     */
    public Page<Comment> getCommentsByArticle(Long articleId, Pageable pageable) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new MessageException("Không tìm thấy bài viết"));
        return commentRepository.findByArticleAndParentIsNullAndApproved(article, pageable);
    }

    /**
     * Lấy tất cả comment của một article kèm replies (dạng tree) - chỉ lấy đã approved
     */
    public List<CommentDto> getCommentsWithReplies(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new MessageException("Không tìm thấy bài viết"));
        
        List<Comment> parentComments = commentRepository.findByArticleAndParentIsNullAndApproved(article, 
                org.springframework.data.domain.PageRequest.of(0, 100)).getContent();
        
        return parentComments.stream()
                .map(this::convertToDtoWithReplies)
                .collect(Collectors.toList());
    }

    /**
     * Tạo comment mới
     */
    @Transactional
    public CommentDto createComment(Long articleId, String content, Long parentId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new MessageException("Không tìm thấy bài viết"));

        if (!article.getAllowComments()) {
            throw new MessageException("Bài viết này không cho phép bình luận");
        }

        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            throw new MessageException("Bạn cần đăng nhập để bình luận");
        }

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setArticle(article);
        comment.setUser(currentUser);
        comment.setStatus(1); // 1 = approved, 0 = pending

        // Nếu có parentId thì đây là reply
        if (parentId != null) {
            Comment parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new MessageException("Không tìm thấy comment cha"));
            comment.setParent(parent);
        }

        Comment savedComment = commentRepository.save(comment);
        return convertToDto(savedComment);
    }

    /**
     * Xóa comment (chỉ user tạo comment mới xóa được, hoặc admin)
     * Xóa cả các comment con (replies) nếu có
     */
    @Transactional
    public void deleteComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new MessageException("Không tìm thấy bình luận"));

        User currentUser = userUtils.getUserWithAuthority();
        if (currentUser == null) {
            throw new MessageException("Bạn cần đăng nhập");
        }

        boolean isAdmin = currentUser.getAuthorities() != null && 
                         "ROLE_ADMIN".equals(currentUser.getAuthorities().getName());
        
        if (!comment.getUser().getId().equals(currentUser.getId()) && !isAdmin) {
            throw new MessageException("Bạn không có quyền xóa bình luận này");
        }

        deleteCommentRecursive(comment);
    }

    /**
     * Xóa comment và tất cả comment con (replies) một cách recursive
     * Xóa từ dưới lên (con trước, cha sau) để tránh foreign key constraint
     */
    private void deleteCommentRecursive(Comment comment) {
        List<Comment> replies = commentRepository.findByParent(comment);
        
        for (Comment reply : replies) {
            deleteCommentRecursive(reply);
        }
        
        commentRepository.delete(comment);
        commentRepository.flush();
    }

    /**
     * Admin: Lấy tất cả comments với filter
     */
    public Page<Comment> getAllCommentsForAdmin(String search, Long articleId, Long userId, Integer status, Pageable pageable) {
        if (articleId != null) {
            Article article = articleRepository.findById(articleId)
                    .orElseThrow(() -> new MessageException("Không tìm thấy bài viết"));
            Page<Comment> result = commentRepository.findByArticle(article, pageable);
            if (status != null) {
                List<Comment> filtered = result.getContent().stream()
                        .filter(c -> c.getStatus() != null && c.getStatus().equals(status))
                        .collect(Collectors.toList());
                return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
            }
            return result;
        }
        
        if (userId != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new MessageException("Không tìm thấy người dùng"));
            Page<Comment> result = commentRepository.findByUser(user, pageable);
            if (status != null) {
                List<Comment> filtered = result.getContent().stream()
                        .filter(c -> c.getStatus() != null && c.getStatus().equals(status))
                        .collect(Collectors.toList());
                return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
            }
            return result;
        }
        
        // Filter theo status
        if (status != null) {
            return commentRepository.findByStatus(status, pageable);
        }
        
        // Nếu có search, tìm theo content
        if (search != null && !search.trim().isEmpty()) {
            return commentRepository.findByContentContainingIgnoreCase(search.trim(), pageable);
        }
        
        // Lấy tất cả
        return commentRepository.findAll(pageable);
    }

    /**
     * Admin: Duyệt comment (status = 1)
     */
    @Transactional
    public void approveComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new MessageException("Không tìm thấy bình luận"));
        comment.setStatus(1); // 1 = approved
        commentRepository.save(comment);
    }

    /**
     * Admin: Xóa comment (xóa cả comment con nếu có)
     */
    @Transactional
    public void deleteCommentByAdmin(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new MessageException("Không tìm thấy bình luận"));
        deleteCommentRecursive(comment);
    }

    /**
     * Convert Comment entity to CommentDto (không có replies)
     */
    private CommentDto convertToDto(Comment comment) {
        CommentDto dto = new CommentDto();
        dto.setId(comment.getId());
        dto.setContent(comment.getContent());
        dto.setUserId(comment.getUser().getId().intValue());
        // Hiển thị fullname, nếu không có thì dùng username
        String userName = comment.getUser().getFullname();
        if (userName == null || userName.trim().isEmpty()) {
            userName = comment.getUser().getUsername();
        }
        dto.setUserName(userName);
        dto.setArticleId(comment.getArticle().getId().intValue());
        dto.setArticleTitle(comment.getArticle().getTitle());
        dto.setStatus(comment.getStatus());
        
        if (comment.getParent() != null) {
            dto.setParentId(comment.getParent().getId().intValue());
        }
        
        if (comment.getCreatedAt() != null) {
            dto.setCreatedAt(comment.getCreatedAt().format(DATE_FORMATTER));
        }
        if (comment.getUpdatedAt() != null) {
            dto.setUpdatedAt(comment.getUpdatedAt().format(DATE_FORMATTER));
        }
        
        return dto;
    }

    /**
     * Convert Comment entity to CommentDto kèm replies (dạng tree, recursive) - chỉ lấy đã approved
     */
    private CommentDto convertToDtoWithReplies(Comment comment) {
        CommentDto dto = convertToDto(comment);
        
        // Load replies (chỉ đã approved)
        List<Comment> replies = commentRepository.findByParentAndApproved(comment);
        if (replies != null && !replies.isEmpty()) {
            // Recursive: convert mỗi reply và load replies của nó
            List<CommentDto> replyDtos = replies.stream()
                    .map(this::convertToDtoWithReplies) // Recursive call
                    .collect(Collectors.toList());
            dto.setReplies(replyDtos);
        } else {
            dto.setReplies(new ArrayList<>());
        }
        
        return dto;
    }
}

