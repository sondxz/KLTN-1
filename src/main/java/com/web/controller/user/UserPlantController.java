package com.web.controller.user;

import com.web.entity.FolkRemedy;
import com.web.entity.Plant;
import com.web.service.FolkRemedyService;
import com.web.service.PlantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpSession;
import java.util.List;

@Controller
public class UserPlantController {

    @Autowired
    private PlantService plantService;

    @Autowired
    private FolkRemedyService folkRemedyService;

    @RequestMapping(value = {"/plant-detail/{slug}"}, method = RequestMethod.GET)
    public String plantDetail(Model model, @PathVariable String slug, HttpSession session) {
        // Tìm plant không giới hạn status (cho phép admin/expert xem cây CHỜ DUYỆT từ trang pending-approval)
        Plant plant = plantService.findBySlugPublic(slug);
        if(plant == null){
            plant = plantService.findBySlug(slug); // fallback: tìm tất cả status
        }
        if(plant == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "plant not found");
        }
        
        // Tăng view count với chống spam (bắt exception để không làm gián đoạn response)
        try {
            plantService.incrementViewCount(plant.getId(), session);
            // Reload plant để lấy viewCount mới nhất
            plant = plantService.findBySlug(slug);
        } catch (Exception e) {
            // Log lỗi nhưng không làm gián đoạn việc hiển thị cây dược liệu
            // Logger sẽ được log trong PlantService
            // Giữ nguyên plant đã load
        }
        
        model.addAttribute("plant", plant);

        // Thêm danh sách bài thuốc dân gian liên quan
        try {
            List<FolkRemedy> relatedRemedies = folkRemedyService.findByPlantId(plant.getId());
            model.addAttribute("relatedRemedies", relatedRemedies);
        } catch (Exception e) {
            // Không làm gián đoạn nếu lỗi
            model.addAttribute("relatedRemedies", java.util.Collections.emptyList());
        }

        return "user/plant-detail.html";
    }
}
