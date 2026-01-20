package com.web.api;

import com.web.entity.Families;
import com.web.service.FamiliesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/families")
public class FamiliesApi {

    @Autowired
    private FamiliesService familiesService;

    @GetMapping("/public/all")
    public ResponseEntity<?> getAllFamilies(Pageable pageable, @RequestParam String search) {
        return ResponseEntity.ok(familiesService.findAll(pageable, search));
    }

    @GetMapping("/public/all-list")
    public ResponseEntity<?> getAllList() {
        return ResponseEntity.ok(familiesService.findAllList());
    }


    @GetMapping("/public/find-by-id")
    public ResponseEntity<Families> getFamilyById(@RequestParam Long id) {
        return ResponseEntity.ok(familiesService.findById(id));
    }

    @PostMapping("/admin/create")
    public ResponseEntity<Families> createFamily(@RequestBody Families families) {
        return ResponseEntity.ok(familiesService.save(families));
    }

    @DeleteMapping("/admin/delete")
    public ResponseEntity<?> deleteFamily(@RequestParam Long id) {
        familiesService.delete(id);
        return ResponseEntity.ok("Đã xóa thành công family có ID = " + id);
    }

    @GetMapping("/admin/export")
    public void exportFamilies(HttpServletResponse response,
                               @RequestParam(required = false) String search) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"families_export.csv\"");
        familiesService.writeFamiliesToCsv(response.getWriter(), search);
    }
}
