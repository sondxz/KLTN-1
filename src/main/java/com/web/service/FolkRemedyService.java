package com.web.service;

import com.web.entity.Diseases;
import com.web.entity.FolkRemedy;
import com.web.entity.Plant;
import com.web.exception.MessageException;
import com.web.repository.DiseasesRepository;
import com.web.repository.FolkRemedyRepository;
import com.web.repository.PlantRepository;
import com.web.utils.SlugGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FolkRemedyService {

    private static final Logger log = LoggerFactory.getLogger(FolkRemedyService.class);

    @Autowired
    private FolkRemedyRepository folkRemedyRepository;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private DiseasesRepository diseasesRepository;

    // ================================================
    // PUBLIC ACCESS
    // ================================================

    /**
     * Danh sách bài thuốc đã approved (có search + phân trang)
     */
    public Page<FolkRemedy> findAllPublic(String search, Pageable pageable) {
        String searchParam = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        return folkRemedyRepository.findAllPublic(searchParam, pageable);
    }

    /**
     * Chi tiết bài thuốc (chỉ approved)
     */
    public FolkRemedy findByIdPublic(Long id) {
        FolkRemedy fr = folkRemedyRepository.findById(id)
                .orElseThrow(() -> new MessageException("Không tìm thấy bài thuốc có ID = " + id));
        if (!"approved".equals(fr.getStatus())) {
            throw new MessageException("Bài thuốc chưa được duyệt");
        }
        return fr;
    }

    /**
     * Tìm bài thuốc liên quan đến cây dược liệu
     */
    public List<FolkRemedy> findByPlantId(Long plantId) {
        return folkRemedyRepository.findByPlantId(plantId);
    }

    /**
     * Tìm bài thuốc liên quan đến bệnh
     */
    public List<FolkRemedy> findByDiseaseId(Long diseaseId) {
        return folkRemedyRepository.findByDiseaseId(diseaseId);
    }

    /**
     * Lấy tất cả bài thuốc đã approved (cho RAG indexing)
     */
    public List<FolkRemedy> findAllApproved() {
        return folkRemedyRepository.findAllApproved();
    }

    // ================================================
    // ADMIN CRUD
    // ================================================

    /**
     * Admin: Danh sách tất cả bài thuốc có filter
     */
    public Page<FolkRemedy> findAllByAdmin(String search, String status, Pageable pageable) {
        String searchParam = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String statusParam = (status != null && !status.trim().isEmpty()) ? status.trim() : null;
        return folkRemedyRepository.findAllByAdmin(searchParam, statusParam, pageable);
    }

    /**
     * Admin: Tìm theo ID
     */
    public FolkRemedy findById(Long id) {
        return folkRemedyRepository.findById(id)
                .orElseThrow(() -> new MessageException("Không tìm thấy bài thuốc có ID = " + id));
    }

    /**
     * Admin: Tạo mới bài thuốc
     */
    @Transactional
    public FolkRemedy create(FolkRemedy folkRemedy, List<Long> plantIds, List<Long> diseaseIds) {
        // Validate
        if (folkRemedy.getName() == null || folkRemedy.getName().trim().isEmpty()) {
            throw new MessageException("Tên bài thuốc không được để trống");
        }

        // Tạo slug
        if (folkRemedy.getSlug() == null || folkRemedy.getSlug().isEmpty()) {
            folkRemedy.setSlug(SlugGenerator.generateSlug(folkRemedy.getName()));
        }

        // Kiểm tra slug trùng
        if (folkRemedyRepository.existsBySlug(folkRemedy.getSlug())) {
            folkRemedy.setSlug(folkRemedy.getSlug() + "-" + System.currentTimeMillis());
        }

        // Set quan hệ plants
        if (plantIds != null && !plantIds.isEmpty()) {
            List<Plant> plants = new ArrayList<>();
            for (Long pid : plantIds) {
                plantRepository.findById(pid).ifPresent(plants::add);
            }
            folkRemedy.setPlants(plants);
        }

        // Set quan hệ diseases
        if (diseaseIds != null && !diseaseIds.isEmpty()) {
            List<Diseases> diseases = new ArrayList<>();
            for (Long did : diseaseIds) {
                diseasesRepository.findById(did).ifPresent(diseases::add);
            }
            folkRemedy.setDiseases(diseases);
        }

        return folkRemedyRepository.save(folkRemedy);
    }

    /**
     * Admin: Cập nhật bài thuốc
     */
    @Transactional
    public FolkRemedy update(Long id, FolkRemedy updated, List<Long> plantIds, List<Long> diseaseIds) {
        FolkRemedy existing = findById(id);

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setUsageInstruction(updated.getUsageInstruction());
        existing.setPreparation(updated.getPreparation());
        existing.setContraindication(updated.getContraindication());
        existing.setSource(updated.getSource());

        if (updated.getStatus() != null) {
            existing.setStatus(updated.getStatus());
        }

        // Update slug nếu tên thay đổi
        if (updated.getSlug() != null && !updated.getSlug().isEmpty()) {
            existing.setSlug(updated.getSlug());
        }

        // Update quan hệ plants
        if (plantIds != null) {
            List<Plant> plants = new ArrayList<>();
            for (Long pid : plantIds) {
                plantRepository.findById(pid).ifPresent(plants::add);
            }
            existing.setPlants(plants);
        }

        // Update quan hệ diseases
        if (diseaseIds != null) {
            List<Diseases> diseases = new ArrayList<>();
            for (Long did : diseaseIds) {
                diseasesRepository.findById(did).ifPresent(diseases::add);
            }
            existing.setDiseases(diseases);
        }

        return folkRemedyRepository.save(existing);
    }

    /**
     * Admin: Duyệt bài thuốc
     */
    @Transactional
    public FolkRemedy approve(Long id) {
        FolkRemedy fr = findById(id);
        fr.setStatus("approved");
        log.info("Approved folk remedy: {} (ID={})", fr.getName(), id);
        return folkRemedyRepository.save(fr);
    }

    /**
     * Admin: Từ chối bài thuốc
     */
    @Transactional
    public FolkRemedy reject(Long id) {
        FolkRemedy fr = findById(id);
        fr.setStatus("rejected");
        log.info("Rejected folk remedy: {} (ID={})", fr.getName(), id);
        return folkRemedyRepository.save(fr);
    }

    /**
     * Admin: Xóa bài thuốc
     */
    @Transactional
    public void delete(Long id) {
        if (!folkRemedyRepository.existsById(id)) {
            throw new MessageException("Không tìm thấy bài thuốc có ID = " + id);
        }
        folkRemedyRepository.deleteById(id);
        log.info("Deleted folk remedy ID={}", id);
    }

    /**
     * Đếm số bài thuốc chờ duyệt
     */
    public long countPending() {
        return folkRemedyRepository.countByStatus("pending");
    }
}
