package com.web.repository;

import com.web.dto.PlantImp;
import com.web.enums.PlantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.web.dto.response.PlantWithMedia;
import com.web.entity.Plant;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlantRepository extends JpaRepository<Plant, Long>, JpaSpecificationExecutor<Plant> {

    /**
     * Full-text search cho admin (dùng LIKE - fallback nếu chưa có FULLTEXT index)
     * Ưu tiên tìm trong name, scientificName, otherNames, genus trước
     * Sort trong SQL để tối ưu performance (sử dụng indexes)
     */
    @Query("""
            SELECT p FROM Plant p
            WHERE 
                (:q IS NULL OR 
                LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.scientificName) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.otherNames) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.genus) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.partsUsed) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.botanicalCharacteristics) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.chemicalComposition) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.distribution) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.ecology) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.medicinalUses) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.indications) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.contraindications) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.dosage) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.folkRemedies) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.sideEffects) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.stem) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.leaf) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.flower) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.fruitOrSeed) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.root) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.source) LIKE LOWER(CONCAT('%', :q, '%')))
            AND (:familiesId IS NULL OR p.families.id = :familiesId)
            AND (:plantStatus IS NULL OR p.plantStatus = :plantStatus)
            """)
    Page<Plant> searchByAdmin(
            @Param("q") String q,
            @Param("familiesId") Long familiesId,
            @Param("plantStatus") PlantStatus plantStatus,
            Pageable pageable
    );

    /**
     * Full-text search cho admin (dùng MySQL FULLTEXT với MATCH() AGAINST())
     * Yêu cầu: Phải chạy script database-fulltext-indexes.sql trước
     * Performance: Nhanh hơn nhiều so với LIKE, đặc biệt với dữ liệu lớn
     * 
     * Ưu tiên tìm trong name, scientific_name trước, sau đó mới tìm trong description
     * Sử dụng BOOLEAN MODE với + để bắt buộc từ khóa phải xuất hiện
     * 
     * Lưu ý: Nếu chưa có FULLTEXT index, query này sẽ lỗi. 
     * Fallback về searchByAdmin() nếu cần.
     */
    @Query(value = """
            SELECT p.*,
                   (
                     (MATCH(p.name, p.scientific_name, p.other_names, p.genus, p.parts_used) AGAINST(:q IN BOOLEAN MODE)) * 3
                     + (MATCH(p.description, p.botanical_characteristics, p.chemical_composition, p.distribution, p.ecology, p.medicinal_uses, p.indications, p.contraindications, p.dosage, p.folk_remedies, p.side_effects, p.source) AGAINST(:q IN BOOLEAN MODE)) * 1
                     + (MATCH(p.stem, p.leaf, p.flower, p.fruit_or_seed, p.root) AGAINST(:q IN BOOLEAN MODE)) * 1
                   ) AS score
            FROM plants p
            WHERE 
                (:q IS NULL OR :q = '' OR
                MATCH(p.name, p.scientific_name, p.other_names, p.genus, p.parts_used) 
                    AGAINST(:q IN BOOLEAN MODE)
                OR (CHAR_LENGTH(:q) > 3 AND (
                    MATCH(p.description, p.botanical_characteristics, p.chemical_composition, p.distribution, p.ecology, p.medicinal_uses, p.indications, p.contraindications, p.dosage, p.folk_remedies, p.side_effects, p.source) 
                        AGAINST(:q IN BOOLEAN MODE)
                    OR MATCH(p.stem, p.leaf, p.flower, p.fruit_or_seed, p.root) 
                        AGAINST(:q IN BOOLEAN MODE)
                )))
            AND (:familiesId IS NULL OR p.families_id = :familiesId)
            AND (:plantStatus IS NULL OR p.plant_status = :plantStatus)
            ORDER BY score DESC, p.created_at DESC
            """, 
            countQuery = """
            SELECT COUNT(*) FROM plants p
            WHERE 
                (:q IS NULL OR :q = '' OR
                MATCH(p.name, p.scientific_name, p.other_names, p.genus, p.parts_used) 
                    AGAINST(:q IN NATURAL LANGUAGE MODE)
                OR (CHAR_LENGTH(:q) > 3 AND (
                    MATCH(p.description, p.botanical_characteristics, p.chemical_composition, p.distribution, p.ecology, p.medicinal_uses, p.indications, p.contraindications, p.dosage, p.folk_remedies, p.side_effects, p.source) 
                        AGAINST(:q IN NATURAL LANGUAGE MODE)
                    OR MATCH(p.stem, p.leaf, p.flower, p.fruit_or_seed, p.root) 
                        AGAINST(:q IN NATURAL LANGUAGE MODE)
                )))
            AND (:familiesId IS NULL OR p.families_id = :familiesId)
            AND (:plantStatus IS NULL OR p.plant_status = :plantStatus)
            """, 
            nativeQuery = true)
    Page<Plant> searchByAdminFullText(
            @Param("q") String q,
            @Param("familiesId") Long familiesId,
            @Param("plantStatus") String plantStatus,
            Pageable pageable
    );

    /**
     * Full-text search cho export (dùng LIKE - fallback)
     */
    @Query("""
            SELECT p FROM Plant p
            WHERE 
                (:q IS NULL OR 
                LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.scientificName) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.otherNames) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.genus) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.partsUsed) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.botanicalCharacteristics) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.chemicalComposition) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.distribution) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.ecology) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.medicinalUses) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.indications) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.contraindications) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.dosage) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.folkRemedies) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.sideEffects) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.stem) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.leaf) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.flower) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.fruitOrSeed) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.root) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.source) LIKE LOWER(CONCAT('%', :q, '%')))
            AND (:familiesId IS NULL OR p.families.id = :familiesId)
            AND (:plantStatus IS NULL OR p.plantStatus = :plantStatus)
            """)
    List<Plant> searchForExport(
            @Param("q") String q,
            @Param("familiesId") Long familiesId,
            @Param("plantStatus") PlantStatus plantStatus
    );

    /**
     * Full-text search cho export (dùng MySQL FULLTEXT)
     */
    @Query(value = """
            SELECT p.*,
                   (
                     (MATCH(p.name, p.scientific_name, p.other_names, p.genus, p.parts_used) AGAINST(:q IN BOOLEAN MODE)) * 3
                     + (MATCH(p.description, p.botanical_characteristics, p.chemical_composition, p.distribution, p.ecology, p.medicinal_uses, p.indications, p.contraindications, p.dosage, p.folk_remedies, p.side_effects, p.source) AGAINST(:q IN BOOLEAN MODE)) * 1
                     + (MATCH(p.stem, p.leaf, p.flower, p.fruit_or_seed, p.root) AGAINST(:q IN BOOLEAN MODE)) * 1
                   ) AS score
            FROM plants p
            WHERE 
                (:q IS NULL OR :q = '' OR
                MATCH(p.name, p.scientific_name, p.other_names, p.genus, p.parts_used) 
                    AGAINST(:q IN BOOLEAN MODE)
                OR (CHAR_LENGTH(:q) > 3 AND (
                    MATCH(p.description, p.botanical_characteristics, p.chemical_composition, p.distribution, p.ecology, p.medicinal_uses, p.indications, p.contraindications, p.dosage, p.folk_remedies, p.side_effects, p.source) 
                        AGAINST(:q IN BOOLEAN MODE)
                    OR MATCH(p.stem, p.leaf, p.flower, p.fruit_or_seed, p.root) 
                        AGAINST(:q IN BOOLEAN MODE)
                )))
            AND (:familiesId IS NULL OR p.families_id = :familiesId)
            AND (:plantStatus IS NULL OR p.plant_status = :plantStatus)
            ORDER BY score DESC, p.created_at DESC
            """, nativeQuery = true)
    List<Plant> searchForExportFullText(
            @Param("q") String q,
            @Param("familiesId") Long familiesId,
            @Param("plantStatus") String plantStatus
    );

    @Query(value = """
            SELECT p.*,
                   (
                     (MATCH(p.name, p.scientific_name, p.other_names, p.genus, p.parts_used) AGAINST(:q IN BOOLEAN MODE)) * 3
                     + (MATCH(p.description, p.botanical_characteristics, p.chemical_composition, p.distribution, p.ecology, p.medicinal_uses, p.indications, p.contraindications, p.dosage, p.folk_remedies, p.side_effects, p.source) AGAINST(:q IN BOOLEAN MODE)) * 1
                     + (MATCH(p.stem, p.leaf, p.flower, p.fruit_or_seed, p.root) AGAINST(:q IN BOOLEAN MODE)) * 1
                   ) AS score
            FROM plants p
            WHERE 
                (:q IS NULL OR :q = '' OR
                MATCH(p.name, p.scientific_name, p.other_names, p.genus, p.parts_used) 
                    AGAINST(:q IN BOOLEAN MODE)
                OR (CHAR_LENGTH(:q) > 3 AND (
                    MATCH(p.description, p.botanical_characteristics, p.chemical_composition, p.distribution, p.ecology, p.medicinal_uses, p.indications, p.contraindications, p.dosage, p.folk_remedies, p.side_effects, p.source) 
                        AGAINST(:q IN BOOLEAN MODE)
                    OR MATCH(p.stem, p.leaf, p.flower, p.fruit_or_seed, p.root) 
                        AGAINST(:q IN BOOLEAN MODE)
                )))
            AND (:familiesId IS NULL OR p.families_id = :familiesId)
            AND p.plant_status = 'DA_XUAT_BAN'
            ORDER BY score DESC, p.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM plants p
            WHERE 
                (:q IS NULL OR :q = '' OR
                MATCH(p.name, p.scientific_name, p.other_names, p.genus, p.parts_used) 
                    AGAINST(:q IN NATURAL LANGUAGE MODE)
                OR (CHAR_LENGTH(:q) > 3 AND (
                    MATCH(p.description, p.botanical_characteristics, p.chemical_composition, p.distribution, p.ecology, p.medicinal_uses, p.indications, p.contraindications, p.dosage, p.folk_remedies, p.side_effects, p.source) 
                        AGAINST(:q IN NATURAL LANGUAGE MODE)
                    OR MATCH(p.stem, p.leaf, p.flower, p.fruit_or_seed, p.root) 
                        AGAINST(:q IN NATURAL LANGUAGE MODE)
                )))
            AND (:familiesId IS NULL OR p.families_id = :familiesId)
            AND p.plant_status = 'DA_XUAT_BAN'
            """,
            nativeQuery = true)
    Page<Plant> findAllPublic(
            @Param("q") String q,
            @Param("familiesId") Long familiesId,
            Pageable pageable
    );

    /**
     * Tìm kiếm theo tên cây hoặc tên khoa học (cho public access)
     */
    @Query(value = """
            SELECT p.*, 
                CASE 
                    WHEN :nameSearch IS NOT NULL AND :nameSearch != '' AND 
                         LOWER(p.name) LIKE LOWER(CONCAT(:nameSearch, '%')) THEN 100
                    WHEN :nameSearch IS NOT NULL AND :nameSearch != '' AND 
                         LOWER(p.scientific_name) LIKE LOWER(CONCAT(:nameSearch, '%')) THEN 90
                    WHEN :nameSearch IS NOT NULL AND :nameSearch != '' AND 
                         LOWER(p.name) LIKE LOWER(CONCAT('%', :nameSearch, '%')) THEN 50
                    WHEN :nameSearch IS NOT NULL AND :nameSearch != '' AND 
                         LOWER(p.scientific_name) LIKE LOWER(CONCAT('%', :nameSearch, '%')) THEN 40
                    ELSE 0
                END AS score
            FROM plants p
            WHERE 
                (:nameSearch IS NULL OR :nameSearch = '' OR
                LOWER(p.name) LIKE LOWER(CONCAT('%', :nameSearch, '%')) OR
                LOWER(p.scientific_name) LIKE LOWER(CONCAT('%', :nameSearch, '%')))
            AND (:familiesId IS NULL OR p.families_id = :familiesId)
            AND p.plant_status = 'DA_XUAT_BAN'
            ORDER BY score DESC, p.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM plants p
            WHERE 
                (:nameSearch IS NULL OR :nameSearch = '' OR
                LOWER(p.name) LIKE LOWER(CONCAT('%', :nameSearch, '%')) OR
                LOWER(p.scientific_name) LIKE LOWER(CONCAT('%', :nameSearch, '%')))
            AND (:familiesId IS NULL OR p.families_id = :familiesId)
            AND p.plant_status = 'DA_XUAT_BAN'
            """,
            nativeQuery = true)
    Page<Plant> findAllPublicByName(
            @Param("nameSearch") String nameSearch,
            @Param("familiesId") Long familiesId,
            Pageable pageable
    );

    @Query(value = "select p.* from plants p where p.featured = 1 limit 20", nativeQuery = true)
    List<Plant> cayNoiBat();

    @Query("select p from Plant p where p.slug = ?1")
    Optional<Plant> findBySlug(String slug);

    // Tìm cây dược liệu theo slug và chỉ trả về nếu đã xuất bản (cho public access)
    @Query("select p from Plant p where p.slug = ?1 and p.plantStatus = com.web.enums.PlantStatus.DA_XUAT_BAN")
    Optional<Plant> findBySlugAndPublished(String slug);

    @Query("select p.id as id, p.name as name from Plant p order by p.name asc ")
    List<PlantImp> findAllName();

    /**
     * Tìm cây trùng lặp theo tên hoặc tên khoa học (không phân biệt hoa thường)
     * @param name Tên cây
     * @param scientificName Tên khoa học
     * @param excludeId ID cây cần loại trừ (khi update, không check chính nó)
     * @return List Plant nếu tìm thấy trùng (có thể có nhiều cây trùng)
     */
    @Query("""
            SELECT p FROM Plant p
            WHERE (
                (:name IS NOT NULL AND :name != '' AND LOWER(TRIM(p.name)) = LOWER(TRIM(:name)))
                OR (:scientificName IS NOT NULL AND :scientificName != '' AND LOWER(TRIM(p.scientificName)) = LOWER(TRIM(:scientificName)))
            )
            AND (:excludeId IS NULL OR p.id != :excludeId)
            ORDER BY p.id ASC
            """)
    List<Plant> findDuplicatePlants(
            @Param("name") String name,
            @Param("scientificName") String scientificName,
            @Param("excludeId") Long excludeId
    );

    // Atomic update view count
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Plant p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    // Lấy top viewed plants (đã duyệt)
    @Query("SELECT p FROM Plant p WHERE p.plantStatus = :status ORDER BY p.viewCount DESC")
    List<Plant> findTopViewed(@Param("status") PlantStatus status, Pageable pageable);

    // ============================================
    // Methods cho Semantic Search (Chat AI)
    // ============================================

    /**
     * Tìm cây dược liệu theo tên (cho Chat AI semantic search)
     */
    List<Plant> findByNameContainingIgnoreCase(String name);

    /**
     * Tìm cây dược liệu theo tên khoa học (cho Chat AI semantic search)
     */
    List<Plant> findByScientificNameContainingIgnoreCase(String scientificName);

    /**
     * Tìm cây dược liệu theo tên khác (cho Chat AI semantic search)
     */
    @Query("SELECT p FROM Plant p WHERE LOWER(p.otherNames) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Plant> findByOtherNamesContainingIgnoreCase(@Param("keyword") String keyword);

    /**
     * Tìm cây dược liệu theo công dụng (cho Chat AI semantic search)
     */
    @Query("SELECT p FROM Plant p WHERE LOWER(p.medicinalUses) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Plant> findByMedicinalUsesContainingIgnoreCase(@Param("keyword") String keyword);

    /**
     * Tìm cây dược liệu theo chỉ định (cho Chat AI semantic search)
     */
    @Query("SELECT p FROM Plant p WHERE LOWER(p.indications) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Plant> findByIndicationsContainingIgnoreCase(@Param("keyword") String keyword);

    /**
     * FULLTEXT search cho Chat AI - Tìm các cây liên quan đến keyword
     * Sử dụng FULLTEXT index để tìm nhanh hơn
     * Chỉ lấy các cây đã xuất bản (DA_XUAT_BAN)
     */
    @Query(value = """
            SELECT p.* FROM plants p
            WHERE MATCH(p.name, p.scientific_name, p.other_names, p.medicinal_uses, p.indications) 
            AGAINST(:keyword IN BOOLEAN MODE)
            AND p.plant_status = 'DA_XUAT_BAN'
            LIMIT 15
            """, nativeQuery = true)
    List<Plant> findRelevantPlantsFullText(@Param("keyword") String keyword);

    @Query("select count(p) from Plant p where p.plantStatus = ?1")
    Long countByPlantStatus(com.web.enums.PlantStatus status);

    @Query("select count(p) from Plant p where p.createdAt >= ?1")
    Long countByCreatedAtAfter(java.time.LocalDateTime date);
    @Query("SELECT COUNT(DISTINCT p.genus) FROM Plant p WHERE p.genus IS NOT NULL AND TRIM(p.genus) != ''")
    Long countDistinctGenus();
}

