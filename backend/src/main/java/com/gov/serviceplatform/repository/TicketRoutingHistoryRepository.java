package com.gov.serviceplatform.repository;

import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.entity.TicketRoutingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRoutingHistoryRepository extends JpaRepository<TicketRoutingHistory, Long> {
    
    List<TicketRoutingHistory> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    
    List<TicketRoutingHistory> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
    
    List<TicketRoutingHistory> findByTicketIdAndIsReturnedFalseOrderByCreatedAtDesc(Long ticketId);
    
    @Query("SELECT r FROM TicketRoutingHistory r WHERE r.ticket.id = :ticketId AND r.isReturned = false ORDER BY r.routingLevel DESC")
    List<TicketRoutingHistory> findLastRoutingLevels(@Param("ticketId") Long ticketId);
    
    Optional<TicketRoutingHistory> findFirstByTicketIdOrderByRoutingLevelDesc(Long ticketId);
    
    Optional<TicketRoutingHistory> findFirstByTicketIdAndIsReturnedFalseOrderByRoutingLevelDesc(Long ticketId);
    
    @Query("SELECT MAX(r.routingLevel) FROM TicketRoutingHistory r WHERE r.ticket.id = :ticketId")
    Integer findMaxRoutingLevel(@Param("ticketId") Long ticketId);
    
    long countByTicketId(Long ticketId);
}
