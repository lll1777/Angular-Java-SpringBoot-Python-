package com.gov.serviceplatform.repository;

import com.gov.serviceplatform.entity.SlaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlaConfigRepository extends JpaRepository<SlaConfig, Long> {
    
    List<SlaConfig> findByIsActiveTrue();
    
    Optional<SlaConfig> findByCategoryAndSubCategoryAndIsActiveTrue(String category, String subCategory);
    
    Optional<SlaConfig> findByCategoryAndIsActiveTrue(String category);
    
    @Query("SELECT s FROM SlaConfig s WHERE s.category = :category AND (s.subCategory = :subCategory OR s.subCategory IS NULL) AND s.isActive = true ORDER BY s.priority DESC")
    List<SlaConfig> findByCategoryAndSubCategoryOrderByPriority(@Param("category") String category, @Param("subCategory") String subCategory);
    
    @Query("SELECT s FROM SlaConfig s WHERE s.isUrgent = :isUrgent AND s.isActive = true")
    List<SlaConfig> findByIsUrgentAndIsActiveTrue(@Param("isUrgent") Boolean isUrgent);
}
