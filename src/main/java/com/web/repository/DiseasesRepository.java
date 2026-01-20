package com.web.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.web.entity.Diseases;

@Repository
public interface DiseasesRepository extends JpaRepository<Diseases, Long>, JpaSpecificationExecutor<Diseases> {

    boolean existsByName(String name);

    boolean existsBySlug(String slug);

    @Query("select d from Diseases d where d.name like %?1%")
    Page<Diseases> findAllByParam(String search, Pageable pageable);

    @Query("select d from Diseases d where (:q is null or lower(d.name) like lower(concat('%', :q, '%')))")
    java.util.List<Diseases> findAllForExport(String q);
}
