package com.web.api;
import com.web.entity.Diseases;
import com.web.service.DiseasesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/api/diseases")
public class DiseasesApi {

    @Autowired
    private DiseasesService diseasesService;

    @GetMapping("/public/get-all")
    public ResponseEntity<Page<Diseases>> getAll(Pageable pageable, @RequestParam(required = false) String q) {
        return ResponseEntity.ok(diseasesService.findAllByParam(q, pageable));
    }

    @GetMapping("/public/get-all-list")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(diseasesService.findAllList());
    }

    @GetMapping("/public/find-by-id")
    public ResponseEntity<Diseases> findById(@RequestParam Long id) {
        return ResponseEntity.ok(diseasesService.findById(id));
    }

    @PostMapping("/admin/create")
    public ResponseEntity<Diseases> save(@RequestBody Diseases diseases) {
        return ResponseEntity.ok(diseasesService.save(diseases));
    }

    @DeleteMapping("/admin/delete")
    public ResponseEntity<String> delete(@RequestParam Long id) {
        diseasesService.delete(id);
        return ResponseEntity.ok("Xóa thành công");
    }

    @GetMapping("/public/exists-by-name")
    public ResponseEntity<Boolean> existsByName(@RequestParam String name) {
        return ResponseEntity.ok(diseasesService.existsByName(name));
    }

    @GetMapping("/public/exists-by-slug")
    public ResponseEntity<Boolean> existsBySlug(@RequestParam String slug) {
        return ResponseEntity.ok(diseasesService.existsBySlug(slug));
    }

    @GetMapping("/admin/export")
    public void exportDiseases(HttpServletResponse response,
                               @RequestParam(required = false) String q) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"diseases_export.csv\"");
        diseasesService.writeDiseasesToCsv(response.getWriter(), q);
    }
}
