package com.gov.serviceplatform.service;

import com.gov.serviceplatform.dto.TicketCreateDTO;
import com.gov.serviceplatform.dto.TicketQueryDTO;
import com.gov.serviceplatform.entity.Department;
import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.entity.TicketCooperation;
import com.gov.serviceplatform.entity.TicketRoutingHistory;
import com.gov.serviceplatform.entity.User;
import com.gov.serviceplatform.enums.TicketStatus;
import com.gov.serviceplatform.repository.DepartmentRepository;
import com.gov.serviceplatform.repository.TicketFlowRepository;
import com.gov.serviceplatform.repository.TicketRepository;
import com.gov.serviceplatform.service.ai.AIService;
import com.gov.serviceplatform.state.TicketStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final DepartmentRepository departmentRepository;
    private final TicketStateMachine stateMachine;
    private final AIService aiService;
    private final AuditService auditService;
    private final TicketRoutingService routingService;
    private final TicketCooperationService cooperationService;
    private final WorkCalendarService workCalendarService;
    private final SlaService slaService;

    @Transactional
    public Ticket createTicket(TicketCreateDTO dto, User citizen) {
        Ticket ticket = new Ticket();
        ticket.setTitle(dto.getTitle());
        ticket.setContent(dto.getContent());
        ticket.setCategory(dto.getCategory());
        ticket.setSubCategory(dto.getSubCategory());
        ticket.setCitizen(citizen);
        ticket.setCitizenName(dto.getCitizenName() != null ? dto.getCitizenName() : (citizen != null ? citizen.getRealName() : null));
        ticket.setCitizenPhone(dto.getCitizenPhone() != null ? dto.getCitizenPhone() : (citizen != null ? citizen.getPhone() : null));
        ticket.setAddress(dto.getAddress());
        ticket.setIsAnonymous(dto.getIsAnonymous() != null ? dto.getIsAnonymous() : false);
        ticket.setIsUrgent(dto.getIsUrgent() != null ? dto.getIsUrgent() : false);
        ticket.setPriorityLevel(dto.getPriorityLevel() != null ? dto.getPriorityLevel() : 1);
        ticket.setStatus(TicketStatus.SUBMITTED);

        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(dto.getDepartmentId())
                .orElseThrow(() -> new IllegalArgumentException("部门不存在"));
            ticket.setCurrentDepartment(dept);
        }

        Ticket savedTicket = ticketRepository.save(ticket);

        processTicketWithAI(savedTicket);

        auditService.logOperation("CREATE", "Ticket", savedTicket.getId(), 
            "创建诉求工单", null, savedTicket.toString(), citizen);

        return savedTicket;
    }

    @Transactional
    public void processTicketWithAI(Ticket ticket) {
        routingService.aiAssignTicket(ticket, null);
    }

    @Transactional
    public Ticket assignTicket(Long ticketId, Long departmentId, User operator) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("工单不存在"));

        if (departmentId != null) {
            Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("部门不存在"));
            ticket.setCurrentDepartment(dept);
            ticketRepository.save(ticket);
        }

        Ticket assignedTicket = stateMachine.assign(ticket, operator);

        auditService.logOperation("ASSIGN", "Ticket", ticketId, 
            "派单到部门：" + (ticket.getCurrentDepartment() != null ? ticket.getCurrentDepartment().getName() : "未知"),
            null, null, operator);

        return assignedTicket;
    }

    @Transactional
    public Ticket acceptTicket(Long ticketId, User operator) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("工单不存在"));

        Ticket acceptedTicket = stateMachine.accept(ticket, operator);

        auditService.logOperation("ACCEPT", "Ticket", ticketId, "接单", null, null, operator);

        return acceptedTicket;
    }

    @Transactional
    public Ticket startProcessing(Long ticketId, String content, User operator) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("工单不存在"));

        Ticket processedTicket = stateMachine.startProcessing(ticket, operator, content);

        auditService.logOperation("START_PROCESSING", "Ticket", ticketId, 
            "开始办理：" + content, null, null, operator);

        return processedTicket;
    }

    @Transactional
    public Ticket transferTicket(Long ticketId, Long targetDepartmentId, String reason, User operator) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("工单不存在"));

        Ticket transferredTicket = stateMachine.transfer(ticket, operator, targetDepartmentId, reason);

        auditService.logOperation("TRANSFER", "Ticket", ticketId, 
            "转办，原因：" + reason, null, null, operator);

        return transferredTicket;
    }

    @Transactional
    public Ticket returnTicket(Long ticketId, String reason, User operator) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("工单不存在"));

        Ticket returnedTicket = stateMachine.returnToPrevious(ticket, operator, reason);

        return returnedTicket;
    }

    @Transactional
    public Ticket escalateTicket(Long ticketId, String reason, User operator) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("工单不存在"));

        return routingService.escalateTicket(ticket, operator, reason);
    }

    @Transactional
    public TicketCooperation createCooperation(Long ticketId, Long cooperationDeptId, 
                                                String requirement, Integer processingHours, User operator) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("工单不存在"));

        return cooperationService.createCooperation(ticket, operator, cooperationDeptId, requirement, processingHours);
    }

    @Transactional
    public TicketCooperation acceptCooperation(Long cooperationId, User operator) {
        return cooperationService.acceptCooperation(cooperationId, operator);
    }

    @Transactional
    public TicketCooperation completeCooperation(Long cooperationId, String response, User operator) {
        return cooperationService.completeCooperation(cooperationId, response, operator);
    }

    @Transactional
    public Ticket completeTicket(Long ticketId, String completionContent, User operator) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("工单不存在"));

        Ticket completedTicket = stateMachine.complete(ticket, operator, completionContent);

        auditService.logOperation("COMPLETE", "Ticket", ticketId, 
            "办结，内容：" + completionContent, null, null, operator);

        return completedTicket;
    }

    @Transactional
    public Ticket closeTicket(Long ticketId, Integer satisfaction, String comment, User operator) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("工单不存在"));

        Ticket closedTicket = stateMachine.close(ticket, operator, satisfaction, comment);

        auditService.logOperation("CLOSE", "Ticket", ticketId, 
            "关闭工单，满意度：" + satisfaction, null, null, operator);

        return closedTicket;
    }

    @Transactional
    public Ticket cancelTicket(Long ticketId, String reason, User operator) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("工单不存在"));

        Ticket cancelledTicket = stateMachine.cancel(ticket, operator, reason);

        auditService.logOperation("CANCEL", "Ticket", ticketId, 
            "取消工单，原因：" + reason, null, null, operator);

        return cancelledTicket;
    }

    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
    }

    public Ticket getTicketByNumber(String ticketNumber) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber);
        if (ticket == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        return ticket;
    }

    public Page<Ticket> getTicketsByCitizen(Long citizenId, Pageable pageable) {
        return ticketRepository.findByCitizenId(citizenId, pageable);
    }

    public Page<Ticket> getTicketsByDepartment(Long departmentId, Pageable pageable) {
        return ticketRepository.findByCurrentDepartmentId(departmentId, pageable);
    }

    public Page<Ticket> getTicketsByHandler(Long handlerId, Pageable pageable) {
        return ticketRepository.findByHandlerId(handlerId, pageable);
    }

    public Page<Ticket> getTicketsByStatus(TicketStatus status, Pageable pageable) {
        return ticketRepository.findByStatus(status, pageable);
    }

    public List<TicketRoutingHistory> getTicketRoutingHistory(Long ticketId) {
        return routingService.getRoutingHistory(ticketId);
    }

    public List<TicketCooperation> getTicketCooperations(Long ticketId) {
        return cooperationService.getCooperationsForTicket(ticketId);
    }

    public Page<Ticket> queryTickets(TicketQueryDTO query, Pageable pageable) {
        return ticketRepository.findAll(pageable);
    }
}
