package com.web.repository;

import com.web.entity.ChunkEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ChunkEmbeddingRepository extends JpaRepository<ChunkEmbedding, Long> {

    /**
     * Tìm chunk bằng Full-Text Search trên chunk_text (BOOLEAN MODE — kiểm soát tốt hơn).
     * BOOLEAN MODE yêu cầu từ khóa phải xuất hiện, tránh partial match quá thoáng.
     * Trả về top N kết quả có điểm FTS cao nhất.
     */
    @Query(value = """
            SELECT ce.*, 
                   MATCH(ce.chunk_text) AGAINST(:query IN BOOLEAN MODE) AS fts_score
            FROM chunk_embeddings ce
            WHERE MATCH(ce.chunk_text) AGAINST(:query IN BOOLEAN MODE)
            ORDER BY fts_score DESC
            LIMIT :limitCount
            """, nativeQuery = true)
    List<ChunkEmbedding> findByFullTextSearchBoolean(@Param("query") String query, @Param("limitCount") int limitCount);

    /**
     * Tìm chunk bằng Full-Text Search (NATURAL LANGUAGE MODE — fallback).
     * Dùng khi BOOLEAN MODE không trả về kết quả nào.
     */
    @Query(value = """
            SELECT ce.*, 
                   MATCH(ce.chunk_text) AGAINST(:query IN NATURAL LANGUAGE MODE) AS fts_score
            FROM chunk_embeddings ce
            WHERE MATCH(ce.chunk_text) AGAINST(:query IN NATURAL LANGUAGE MODE)
            ORDER BY fts_score DESC
            LIMIT :limitCount
            """, nativeQuery = true)
    List<ChunkEmbedding> findByFullTextSearch(@Param("query") String query, @Param("limitCount") int limitCount);

    /**
     * Tìm chunk theo entityName chính xác (case-insensitive).
     * Dùng cho Entity Verification — kiểm tra xem thực thể có trong DB không.
     */
    @Query("SELECT ce FROM ChunkEmbedding ce WHERE LOWER(ce.entityName) = LOWER(:name)")
    List<ChunkEmbedding> findByEntityNameIgnoreCase(@Param("name") String name);

    /**
     * Tìm chunk theo entityName chứa từ khóa (LIKE).
     * Dùng để tìm gợi ý cây tương tự khi không có exact match.
     */
    @Query("SELECT ce FROM ChunkEmbedding ce WHERE LOWER(ce.entityName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<ChunkEmbedding> findByEntityNameContainingIgnoreCase(@Param("name") String name);

    /**
     * Tìm chunk bằng FTS với filter theo content_type
     */
    @Query(value = """
            SELECT ce.*, 
                   MATCH(ce.chunk_text) AGAINST(:query IN NATURAL LANGUAGE MODE) AS fts_score
            FROM chunk_embeddings ce
            WHERE MATCH(ce.chunk_text) AGAINST(:query IN NATURAL LANGUAGE MODE)
              AND ce.content_type = :contentType
            ORDER BY fts_score DESC
            LIMIT :limitCount
            """, nativeQuery = true)
    List<ChunkEmbedding> findByFullTextSearchAndType(
            @Param("query") String query,
            @Param("contentType") String contentType,
            @Param("limitCount") int limitCount);

    /**
     * Lấy danh sách chunk có embedding theo trang (để tránh OOM khi có quá nhiều chunk)
     */
    @Query(value = """
            SELECT ce.* FROM chunk_embeddings ce
            WHERE ce.embedding IS NOT NULL 
              AND JSON_LENGTH(ce.embedding) > 0
            """, 
            countQuery = """
            SELECT COUNT(*) FROM chunk_embeddings ce
            WHERE ce.embedding IS NOT NULL 
              AND JSON_LENGTH(ce.embedding) > 0
            """,
            nativeQuery = true)
    org.springframework.data.domain.Page<ChunkEmbedding> findAllWithEmbedding(org.springframework.data.domain.Pageable pageable);

    /**
     * Lấy chunk theo entity
     */
    List<ChunkEmbedding> findByContentTypeAndEntityId(ChunkEmbedding.ContentType contentType, Long entityId);

    /**
     * Xóa chunk theo entity (khi re-index)
     */
    @Modifying
    @Transactional
    void deleteByContentTypeAndEntityId(ChunkEmbedding.ContentType contentType, Long entityId);

    /**
     * Xóa tất cả chunk theo content_type (khi re-index toàn bộ)
     */
    @Modifying
    @Transactional
    void deleteByContentType(ChunkEmbedding.ContentType contentType);

    /**
     * Đếm số chunk theo content_type
     */
    long countByContentType(ChunkEmbedding.ContentType contentType);

    /**
     * Đếm tổng chunk có embedding
     */
    @Query("SELECT COUNT(ce) FROM ChunkEmbedding ce WHERE ce.embedding IS NOT NULL")
    long countWithEmbedding();
}
