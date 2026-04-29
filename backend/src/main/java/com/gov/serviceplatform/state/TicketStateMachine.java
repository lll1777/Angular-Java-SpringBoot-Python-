package com.gov.serviceplatform.state;

import com.gov.serviceplatform.entity.CooperationRecord;
import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.entity.User;
import com.gov.serviceplatform.enums.TicketStatus;

import java.util.List;

public interface TicketStateMachine {
    
    boolean canTransition(Ticket ticket, TicketStatus targetStatus);
    
    Ticket transition(Ticket ticket, TicketStatus targetStatus, User operator, String remark);
    
    Ticket assign(Ticket ticket, User operator);
    
    Ticket autoAssign(Ticket ticket);
    
    Ticket accept(Ticket ticket, User operator);
    
    Ticket startProcessing(Ticket ticket, User operator, String content);
    
    Ticket transfer(Ticket ticket, User operator, Long targetDepartmentId, String reason);
    
    Ticket cooperate(Ticket ticket, User operator, Long[] coDepartmentIds, String requirement);
    
    CooperationRecord acceptCooperation(Long cooperationId, User operator);
    
    CooperationRecord completeCooperation(Long cooperationId, User operator, String response);
    
    Ticket returnToPrevious(Ticket ticket, User operator, String reason);
    
    Ticket escalate(Ticket ticket, User operator, String reason);
    
    Ticket autoEscalate(Ticket ticket);
    
    Ticket handleReturnAndReassign(Ticket ticket, User operator, String returnReason);
    
    Ticket submitForReview(Ticket ticket, User operator, String result);
    
    Ticket complete(Ticket ticket, User operator, String completionContent);
    
    Ticket startVisit(Ticket ticket, User operator);
    
    Ticket close(Ticket ticket, User operator, Integer satisfaction, String comment);
    
    Ticket cancel(Ticket ticket, User operator, String reason);
    
    List<CooperationRecord> getCooperationRecords(Ticket ticket);
    
    void handleClaimTimeout(Ticket ticket);
}
