package com.gov.serviceplatform.repository;

import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.entity.TicketCooperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketCooperationRepository extends JpaRepository<TicketCooperation, Long> {
    
    List<TicketCooperation> findByTicketId(Long ticketId);
    
    List<TicketCooperation> findByTicketIdOrderBySortOrderAsc(Long ticketId);
    
    List<TicketCooperation> findByCooperationDepartmentId(Long departmentId);
    
    List<TicketCooperation> findByCooperationDepartmentIdAndStatus(Long departmentId, TicketCooperation.CooperationStatus status);
    
    List<TicketCooperation> findByStatus(TicketCooperation.CooperationStatus status);
    
    @Query("SELECT c FROM TicketCooperation c WHERE c.dueTime <= :now AND c.status IN :statuses")
    List<TicketCooperation> findOverdueCooperations(
        @Param("now") LocalDateTime now,
        @Param("statuses") List<TicketCooperation.CooperationStatus> statuses);
    
    Optional<TicketCooperation> findFirstByTicketIdOrderByCreatedAtDesc(Long ticketId);
    
    long countByCooperationDepartmentIdAndStatus(Long departmentId, TicketCooperation.CooperationStatus status);
    
    @Query("SELECT c FROM TicketCooperation c WHERE c.ticket = :ticket AND c.status IN :statuses")
    List<TicketCooperation> findActiveCooperationsForTicket(
        @Param("ticket") Ticket ticket,
        @Param("statuses") List<TicketCooperation.CooperationStatus> statuses);
}
