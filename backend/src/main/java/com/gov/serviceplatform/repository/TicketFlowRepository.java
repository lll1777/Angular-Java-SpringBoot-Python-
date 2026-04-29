package com.gov.serviceplatform.repository;

import com.gov.serviceplatform.entity.TicketFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketFlowRepository extends JpaRepository<TicketFlow, Long> {
    
    List<TicketFlow> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    
    List<TicketFlow> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
    
    List<TicketFlow> findByOperatorId(Long operatorId);
    
    List<TicketFlow> findByFromDepartmentIdOrToDepartmentId(Long fromDeptId, Long toDeptId);
}
