package com.web.repository;

import com.web.entity.ResearchExpert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

@Repository
public interface ResearchExpertRepository extends JpaRepository<ResearchExpert, Long> {
    
    @Modifying
    @Transactional
    @Query("delete from ResearchExpert re where re.research.id = ?1")
    int deleteByResearch(Long researchId);

    @Query("SELECT COUNT(re) > 0 FROM ResearchExpert re WHERE re.research.id = :researchId AND re.expert.id = :expertId")
    boolean existsByResearchAndExpert(@Param("researchId") Long researchId, @Param("expertId") Long expertId);
}

