package com.web.api;

import com.web.dto.PlantImp;
import com.web.dto.PlantSearch;
import com.web.dto.request.PlantRequestDto;
import com.web.entity.Plant;
import com.web.enums.PlantStatus;
import com.web.service.PlantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plant")
public class PlantApi {

    private static final Logger logger = LoggerFactory.getLogger(PlantApi.class);

    @Autowired
    private PlantService plantService;

    @GetMapping("/admin/all")
    public Page<Plant> getAll(
            Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long familiesId,
            @RequestParam(required = false) PlantStatus plantStatus
    ) {
        return plantService.getAllByAdmin(pageable, q, familiesId,plantStatus);
    }

    @PostMapping("/public/all")
    public Page<Plant> getAll(Pageable pageable,@RequestBody PlantSearch plantSearch) {
        return plantService.getAllByPublic(pageable, plantSearch);
    }

    @GetMapping("/admin/all-name")
    public List<PlantImp> getAllName() {
        return plantService.findAllName();
    }

    @GetMapping("/public/all-list")
    public List<PlantImp> getAllList() {
        return plantService.findAllName();
    }

    @PostMapping("/admin/create")
    public ResponseEntity<?> create(@RequestBody PlantRequestDto dto) {
        try {
            if (dto.getPlant() != null && dto.getPlant().getId() != null) {
                return ResponseEntity.status(400).body("Sử dụng PUT /admin/update để cập nhật cây dược liệu");
            }
            return ResponseEntity.ok(plantService.saveOrUpdate(dto));
        } catch (com.web.exception.MessageException e) {
            String errorMessage = e.getDefaultMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = e.getMessage() != null ? e.getMessage() : "Đã xảy ra lỗi không xác định";
            }
            return ResponseEntity.status(400).body(errorMessage);
        } catch (Exception e) {
            logger.error("Error creating plant", e);
            return ResponseEntity.status(500).body("Đã xảy ra lỗi không xác định. Vui lòng thử lại sau.");
        }
    }

    @PutMapping("/admin/update")
    public ResponseEntity<?> update(@RequestBody PlantRequestDto dto) {
        try {
            if (dto.getPlant() == null || dto.getPlant().getId() == null) {
                return ResponseEntity.status(400).body("ID cây dược liệu là bắt buộc để cập nhật");
            }
            return ResponseEntity.ok(plantService.saveOrUpdate(dto));
        } catch (com.web.exception.MessageException e) {
            String errorMessage = e.getDefaultMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = e.getMessage() != null ? e.getMessage() : "Đã xảy ra lỗi không xác định";
            }
            return ResponseEntity.status(400).body(errorMessage);
        } catch (Exception e) {
            logger.error("Error updating plant", e);
            return ResponseEntity.status(500).body("Đã xảy ra lỗi không xác định. Vui lòng thử lại sau.");
        }
    }

    @PostMapping("/user/create")
    public ResponseEntity<?> createByUser(@RequestBody PlantRequestDto dto) {
        try {
            return ResponseEntity.ok(plantService.saveOrUpdate(dto));
        } catch (com.web.exception.MessageException e) {
            String errorMessage = e.getDefaultMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = e.getMessage() != null ? e.getMessage() : "Đã xảy ra lỗi không xác định";
            }
            return ResponseEntity.status(400).body(errorMessage);
        } catch (Exception e) {
            logger.error("Error creating plant by user", e);
            return ResponseEntity.status(500).body("Đã xảy ra lỗi không xác định. Vui lòng thử lại sau.");
        }
    }

    @GetMapping("/public/all-status")
    public List<Map<String, String>> getAllStatuses() {
        List<Map<String, String>> list = new ArrayList<>();
        for (PlantStatus status : PlantStatus.values()) {
            Map<String, String> item = new HashMap<>();
            item.put("name", status.name());
            item.put("label", status.getLabel());
            list.add(item);
        }
        return list;
    }

    @DeleteMapping("/admin/delete")
    public ResponseEntity<String> delete(@RequestParam Long id) {
        plantService.delete(id);
        return ResponseEntity.ok("Xóa thành công");
    }

    @DeleteMapping("/admin/delete-image")
    public ResponseEntity<String> deleteImage(@RequestParam Long id) {
        plantService.deleteImage(id);
        return ResponseEntity.ok("Xóa thành công");
    }

    @GetMapping("/public/find-by-id")
    public ResponseEntity<?> findById(@RequestParam Long id) {
        Plant result = plantService.findById(id);
        return ResponseEntity.ok(result);
    }

    /**
     * Kiểm tra cây dược liệu có trùng lặp không (cho frontend check trước khi submit)
     * @param name Tên cây
     * @param scientificName Tên khoa học
     * @param excludeId ID cây cần loại trừ (khi check trong danh sách pending, loại trừ chính nó)
     * @return Map chứa thông tin: isDuplicate (boolean), duplicatePlant (Plant nếu trùng, null nếu không)
     */
    @GetMapping("/public/check-duplicate")
    public ResponseEntity<?> checkDuplicate(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String scientificName,
            @RequestParam(required = false) Long excludeId
    ) {
        Plant duplicatePlant = plantService.checkDuplicate(name, scientificName, excludeId);
        Map<String, Object> result = new HashMap<>();
        result.put("isDuplicate", duplicatePlant != null);
        result.put("duplicatePlant", duplicatePlant);
        if (duplicatePlant != null) {
            result.put("message", String.format(
                "Cây dược liệu '%s' đã tồn tại trong hệ thống!",
                duplicatePlant.getName()
            ));
        } else {
            result.put("message", "Cây dược liệu này chưa có trong hệ thống.");
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/public/cay-noi-bat-index")
    public ResponseEntity<?> cayNoiBatIndex() {
        List<Plant> result = plantService.cayNoiBatIndex();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/admin/export")
    public void exportPlants(
            HttpServletResponse response,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long familiesId,
            @RequestParam(required = false) PlantStatus plantStatus
    ) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"plants_export.csv\"");
        plantService.writePlantsToCsv(response.getWriter(), q, familiesId, plantStatus);
    }

    // ========== EXPERT APIs ==========
    @GetMapping("/expert/pending")
    public Page<Plant> getPendingPlants(
            Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long familiesId
    ) {
        return plantService.getPendingPlants(pageable, q, familiesId);
    }

    @PostMapping("/expert/approve")
    public ResponseEntity<?> approvePlant(@RequestParam Long id) {
        return ResponseEntity.ok(plantService.approveOrReject(id, PlantStatus.DA_XUAT_BAN));
    }

    @PostMapping("/expert/reject")
    public ResponseEntity<?> rejectPlant(@RequestParam Long id) {
        return ResponseEntity.ok(plantService.approveOrReject(id, PlantStatus.TU_CHOI));
    }
}
