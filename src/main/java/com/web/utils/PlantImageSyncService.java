package com.web.utils;

import com.web.entity.Plant;
import com.web.repository.PlantRepository;
import com.web.utils.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class PlantImageSyncService {

    private static final Logger logger = LoggerFactory.getLogger(PlantImageSyncService.class);

    private final CloudinaryService cloudinaryService;
    private final PlantRepository plantRepository;

    private final String IMAGE_FOLDER = "D:\\code\\Quan_ly_cay_thuoc_sourcecode\\upload\\2025_06";

    public void uploadAndSyncPlantImages() {
        try {
            // B1. Lấy tất cả file trong thư mục
            File folder = new File(IMAGE_FOLDER);
            File[] files = folder.listFiles((dir, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg") || lower.endsWith(".webp");
            });

            if (files == null || files.length == 0) {
                logger.warn("No image files found in folder: {}", IMAGE_FOLDER);
                return;
            }

            List<String> uploadedUrls = Collections.synchronizedList(new ArrayList<>());
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(files.length, 8));

            for (File file : files) {
                executor.submit(() -> {
                    try {
                        String url = cloudinaryService.uploadFile(file);
                        uploadedUrls.add(url);
                    } catch (Exception e) {
                        logger.error("Error uploading file: {}", file.getName(), e);
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(15, TimeUnit.MINUTES);

            logger.info("Total images uploaded: {}", uploadedUrls.size());

            List<Plant> plants = plantRepository.findAll();
            if (plants.isEmpty()) {
                logger.warn("No plants found in database");
                return;
            }

            int imgCount = uploadedUrls.size();
            for (int i = 0; i < plants.size(); i++) {
                String imageUrl = uploadedUrls.get(i % imgCount);
                plants.get(i).setImage(imageUrl);
            }

            plantRepository.saveAll(plants);
            logger.info("Updated images for {} plants", plants.size());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Upload interrupted", e);
        } catch (Exception e) {
            logger.error("Error uploading and assigning images", e);
            throw new RuntimeException("Error uploading and assigning images", e);
        }
    }
}
