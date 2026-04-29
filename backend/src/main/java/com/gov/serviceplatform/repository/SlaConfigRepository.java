package com.gov.serviceplatform.repository;

import com.gov.serviceplatform.entity.Department;
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
    
    Optional<SlaConfig> findByCategoryAndSubCategoryAndDepartmentIdAndIsActiveTrue(
        String category, String subCategory, Long departmentId);
    
    Optional<SlaConfig> findByCategoryAndDepartmentIdAndIsActiveTrue(String category, Long departmentId);
    
    Optional<SlaConfig> findByDepartmentIdAndIsActiveTrue(Long departmentId);
    
    List<SlaConfig> findByCategoryAndIsActiveTrue(String category);
    
    @Query("SELECT s FROM SlaConfig s WHERE s.isActive = true AND " +
           "(s.category = :category OR :category IS NULL) AND " +
           "(s.subCategory = :subCategory OR :subCategory IS NULL) AND " +
           "(s.department = :department OR :department IS NULL) " +
           "ORDER BY s.priorityLevel DESC")
    List<SlaConfig> findMatchingConfigs(
        @Param("category") String category,
        @Param("subCategory") String subCategory,
        @Param("department") Department department);
}
