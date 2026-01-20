package com.web.utils;

import com.web.utils.PlantImageSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/up")
@RequiredArgsConstructor
public class PlantImageController {

    private final PlantImageSyncService plantImageSyncService;

    @GetMapping
    public String uploadAllFromFolder() {
        plantImageSyncService.uploadAndSyncPlantImages();
        return "Đã upload toàn bộ ảnh trong thư mục lên Cloudinary và cập nhật Plant.image!";
    }
}