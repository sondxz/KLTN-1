package com.web.repository;

import com.web.entity.PlantDiseases;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PlantDiseasesRepository extends JpaRepository<PlantDiseases, Long> {

    @Modifying
    @Transactional
    @Query("delete from PlantDiseases p where p.plant.id = ?1")
    int deleteByPlant(Long plantId);
}
