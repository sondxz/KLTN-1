package com.web.repository;

import com.web.entity.PlantDiseases;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PlantDiseasesRepository extends JpaRepository<PlantDiseases, Long> {

    @Modifying
    @Transactional
    @Query("delete from PlantDiseases p where p.plant.id = ?1")
    int deleteByPlant(Long plantId);

    @Query("""
            select pd from PlantDiseases pd
            join fetch pd.plant p
            join fetch pd.diseases d
            where lower(d.name) like lower(concat('%', :keyword, '%'))
              and p.plantStatus = com.web.enums.PlantStatus.DA_XUAT_BAN
            order by p.name asc
            """)
    List<PlantDiseases> findPublishedPlantsByDiseaseKeyword(@Param("keyword") String keyword);

    @Query("""
            select pd from PlantDiseases pd
            join fetch pd.plant p
            join fetch pd.diseases d
            where lower(p.name) like lower(concat('%', :keyword, '%'))
              and p.plantStatus = com.web.enums.PlantStatus.DA_XUAT_BAN
            order by d.name asc
            """)
    List<PlantDiseases> findPublishedDiseasesByPlantKeyword(@Param("keyword") String keyword);
}
