package com.web.api;

import com.web.dto.ResearchRequest;
import com.web.entity.Research;
import com.web.enums.ResearchStatus;
import com.web.service.ResearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/research")
public class ResearchApi {

    @Autowired
    private ResearchService researchService;

    @GetMapping("/admin/all")
    public Page<Research> getAll(
            Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ResearchStatus status
    ) {
        return researchService.getAll(q, status, pageable);
    }

    @GetMapping("/public/all")
    public Page<Research> getAllPublic(
            Pageable pageable,
            @RequestParam(required = false) String field,
            @RequestParam(required = false) Integer publishedYear,
            @RequestParam(required = false) String q
    ) {
        return researchService.getAllPublic(q,field, publishedYear, pageable);
    }

    @GetMapping("/public/find-by-id")
    public Research findById(@RequestParam Long id) {
        return researchService.findById(id);
    }

    @PostMapping("/admin/save")
    public Research save(@RequestBody ResearchRequest request) {
        return researchService.save(request);
    }

    @DeleteMapping("/admin/delete")
    public void delete(@RequestParam Long id) {
        researchService.delete(id);
    }

    @GetMapping("/public/research-status")
    public List<Map<String, String>> getAllStatuses() {
        List<Map<String, String>> list = new ArrayList<>();
        for (ResearchStatus status : ResearchStatus.values()) {
            Map<String, String> item = new HashMap<>();
            item.put("name", status.name());
            item.put("label", status.getLabel());
            list.add(item);
        }
        return list;
    }

    @GetMapping("/admin/export")
    public void exportResearch(
            HttpServletResponse response,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ResearchStatus status
    ) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"research_export.csv\"");
        researchService.writeResearchToCsv(response.getWriter(), q, status);
    }
}
