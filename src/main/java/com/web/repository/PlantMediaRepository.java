package com.web.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.web.entity.PlantMedia;

import java.util.List;

@Repository
public interface PlantMediaRepository extends JpaRepository<PlantMedia, Long> {
    
    List<PlantMedia> findByPlantId(Long plantId);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM PlantMedia pm WHERE pm.plant.id = :plantId")
    void deleteByPlantId(@Param("plantId") Long plantId);
}
