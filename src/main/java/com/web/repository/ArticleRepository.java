package com.web.repository;

import com.web.enums.ArticleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.web.entity.*;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {

    boolean existsByTitle(String title);

    boolean existsBySlug(String slug);

    // Tìm kiếm + lọc theo trạng thái (nếu truyền)
    @Query(value = """
            SELECT a.* FROM articles a
            WHERE (:search IS NULL OR :search = '' OR
                   MATCH(a.title, a.excerpt) AGAINST(:search IN NATURAL LANGUAGE MODE))
              AND (:status IS NULL OR a.article_status = :status)
            ORDER BY a.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM articles a
            WHERE (:search IS NULL OR :search = '' OR
                   MATCH(a.title, a.excerpt) AGAINST(:search IN NATURAL LANGUAGE MODE))
              AND (:status IS NULL OR a.article_status = :status)
            """,
            nativeQuery = true)
    Page<Article> findAllByParam(@Param("search") String search, @Param("status") String status, Pageable pageable);

    @Query(value = """
            SELECT a.* FROM articles a
            WHERE (:search IS NULL OR :search = '' OR
                   MATCH(a.title, a.excerpt) AGAINST(:search IN NATURAL LANGUAGE MODE))
              AND (:diseasesId IS NULL OR a.diseases_id = :diseasesId)
              AND a.article_status = 'DA_XUAT_BAN'
            ORDER BY a.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM articles a
            WHERE (:search IS NULL OR :search = '' OR
                   MATCH(a.title, a.excerpt) AGAINST(:search IN NATURAL LANGUAGE MODE))
              AND (:diseasesId IS NULL OR a.diseases_id = :diseasesId)
              AND a.article_status = 'DA_XUAT_BAN'
            """,
            nativeQuery = true)
    Page<Article> findAllByParam(@Param("search") String search, @Param("diseasesId") Long diseasesId, Pageable pageable);

    @Query("select a from Article a where a.slug = ?1")
    Optional<Article> findBySlug(String slug);

    @Query("select a from Article a where a.slug = ?1 and a.articleStatus = com.web.enums.ArticleStatus.DA_XUAT_BAN")
    Optional<Article> findBySlugAndPublished(String slug);

    @Query(value = """
            SELECT a.* FROM articles a
            WHERE (:search IS NULL OR :search = '' OR
                   MATCH(a.title, a.excerpt) AGAINST(:search IN NATURAL LANGUAGE MODE))
              AND (:status IS NULL OR a.article_status = :status)
            ORDER BY a.created_at DESC
            """, nativeQuery = true)
    List<Article> findAllForExport(@Param("search") String search, @Param("status") String status);

    // Atomic update view count
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Article a SET a.viewCount = a.viewCount + 1 WHERE a.id = :id")
    void incrementViewCount(@Param("id") Long id);

    // Lấy top viewed articles (đã xuất bản)
    @Query("SELECT a FROM Article a WHERE a.articleStatus = :status ORDER BY a.viewCount DESC")
    List<Article> findTopViewed(@Param("status") ArticleStatus status, Pageable pageable);

    @Query("select count(a) from Article a where a.articleStatus = ?1")
    Long countByStatus(ArticleStatus status);

    @Query("select count(a) from Article a where a.createdAt >= ?1")
    Long countByCreatedAtAfter(java.time.LocalDateTime date);
}
