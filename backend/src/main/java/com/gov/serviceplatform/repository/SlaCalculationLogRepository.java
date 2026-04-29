package com.gov.serviceplatform.repository;

import com.gov.serviceplatform.entity.SlaCalculationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SlaCalculationLogRepository extends JpaRepository<SlaCalculationLog, Long> {
    
    List<SlaCalculationLog> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
    
    List<SlaCalculationLog> findByTicketNumberOrderByCreatedAtDesc(String ticketNumber);
}
