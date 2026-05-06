package com.web.repository;

import com.web.entity.FolkRemedy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolkRemedyRepository extends JpaRepository<FolkRemedy, Long> {

    /**
     * Tìm bài thuốc theo slug
     */
    Optional<FolkRemedy> findBySlug(String slug);

    /**
     * Kiểm tra slug tồn tại
     */
    boolean existsBySlug(String slug);

    /**
     * Đếm số lượng theo status
     */
    Long countByStatus(String status);

    /**
     * Tìm bài thuốc đã approved — public access
     */
    @Query(value = """
            SELECT fr FROM FolkRemedy fr
            WHERE fr.status = 'approved'
              AND (:search IS NULL OR :search = '' OR
                   LOWER(fr.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(fr.description) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<FolkRemedy> findAllPublic(@Param("search") String search, Pageable pageable);

    /**
     * Admin: Tìm tất cả bài thuốc có lọc theo status
     */
    @Query(value = """
            SELECT fr FROM FolkRemedy fr
            WHERE (:status IS NULL OR fr.status = :status)
              AND (:search IS NULL OR :search = '' OR
                   LOWER(fr.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(fr.description) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<FolkRemedy> findAllByAdmin(@Param("search") String search, @Param("status") String status, Pageable pageable);

    /**
     * Tìm bài thuốc liên quan đến một cây dược liệu (đã approved)
     */
    @Query("""
            SELECT DISTINCT fr FROM FolkRemedy fr
            JOIN fr.plants p
            WHERE p.id = :plantId AND fr.status = 'approved'
            ORDER BY fr.createdAt DESC
            """)
    List<FolkRemedy> findByPlantId(@Param("plantId") Long plantId);

    /**
     * Tìm bài thuốc liên quan đến bệnh (đã approved)
     */
    @Query("""
            SELECT DISTINCT fr FROM FolkRemedy fr
            JOIN fr.diseases d
            WHERE d.id = :diseaseId AND fr.status = 'approved'
            ORDER BY fr.createdAt DESC
            """)
    List<FolkRemedy> findByDiseaseId(@Param("diseaseId") Long diseaseId);

    /**
     * Lấy tất cả bài thuốc đã approved (cho RAG indexing)
     */
    @Query("SELECT fr FROM FolkRemedy fr WHERE fr.status = 'approved'")
    List<FolkRemedy> findAllApproved();

    /**
     * Đếm theo trạng thái
     */

}
