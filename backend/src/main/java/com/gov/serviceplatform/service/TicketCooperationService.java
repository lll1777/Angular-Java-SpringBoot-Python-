package com.gov.serviceplatform.service;

import com.gov.serviceplatform.entity.Department;
import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.entity.TicketCooperation;
import com.gov.serviceplatform.entity.User;
import com.gov.serviceplatform.enums.TicketStatus;
import com.gov.serviceplatform.repository.DepartmentRepository;
import com.gov.serviceplatform.repository.TicketCooperationRepository;
import com.gov.serviceplatform.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketCooperationService {

    private final TicketCooperationRepository cooperationRepository;
    private final TicketRepository ticketRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;
    private final WorkCalendarService workCalendarService;
    private final SlaService slaService;

    private static final int DEFAULT_COOPERATION_HOURS = 24;
    private static final List<TicketCooperation.CooperationStatus> ACTIVE_STATUSES = Arrays.asList(
        TicketCooperation.CooperationStatus.PENDING,
        TicketCooperation.CooperationStatus.ACCEPTED,
        TicketCooperation.CooperationStatus.PROCESSING
    );

    @Transactional
    public TicketCooperation createCooperation(Ticket ticket, User initiator, 
                                                 Long cooperationDeptId, String requirement,
                                                 Integer processingHours) {
        if (ticket.getStatus() != TicketStatus.IN_PROGRESS && 
            ticket.getStatus() != TicketStatus.COOPERATING) {
            throw new IllegalArgumentException("只有办理中或协办中的工单才能发起协办");
        }
        
        Department cooperationDept = departmentRepository.findById(cooperationDeptId)
            .orElseThrow(() -> new IllegalArgumentException("协办部门不存在"));
        
        Department initiatorDept = ticket.getCurrentDepartment();
        
        List<TicketCooperation> existingCooperations = cooperationRepository
            .findActiveCooperationsForTicket(ticket, ACTIVE_STATUSES);
        int sortOrder = existingCooperations.size() + 1;
        
        TicketCooperation cooperation = new TicketCooperation();
        cooperation.setTicket(ticket);
        cooperation.setInitiatorDepartment(initiatorDept);
        cooperation.setCooperationDepartment(cooperationDept);
        cooperation.setInitiator(initiator);
        cooperation.setStatus(TicketCooperation.CooperationStatus.PENDING);
        cooperation.setRequirement(requirement);
        cooperation.setSortOrder(sortOrder);
        
        int hours = processingHours != null ? processingHours : DEFAULT_COOPERATION_HOURS;
        cooperation.setProcessingHours(hours);
        
        LocalDateTime dueTime = workCalendarService.calculateDueTime(LocalDateTime.now(), hours, true);
        cooperation.setDueTime(dueTime);
        
        cooperationRepository.save(cooperation);
        
        if (ticket.getStatus() != TicketStatus.COOPERATING) {
            ticket.setStatus(TicketStatus.COOPERATING);
            ticketRepository.save(ticket);
        }
        
        log.info("创建协办: 工单 {} 从 {} 到 {}, 处理时限: {}小时",
            ticket.getTicketNumber(), initiatorDept.getName(), 
            cooperationDept.getName(), hours);
        
        auditService.logOperation("CREATE_COOPERATION", "Ticket", ticket.getId(),
            String.format("发起协办: 部门=%s, 需求=%s, 时限=%d小时", 
                cooperationDept.getName(), requirement, hours),
            null, null, initiator);
        
        return cooperation;
    }

    @Transactional
    public List<TicketCooperation> createBatchCooperations(Ticket ticket, User initiator,
                                                             Long[] cooperationDeptIds, String requirement,
                                                             Integer processingHours) {
        for (Long deptId : cooperationDeptIds) {
            createCooperation(ticket, initiator, deptId, requirement, processingHours);
        }
        
        return cooperationRepository.findByTicketId(ticket.getId());
    }

    @Transactional
    public TicketCooperation acceptCooperation(Long cooperationId, User handler) {
        TicketCooperation cooperation = cooperationRepository.findById(cooperationId)
            .orElseThrow(() -> new IllegalArgumentException("协办记录不存在"));
        
        if (cooperation.getStatus() != TicketCooperation.CooperationStatus.PENDING) {
            throw new IllegalArgumentException("只有待接受的协办才能接单");
        }
        
        cooperation.setStatus(TicketCooperation.CooperationStatus.ACCEPTED);
        cooperation.setHandler(handler);
        cooperation.setAcceptedAt(LocalDateTime.now());
        
        cooperationRepository.save(cooperation);
        
        log.info("接受协办: 工单 {}, 处理人: {}", 
            cooperation.getTicket().getTicketNumber(), handler.getRealName());
        
        auditService.logOperation("ACCEPT_COOPERATION", "TicketCooperation", cooperationId,
            "接受协办", null, null, handler);
        
        return cooperation;
    }

    @Transactional
    public TicketCooperation startCooperationProcessing(Long cooperationId, User handler) {
        TicketCooperation cooperation = cooperationRepository.findById(cooperationId)
            .orElseThrow(() -> new IllegalArgumentException("协办记录不存在"));
        
        if (cooperation.getStatus() != TicketCooperation.CooperationStatus.ACCEPTED) {
            throw new IllegalArgumentException("只有已接受的协办才能开始办理");
        }
        
        cooperation.setStatus(TicketCooperation.CooperationStatus.PROCESSING);
        cooperationRepository.save(cooperation);
        
        log.info("开始协办办理: 工单 {}", cooperation.getTicket().getTicketNumber());
        
        auditService.logOperation("START_COOPERATION_PROCESSING", "TicketCooperation", cooperationId,
            "开始协办办理", null, null, handler);
        
        return cooperation;
    }

    @Transactional
    public TicketCooperation completeCooperation(Long cooperationId, String response, User handler) {
        TicketCooperation cooperation = cooperationRepository.findById(cooperationId)
            .orElseThrow(() -> new IllegalArgumentException("协办记录不存在"));
        
        if (cooperation.getStatus() != TicketCooperation.CooperationStatus.PROCESSING &&
            cooperation.getStatus() != TicketCooperation.CooperationStatus.ACCEPTED) {
            throw new IllegalArgumentException("只有办理中或已接受的协办才能完成");
        }
        
        cooperation.setStatus(TicketCooperation.CooperationStatus.COMPLETED);
        cooperation.setResponse(response);
        cooperation.setCompletedAt(LocalDateTime.now());
        
        cooperationRepository.save(cooperation);
        
        Ticket ticket = cooperation.getTicket();
        
        List<TicketCooperation> activeCooperations = cooperationRepository
            .findActiveCooperationsForTicket(ticket, ACTIVE_STATUSES);
        
        if (activeCooperations.isEmpty()) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticketRepository.save(ticket);
            
            log.info("所有协办完成，工单 {} 恢复到办理中状态", ticket.getTicketNumber());
        }
        
        log.info("完成协办: 工单 {}", ticket.getTicketNumber());
        
        auditService.logOperation("COMPLETE_COOPERATION", "TicketCooperation", cooperationId,
            "完成协办: " + response, null, null, handler);
        
        return cooperation;
    }

    @Transactional
    public TicketCooperation rejectCooperation(Long cooperationId, String reason, User handler) {
        TicketCooperation cooperation = cooperationRepository.findById(cooperationId)
            .orElseThrow(() -> new IllegalArgumentException("协办记录不存在"));
        
        if (cooperation.getStatus() != TicketCooperation.CooperationStatus.PENDING) {
            throw new IllegalArgumentException("只有待接受的协办才能拒绝");
        }
        
        cooperation.setStatus(TicketCooperation.CooperationStatus.REJECTED);
        cooperation.setResponse("拒绝原因: " + reason);
        
        cooperationRepository.save(cooperation);
        
        Ticket ticket = cooperation.getTicket();
        List<TicketCooperation> activeCooperations = cooperationRepository
            .findActiveCooperationsForTicket(ticket, ACTIVE_STATUSES);
        
        if (activeCooperations.isEmpty() && ticket.getStatus() == TicketStatus.COOPERATING) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticketRepository.save(ticket);
        }
        
        log.warn("拒绝协办: 工单 {}, 原因: {}", ticket.getTicketNumber(), reason);
        
        auditService.logOperation("REJECT_COOPERATION", "TicketCooperation", cooperationId,
            "拒绝协办: " + reason, null, null, handler);
        
        return cooperation;
    }

    @Transactional
    public void checkOverdueCooperations() {
        log.info("检查超时协办...");
        
        LocalDateTime now = LocalDateTime.now();
        List<TicketCooperation> overdueCooperations = cooperationRepository
            .findOverdueCooperations(now, ACTIVE_STATUSES);
        
        for (TicketCooperation cooperation : overdueCooperations) {
            if (cooperation.getStatus() != TicketCooperation.CooperationStatus.EXPIRED) {
                cooperation.setStatus(TicketCooperation.CooperationStatus.EXPIRED);
                cooperationRepository.save(cooperation);
                
                log.warn("协办超时: 工单 {}, 协办部门: {}",
                    cooperation.getTicket().getTicketNumber(),
                    cooperation.getCooperationDepartment().getName());
                
                auditService.logOperation("COOPERATION_OVERDUE", "TicketCooperation", 
                    cooperation.getId(), "协办超时", null, null, null);
            }
        }
        
        log.info("超时协办检查完成");
    }

    public List<TicketCooperation> getCooperationsForTicket(Long ticketId) {
        return cooperationRepository.findByTicketIdOrderBySortOrderAsc(ticketId);
    }

    public List<TicketCooperation> getCooperationsForDepartment(Long departmentId) {
        return cooperationRepository.findByCooperationDepartmentId(departmentId);
    }

    public TicketCooperation getCooperationById(Long id) {
        return cooperationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("协办记录不存在"));
    }

    public boolean hasActiveCooperations(Ticket ticket) {
        List<TicketCooperation> activeCooperations = cooperationRepository
            .findActiveCooperationsForTicket(ticket, ACTIVE_STATUSES);
        return !activeCooperations.isEmpty();
    }
}
