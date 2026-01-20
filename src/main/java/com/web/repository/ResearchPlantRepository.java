package com.web.repository;

import com.web.entity.ResearchPlant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ResearchPlantRepository extends JpaRepository<ResearchPlant, Long> {

    @Modifying
    @Transactional
    @Query("delete from ResearchPlant p where p.plant.id = ?1")
    int deleteByPlant(Long plantId);

    @Modifying
    @Transactional
    @Query("delete from ResearchPlant p where p.research.id = ?1")
    int deleteByResearch(Long researchId);
}
