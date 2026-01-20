package com.web.api;

import com.web.entity.Article;
import com.web.entity.Diseases;
import com.web.entity.Expert;
import com.web.entity.Research;
import com.web.enums.ArticleStatus;
import com.web.service.DiseasesService;
import com.web.service.ExpertService;
import com.web.service.ResearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/expert")
public class ExpertApi {

    @Autowired
    private ExpertService expertService;

    @Autowired
    private ResearchService researchService;

    @GetMapping("/admin/all")
    public Page<Expert> getAll(Pageable pageable,@RequestParam(required = false) String q) {
        return expertService.getAll(q, pageable);
    }


    @GetMapping("/public/all")
    public Page<Expert> getAllPublic(Pageable pageable,@RequestParam(required = false) String q,@RequestParam(required = false) String specialization) {
        // Hiển thị tất cả Expert (bao gồm cả Expert cũ chưa có user account)
        return expertService.getAllPublic(q, specialization, pageable);
    }

    @GetMapping("/public/find-by-id")
    public ResponseEntity<Expert> findById(@RequestParam Long id) {
        return ResponseEntity.ok(expertService.findById(id));
    }

    @PostMapping("/admin/create")
    public ResponseEntity<Expert> save(@RequestBody java.util.Map<String, Object> request) {
        // Parse Expert từ request
        Expert expert = new Expert();
        expert.setName((String) request.get("name"));
        expert.setSlug((String) request.get("slug"));
        expert.setTitle((String) request.get("title"));
        expert.setEmail((String) request.get("email"));
        expert.setContactEmail((String) request.get("contactEmail"));
        expert.setPhone((String) request.get("phone"));
        expert.setSpecialization((String) request.get("specialization"));
        expert.setInstitution((String) request.get("institution"));
        expert.setAvatar((String) request.get("avatar"));
        expert.setEducation((String) request.get("education"));
        expert.setBio((String) request.get("bio"));
        expert.setAchievements((String) request.get("achievements"));
        
        // Lấy password từ request
        String password = (String) request.get("password");
        
        // Nếu có ID thì update, không thì create
        if (request.get("id") != null) {
            Long id = Long.valueOf(request.get("id").toString());
            Expert existingExpert = expertService.findById(id);
            return ResponseEntity.ok(expertService.update(existingExpert.getId(), expert, password));
        } else {
            return ResponseEntity.ok(expertService.create(expert, password));
        }
    }

    @DeleteMapping("/admin/delete")
    public ResponseEntity<String> delete(@RequestParam Long id) {
        expertService.delete(id);
        return ResponseEntity.ok("Xóa thành công");
    }

    @GetMapping("/public/research-by-expert")
    public ResponseEntity<java.util.List<Research>> getResearchByExpert(@RequestParam Long expertId) {
        java.util.List<Research> researchList = researchService.findByExpertId(expertId);
        return ResponseEntity.ok(researchList);
    }

}
