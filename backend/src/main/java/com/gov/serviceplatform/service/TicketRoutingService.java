package com.gov.serviceplatform.service;

import com.gov.serviceplatform.entity.Department;
import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.entity.TicketRoutingHistory;
import com.gov.serviceplatform.entity.User;
import com.gov.serviceplatform.enums.TicketStatus;
import com.gov.serviceplatform.repository.DepartmentRepository;
import com.gov.serviceplatform.repository.TicketRepository;
import com.gov.serviceplatform.repository.TicketRoutingHistoryRepository;
import com.gov.serviceplatform.service.ai.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketRoutingService {

    private final TicketRepository ticketRepository;
    private final TicketRoutingHistoryRepository routingHistoryRepository;
    private final DepartmentRepository departmentRepository;
    private final AIService aiService;
    private final AuditService auditService;
    private final SlaService slaService;

    private static final Long GOV_SERVICE_CENTER_ID = 11L;

    @Transactional
    public void recordRouting(Ticket ticket, Department fromDept, Department toDept, 
                              User operator, TicketRoutingHistory.RoutingType routingType,
                              String reason) {
        Integer maxLevel = routingHistoryRepository.findMaxRoutingLevel(ticket.getId());
        int currentLevel = maxLevel != null ? maxLevel + 1 : 1;
        
        TicketRoutingHistory routing = new TicketRoutingHistory();
        routing.setTicket(ticket);
        routing.setFromDepartment(fromDept);
        routing.setToDepartment(toDept);
        routing.setOperator(operator);
        routing.setRoutingType(routingType);
        routing.setRoutingLevel(currentLevel);
        routing.setReason(reason);
        
        routingHistoryRepository.save(routing);
        
        log.info("记录工单 {} 路由: 从 {} 到 {}, 类型: {}, 层级: {}",
            ticket.getTicketNumber(),
            fromDept != null ? fromDept.getName() : "无",
            toDept.getName(),
            routingType.getDescription(),
            currentLevel);
    }

    @Transactional
    public Ticket handleReturnedTicket(Ticket ticket, User operator, String returnReason) {
        log.info("处理退回工单: {}", ticket.getTicketNumber());
        
        TicketRoutingHistory lastRouting = routingHistoryRepository
            .findFirstByTicketIdAndIsReturnedFalseOrderByRoutingLevelDesc(ticket.getId())
            .orElse(null);
        
        if (lastRouting != null && lastRouting.getFromDepartment() != null) {
            lastRouting.setIsReturned(true);
            lastRouting.setReturnReason(returnReason);
            lastRouting.setReturnedAt(LocalDateTime.now());
            routingHistoryRepository.save(lastRouting);
            
            Department prevDept = lastRouting.getFromDepartment();
            
            log.info("工单 {} 自动转派到上一级部门: {}", ticket.getTicketNumber(), prevDept.getName());
            
            ticket.setCurrentDepartment(prevDept);
            ticket.setHandler(null);
            ticket.setStatus(TicketStatus.ASSIGNED);
            
            slaService.calculateAndSetSla(ticket);
            
            recordRouting(ticket, ticket.getCurrentDepartment(), prevDept, operator,
                TicketRoutingHistory.RoutingType.RETURNED, returnReason);
            
            ticketRepository.save(ticket);
            
            auditService.logOperation("AUTO_REASSIGN", "Ticket", ticket.getId(),
                String.format("退回自动转派: 从 %s 到 %s", 
                    ticket.getCurrentDepartment() != null ? ticket.getCurrentDepartment().getName() : "无",
                    prevDept.getName()),
                null, null, operator);
            
            return ticket;
        }
        
        log.warn("工单 {} 无上一级部门，转派到政府服务热线中心", ticket.getTicketNumber());
        
        Department center = departmentRepository.findById(GOV_SERVICE_CENTER_ID)
            .orElseGet(() -> departmentRepository.findAll().stream().findFirst().orElse(null));
        
        if (center != null) {
            ticket.setCurrentDepartment(center);
            ticket.setHandler(null);
            ticket.setStatus(TicketStatus.ASSIGNED);
            
            slaService.calculateAndSetSla(ticket);
            
            recordRouting(ticket, ticket.getCurrentDepartment(), center, operator,
                TicketRoutingHistory.RoutingType.ESCALATION, 
                "无上级部门，转派到热线中心处理: " + returnReason);
            
            ticketRepository.save(ticket);
            
            auditService.logOperation("ESCALATE", "Ticket", ticket.getId(),
                "转派到热线中心处理", null, null, operator);
        }
        
        return ticket;
    }

    @Transactional
    public Ticket autoReassignIfUnaccepted(Ticket ticket) {
        if (ticket.getStatus() != TicketStatus.ASSIGNED && ticket.getStatus() != TicketStatus.TRANSFERRED) {
            return ticket;
        }
        
        if (ticket.getCreatedAt() == null) {
            return ticket;
        }
        
        int acceptTimeout = slaService.getAcceptTimeoutHours(ticket);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = ticket.getCreatedAt().plusHours(acceptTimeout);
        
        if (now.isAfter(deadline)) {
            log.warn("工单 {} 超时未认领（超时时间：{}小时），自动转派", 
                ticket.getTicketNumber(), acceptTimeout);
            
            return handleReturnedTicket(ticket, null, "超时未认领，自动转派");
        }
        
        return ticket;
    }

    @Transactional
    public void checkAndAutoReassignAllUnaccepted() {
        log.info("开始检查超时未认领工单...");
        
        List<Ticket> assignedTickets = ticketRepository.findByStatus(TicketStatus.ASSIGNED, null).getContent();
        
        for (Ticket ticket : assignedTickets) {
            autoReassignIfUnaccepted(ticket);
        }
        
        List<Ticket> transferredTickets = ticketRepository.findByStatus(TicketStatus.TRANSFERRED, null).getContent();
        
        for (Ticket ticket : transferredTickets) {
            autoReassignIfUnaccepted(ticket);
        }
        
        log.info("超时未认领工单检查完成");
    }

    @Transactional
    public Ticket aiAssignTicket(Ticket ticket, User operator) {
        log.info("AI自动派单: {}", ticket.getTicketNumber());
        
        String content = ticket.getTitle() + " " + ticket.getContent();
        AIService.ClassificationResult result = aiService.classifyAndRecommend(content);
        
        ticket.setAiRecommendation(result.getRecommendedDepartment());
        ticket.setAiConfidence(result.getConfidence());
        
        if (result.getCategory() != null) {
            ticket.setCategory(result.getCategory());
        }
        if (result.getSubCategory() != null) {
            ticket.setSubCategory(result.getSubCategory());
        }
        
        Department targetDept = null;
        
        if (result.getRecommendedDepartmentId() != null) {
            targetDept = departmentRepository.findById(result.getRecommendedDepartmentId()).orElse(null);
        }
        
        if (targetDept == null && result.getRecommendedDepartment() != null) {
            List<Department> departments = departmentRepository.findByIsActiveTrue();
            for (Department dept : departments) {
                if (dept.getName().contains(result.getRecommendedDepartment()) ||
                    result.getRecommendedDepartment().contains(dept.getName())) {
                    targetDept = dept;
                    break;
                }
            }
        }
        
        if (targetDept == null) {
            targetDept = departmentRepository.findById(GOV_SERVICE_CENTER_ID)
                .orElseGet(() -> departmentRepository.findByIsActiveTrue().stream().findFirst().orElse(null));
        }
        
        if (targetDept != null) {
            ticket.setCurrentDepartment(targetDept);
            ticket.setStatus(TicketStatus.ASSIGNED);
            
            slaService.calculateAndSetSla(ticket);
            
            recordRouting(ticket, null, targetDept, operator,
                TicketRoutingHistory.RoutingType.AUTO_ROUTE,
                String.format("AI推荐部门: %s, 置信度: %.2f%%", 
                    result.getRecommendedDepartment(), result.getConfidence() * 100));
            
            ticketRepository.save(ticket);
            
            auditService.logOperation("AI_ASSIGN", "Ticket", ticket.getId(),
                String.format("AI自动派单到: %s, 置信度: %.2f%%", 
                    targetDept.getName(), result.getConfidence() * 100),
                null, null, operator);
            
            log.info("工单 {} AI派单完成: 部门={}, 置信度={}", 
                ticket.getTicketNumber(), targetDept.getName(), result.getConfidence());
        }
        
        return ticket;
    }

    @Transactional
    public Ticket escalateTicket(Ticket ticket, User operator, String reason) {
        log.info("工单 {} 升级处理", ticket.getTicketNumber());
        
        TicketRoutingHistory currentRouting = routingHistoryRepository
            .findFirstByTicketIdAndIsReturnedFalseOrderByRoutingLevelDesc(ticket.getId())
            .orElse(null);
        
        Department escalationDept;
        
        if (currentRouting != null && currentRouting.getFromDepartment() != null) {
            escalationDept = currentRouting.getFromDepartment();
        } else {
            escalationDept = departmentRepository.findById(GOV_SERVICE_CENTER_ID)
                .orElseGet(() -> departmentRepository.findByIsActiveTrue().stream().findFirst().orElse(null));
        }
        
        if (escalationDept == null) {
            return ticket;
        }
        
        Department currentDept = ticket.getCurrentDepartment();
        
        ticket.setCurrentDepartment(escalationDept);
        ticket.setHandler(null);
        
        slaService.calculateAndSetSla(ticket);
        
        recordRouting(ticket, currentDept, escalationDept, operator,
            TicketRoutingHistory.RoutingType.ESCALATION, reason);
        
        ticketRepository.save(ticket);
        
        auditService.logOperation("ESCALATE", "Ticket", ticket.getId(),
            String.format("升级: 从 %s 到 %s, 原因: %s", 
                currentDept != null ? currentDept.getName() : "无",
                escalationDept.getName(), reason),
            null, null, operator);
        
        return ticket;
    }

    public List<TicketRoutingHistory> getRoutingHistory(Long ticketId) {
        return routingHistoryRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }
}
