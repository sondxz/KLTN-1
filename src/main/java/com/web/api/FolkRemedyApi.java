package com.web.api;

import com.web.entity.FolkRemedy;
import com.web.service.FolkRemedyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folk-remedies")
public class FolkRemedyApi {

    private static final Logger logger = LoggerFactory.getLogger(FolkRemedyApi.class);

    @Autowired
    private FolkRemedyService folkRemedyService;

    // ================================================
    // PUBLIC ENDPOINTS
    // ================================================

    /**
     * Danh sách bài thuốc đã approved (có search + phân trang)
     * GET /api/folk-remedies/public/list?search=xxx&page=0&size=10
     */
    @GetMapping("/public/list")
    public ResponseEntity<Page<FolkRemedy>> listPublic(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(folkRemedyService.findAllPublic(search, pageable));
    }

    /**
     * Chi tiết bài thuốc (chỉ approved)
     * GET /api/folk-remedies/public/detail?id=1
     */
    @GetMapping("/public/detail")
    public ResponseEntity<FolkRemedy> detailPublic(@RequestParam Long id) {
        return ResponseEntity.ok(folkRemedyService.findByIdPublic(id));
    }

    /**
     * Tìm bài thuốc theo cây dược liệu
     * GET /api/folk-remedies/public/by-plant?plantId=1
     */
    @GetMapping("/public/by-plant")
    public ResponseEntity<List<FolkRemedy>> byPlant(@RequestParam Long plantId) {
        return ResponseEntity.ok(folkRemedyService.findByPlantId(plantId));
    }

    /**
     * Tìm bài thuốc theo bệnh
     * GET /api/folk-remedies/public/by-disease?diseaseId=1
     */
    @GetMapping("/public/by-disease")
    public ResponseEntity<List<FolkRemedy>> byDisease(@RequestParam Long diseaseId) {
        return ResponseEntity.ok(folkRemedyService.findByDiseaseId(diseaseId));
    }

    // ================================================
    // ADMIN ENDPOINTS
    // ================================================

    /**
     * Admin: Danh sách tất cả bài thuốc
     * GET /api/folk-remedies/admin/list?search=xxx&status=pending&page=0&size=10
     */
    @GetMapping("/admin/list")
    public ResponseEntity<Page<FolkRemedy>> listAdmin(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(folkRemedyService.findAllByAdmin(search, status, pageable));
    }

    /**
     * Admin: Chi tiết bài thuốc
     * GET /api/folk-remedies/admin/detail?id=1
     */
    @GetMapping("/admin/detail")
    public ResponseEntity<FolkRemedy> detailAdmin(@RequestParam Long id) {
        return ResponseEntity.ok(folkRemedyService.findById(id));
    }

    /**
     * Admin: Tạo mới bài thuốc
     * POST /api/folk-remedies/admin/create
     */
    @PostMapping("/admin/create")
    public ResponseEntity<FolkRemedy> create(@RequestBody Map<String, Object> payload) {
        FolkRemedy fr = new FolkRemedy();
        fr.setName((String) payload.get("name"));
        fr.setSlug((String) payload.get("slug"));
        fr.setDescription((String) payload.get("description"));
        fr.setUsageInstruction((String) payload.get("usageInstruction"));
        fr.setPreparation((String) payload.get("preparation"));
        fr.setContraindication((String) payload.get("contraindication"));
        fr.setSource((String) payload.get("source"));
        fr.setStatus(payload.get("status") != null ? (String) payload.get("status") : "pending");

        List<Long> plantIds = parseLongList(payload.get("plantIds"));
        List<Long> diseaseIds = parseLongList(payload.get("diseaseIds"));

        return ResponseEntity.ok(folkRemedyService.create(fr, plantIds, diseaseIds));
    }

    /**
     * Admin: Cập nhật bài thuốc
     * PUT /api/folk-remedies/admin/update?id=1
     */
    @PutMapping("/admin/update")
    public ResponseEntity<FolkRemedy> update(@RequestParam Long id,
                                              @RequestBody Map<String, Object> payload) {
        FolkRemedy fr = new FolkRemedy();
        fr.setName((String) payload.get("name"));
        fr.setSlug((String) payload.get("slug"));
        fr.setDescription((String) payload.get("description"));
        fr.setUsageInstruction((String) payload.get("usageInstruction"));
        fr.setPreparation((String) payload.get("preparation"));
        fr.setContraindication((String) payload.get("contraindication"));
        fr.setSource((String) payload.get("source"));
        fr.setStatus((String) payload.get("status"));

        List<Long> plantIds = parseLongList(payload.get("plantIds"));
        List<Long> diseaseIds = parseLongList(payload.get("diseaseIds"));

        return ResponseEntity.ok(folkRemedyService.update(id, fr, plantIds, diseaseIds));
    }

    /**
     * Admin: Duyệt bài thuốc
     * POST /api/folk-remedies/admin/approve?id=1
     */
    @PostMapping("/admin/approve")
    public ResponseEntity<FolkRemedy> approve(@RequestParam Long id) {
        return ResponseEntity.ok(folkRemedyService.approve(id));
    }

    /**
     * Admin: Từ chối bài thuốc
     * POST /api/folk-remedies/admin/reject?id=1
     */
    @PostMapping("/admin/reject")
    public ResponseEntity<FolkRemedy> reject(@RequestParam Long id) {
        return ResponseEntity.ok(folkRemedyService.reject(id));
    }

    /**
     * Admin: Xóa bài thuốc
     * DELETE /api/folk-remedies/admin/delete?id=1
     */
    @DeleteMapping("/admin/delete")
    public ResponseEntity<String> delete(@RequestParam Long id) {
        folkRemedyService.delete(id);
        return ResponseEntity.ok("Xóa bài thuốc thành công");
    }

    // ================================================
    // UTILITY
    // ================================================

    @SuppressWarnings("unchecked")
    private List<Long> parseLongList(Object obj) {
        if (obj == null) return null;
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<Long> result = new java.util.ArrayList<>();
            for (Object item : list) {
                if (item instanceof Number) {
                    result.add(((Number) item).longValue());
                } else if (item instanceof String) {
                    try {
                        result.add(Long.parseLong((String) item));
                    } catch (NumberFormatException e) {
                        // skip invalid
                    }
                }
            }
            return result;
        }
        return null;
    }
}
