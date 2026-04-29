package com.gov.serviceplatform.repository;

import com.gov.serviceplatform.entity.DepartmentEfficiency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentEfficiencyRepository extends JpaRepository<DepartmentEfficiency, Long> {
    
    Optional<DepartmentEfficiency> findByDepartmentIdAndStatisticsDate(Long departmentId, LocalDate statisticsDate);
    
    List<DepartmentEfficiency> findByStatisticsDateOrderByRankAsc(LocalDate statisticsDate);
    
    @Query("SELECT d FROM DepartmentEfficiency d WHERE d.statisticsDate BETWEEN :startDate AND :endDate AND d.department.id = :deptId ORDER BY d.statisticsDate ASC")
    List<DepartmentEfficiency> findByDepartmentAndDateRange(@Param("deptId") Long deptId, 
                                                              @Param("startDate") LocalDate startDate, 
                                                              @Param("endDate") LocalDate endDate);
    
    @Query("SELECT AVG(d.efficiencyScore) FROM DepartmentEfficiency d WHERE d.department.id = :deptId AND d.statisticsDate BETWEEN :startDate AND :endDate")
    Double findAverageEfficiencyScore(@Param("deptId") Long deptId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);
}
