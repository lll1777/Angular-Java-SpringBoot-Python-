package com.gov.serviceplatform.repository;

import com.gov.serviceplatform.entity.CooperationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CooperationRecordRepository extends JpaRepository<CooperationRecord, Long> {
    
    List<CooperationRecord> findByTicketId(Long ticketId);
    
    List<CooperationRecord> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    
    List<CooperationRecord> findByCoDepartmentId(Long coDepartmentId);
    
    List<CooperationRecord> findByStatus(String status);
    
    List<CooperationRecord> findByDueTimeBeforeAndStatus(LocalDateTime dueTime, String status);
    
    long countByCoDepartmentIdAndStatus(Long coDepartmentId, String status);
}
