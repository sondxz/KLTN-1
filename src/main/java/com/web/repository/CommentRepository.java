package com.web.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.web.entity.*;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByArticle(Article article, Pageable pageable);

    Page<Comment> findByUser(User user, Pageable pageable);

    Page<Comment> findByArticleAndParentIsNull(Article article, Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE c.article = :article AND c.parent IS NULL AND c.status = 1")
    Page<Comment> findByArticleAndParentIsNullAndApproved(@Param("article") Article article, Pageable pageable);

    List<Comment> findByParent(Comment parent);

    @Query("SELECT c FROM Comment c WHERE c.parent = :parent AND c.status = 1")
    List<Comment> findByParentAndApproved(@Param("parent") Comment parent);

    Page<Comment> findByStatus(Integer status, Pageable pageable);

    Page<Comment> findByContentContainingIgnoreCase(String content, Pageable pageable);
}
