package com.web.repository;

import com.web.enums.ResearchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.web.entity.*;

import java.util.Optional;

@Repository
public interface ResearchRepository extends JpaRepository<Research, Long>, JpaSpecificationExecutor<Research> {
    Boolean existsByTitle(String title);

    @Query("""
            SELECT a FROM Research a
            WHERE (:search IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', :search, '%')) 
                   OR LOWER(a.authors) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR a.researchStatus = :status)
            """)
    Page<Research> findAllByParam(String search, ResearchStatus status, Pageable pageable);

    @Query(value = """
            SELECT r.* FROM research r
            WHERE (:search IS NULL OR :search = '' OR
                   MATCH(r.title, r.authors) AGAINST(:search IN NATURAL LANGUAGE MODE))
              AND (:field IS NULL OR r.field = :field)
              AND (:publishedYear IS NULL OR r.published_year = :publishedYear)
              -- 2 tương ứng với ResearchStatus.DA_XUAT_BAN (enum ordinal)
              AND r.research_status = 2
            ORDER BY r.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM research r
            WHERE (:search IS NULL OR :search = '' OR
                   MATCH(r.title, r.authors) AGAINST(:search IN NATURAL LANGUAGE MODE))
              AND (:field IS NULL OR r.field = :field)
              AND (:publishedYear IS NULL OR r.published_year = :publishedYear)
              -- 2 tương ứng với ResearchStatus.DA_XUAT_BAN (enum ordinal)
              AND r.research_status = 2
            """,
            nativeQuery = true)
    Page<Research> findAllPublicByParam(@Param("search") String search, @Param("field") String field, @Param("publishedYear") Integer publishedYear, Pageable pageable);

    boolean existsBySlug(String slug);

    @Query("select r from Research r where r.slug = ?1")
    Optional<Research> findBySlug(String slug);

    // Tìm nghiên cứu theo slug và chỉ trả về nếu đã xuất bản (cho public access)
    @Query("select r from Research r where r.slug = ?1 and r.researchStatus = com.web.enums.ResearchStatus.DA_XUAT_BAN")
    Optional<Research> findBySlugAndPublished(String slug);

    @Query(value = """
            SELECT r.* FROM research r
            WHERE (:search IS NULL OR :search = '' OR
                   MATCH(r.title, r.authors) AGAINST(:search IN NATURAL LANGUAGE MODE))
              AND (:status IS NULL OR r.research_status = :status)
            ORDER BY r.created_at DESC
            """, nativeQuery = true)
    java.util.List<Research> findAllForExport(@Param("search") String search, @Param("status") String status);

    @Query("""
            SELECT DISTINCT r FROM Research r
            INNER JOIN r.researchExperts re
            WHERE re.expert.id = :expertId
            AND r.researchStatus = com.web.enums.ResearchStatus.DA_XUAT_BAN
            ORDER BY r.publishedYear DESC, r.createdAt DESC
            """)
    java.util.List<Research> findByExpertId(@Param("expertId") Long expertId);

    @Query("select count(r) from Research r where r.createdAt >= ?1")
    Long countByCreatedAtAfter(java.time.LocalDateTime date);
}
