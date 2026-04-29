package com.gov.serviceplatform.repository;

import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.enums.AlertLevel;
import com.gov.serviceplatform.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    
    Ticket findByTicketNumber(String ticketNumber);
    
    Page<Ticket> findByCitizenId(Long citizenId, Pageable pageable);
    
    Page<Ticket> findByCurrentDepartmentId(Long departmentId, Pageable pageable);
    
    Page<Ticket> findByHandlerId(Long handlerId, Pageable pageable);
    
    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);
    
    Page<Ticket> findByCurrentDepartmentIdAndStatus(Long departmentId, TicketStatus status, Pageable pageable);
    
    Page<Ticket> findByAlertLevel(AlertLevel alertLevel, Pageable pageable);
    
    List<Ticket> findByAlertLevelIn(List<AlertLevel> alertLevels);
    
    @Query("SELECT t FROM Ticket t WHERE t.dueTime <= :threshold AND t.status NOT IN :completedStatuses")
    List<Ticket> findOverdueTickets(@Param("threshold") LocalDateTime threshold, 
                                      @Param("completedStatuses") List<TicketStatus> completedStatuses);
    
    @Query("SELECT t FROM Ticket t WHERE t.yellowWarningTime <= :now AND t.status NOT IN :completedStatuses AND t.alertLevel = :normal")
    List<Ticket> findTicketsNeedingYellowWarning(@Param("now") LocalDateTime now,
                                                   @Param("completedStatuses") List<TicketStatus> completedStatuses,
                                                   @Param("normal") AlertLevel normal);
    
    @Query("SELECT t FROM Ticket t WHERE t.redWarningTime <= :now AND t.status NOT IN :completedStatuses AND t.alertLevel IN :levels")
    List<Ticket> findTicketsNeedingRedWarning(@Param("now") LocalDateTime now,
                                                @Param("completedStatuses") List<TicketStatus> completedStatuses,
                                                @Param("levels") List<AlertLevel> levels);
    
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.currentDepartment.id = :deptId AND t.status = :status")
    long countByDepartmentAndStatus(@Param("deptId") Long deptId, @Param("status") TicketStatus status);
    
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.currentDepartment.id = :deptId AND t.alertLevel = :level")
    long countByDepartmentAndAlertLevel(@Param("deptId") Long deptId, @Param("level") AlertLevel level);
    
    @Query("SELECT AVG(TIMESTAMPDIFF(HOUR, t.createdAt, t.completedAt)) FROM Ticket t WHERE t.currentDepartment.id = :deptId AND t.completedAt IS NOT NULL")
    Double findAverageProcessingHoursByDepartment(@Param("deptId") Long deptId);
    
    @Query("SELECT AVG(t.satisfactionScore) FROM Ticket t WHERE t.currentDepartment.id = :deptId AND t.satisfactionScore IS NOT NULL")
    Double findAverageSatisfactionByDepartment(@Param("deptId") Long deptId);
    
    @Query("SELECT t FROM Ticket t WHERE t.createdAt BETWEEN :start AND :end")
    List<Ticket> findByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
