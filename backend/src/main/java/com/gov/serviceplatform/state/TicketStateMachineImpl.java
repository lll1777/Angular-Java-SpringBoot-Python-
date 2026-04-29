package com.gov.serviceplatform.state;

import com.gov.serviceplatform.entity.Department;
import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.entity.TicketFlow;
import com.gov.serviceplatform.entity.TicketRoutingHistory;
import com.gov.serviceplatform.entity.User;
import com.gov.serviceplatform.enums.AlertLevel;
import com.gov.serviceplatform.enums.TicketStatus;
import com.gov.serviceplatform.repository.DepartmentRepository;
import com.gov.serviceplatform.repository.TicketFlowRepository;
import com.gov.serviceplatform.repository.TicketRepository;
import com.gov.serviceplatform.service.AuditService;
import com.gov.serviceplatform.service.SlaService;
import com.gov.serviceplatform.service.TicketCooperationService;
import com.gov.serviceplatform.service.TicketRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketStateMachineImpl implements TicketStateMachine {

    private final TicketRepository ticketRepository;
    private final TicketFlowRepository ticketFlowRepository;
    private final DepartmentRepository departmentRepository;
    private final SlaService slaService;
    private final TicketRoutingService routingService;
    private final TicketCooperationService cooperationService;
    private final AuditService auditService;

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = new HashMap<>();

    static {
        ALLOWED_TRANSITIONS.put(TicketStatus.SUBMITTED, Set.of(TicketStatus.ASSIGNED, TicketStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(TicketStatus.ASSIGNED, Set.of(TicketStatus.ACCEPTED, TicketStatus.TRANSFERRED, TicketStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(TicketStatus.ACCEPTED, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.TRANSFERRED, TicketStatus.RETURNED));
        ALLOWED_TRANSITIONS.put(TicketStatus.IN_PROGRESS, Set.of(
            TicketStatus.TRANSFERRED, TicketStatus.COOPERATING, 
            TicketStatus.RETURNED, TicketStatus.PENDING_REVIEW, 
            TicketStatus.COMPLETED
        ));
        ALLOWED_TRANSITIONS.put(TicketStatus.TRANSFERRED, Set.of(TicketStatus.ACCEPTED, TicketStatus.RETURNED));
        ALLOWED_TRANSITIONS.put(TicketStatus.COOPERATING, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.PENDING_REVIEW, TicketStatus.COMPLETED));
        ALLOWED_TRANSITIONS.put(TicketStatus.RETURNED, Set.of(TicketStatus.ACCEPTED, TicketStatus.TRANSFERRED, TicketStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(TicketStatus.PENDING_REVIEW, Set.of(TicketStatus.COMPLETED, TicketStatus.IN_PROGRESS));
        ALLOWED_TRANSITIONS.put(TicketStatus.COMPLETED, Set.of(TicketStatus.VISITING, TicketStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(TicketStatus.VISITING, Set.of(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS));
        ALLOWED_TRANSITIONS.put(TicketStatus.CLOSED, Set.of());
        ALLOWED_TRANSITIONS.put(TicketStatus.CANCELLED, Set.of());
    }

    @Override
    public boolean canTransition(Ticket ticket, TicketStatus targetStatus) {
        Set<TicketStatus> allowed = ALLOWED_TRANSITIONS.get(ticket.getStatus());
        return allowed != null && allowed.contains(targetStatus);
    }

    @Override
    @Transactional
    public Ticket transition(Ticket ticket, TicketStatus targetStatus, User operator, String remark) {
        if (!canTransition(ticket, targetStatus)) {
            throw new IllegalArgumentException(String.format(
                "无法从状态 %s 转换到 %s", 
                ticket.getStatus().getDescription(), 
                targetStatus.getDescription()
            ));
        }

        TicketStatus fromStatus = ticket.getStatus();
        Department fromDepartment = ticket.getCurrentDepartment();

        ticket.setStatus(targetStatus);
        ticket.setUpdatedAt(LocalDateTime.now());

        Ticket savedTicket = ticketRepository.save(ticket);

        createTicketFlow(savedTicket, fromStatus, targetStatus, operator, fromDepartment, remark);

        slaService.updateRemainingHours(savedTicket);

        auditService.logOperation("TRANSITION_" + targetStatus.name(), "Ticket", 
            ticket.getId(), 
            String.format("状态转换: %s -> %s, 备注: %s", 
                fromStatus.getDescription(), 
                targetStatus.getDescription(),
                remark != null ? remark : "无"),
            fromStatus.name(), targetStatus.name(), operator);

        return savedTicket;
    }

    @Override
    @Transactional
    public Ticket assign(Ticket ticket, User operator) {
        if (ticket.getStatus() != TicketStatus.SUBMITTED) {
            throw new IllegalArgumentException("只有已提交的工单才能派单");
        }
        if (ticket.getCurrentDepartment() == null) {
            throw new IllegalArgumentException("请先指定承办部门");
        }

        slaService.calculateAndSetSla(ticket);

        routingService.recordRouting(ticket, null, ticket.getCurrentDepartment(), 
            operator, TicketRoutingHistory.RoutingType.INITIAL_ASSIGN,
            "初始派单到部门: " + ticket.getCurrentDepartment().getName());

        return transition(ticket, TicketStatus.ASSIGNED, operator, "派单到部门: " + ticket.getCurrentDepartment().getName());
    }

    @Override
    @Transactional
    public Ticket accept(Ticket ticket, User operator) {
        if (ticket.getStatus() != TicketStatus.ASSIGNED && ticket.getStatus() != TicketStatus.TRANSFERRED) {
            throw new IllegalArgumentException("只有已派单或已转办的工单才能接单");
        }
        
        ticket.setHandler(operator);
        ticket.setAcceptedAt(LocalDateTime.now());
        
        return transition(ticket, TicketStatus.ACCEPTED, operator, "接单");
    }

    @Override
    @Transactional
    public Ticket startProcessing(Ticket ticket, User operator, String content) {
        if (ticket.getStatus() != TicketStatus.ACCEPTED) {
            throw new IllegalArgumentException("只有已接单的工单才能开始办理");
        }
        
        return transition(ticket, TicketStatus.IN_PROGRESS, operator, content);
    }

    @Override
    @Transactional
    public Ticket transfer(Ticket ticket, User operator, Long targetDepartmentId, String reason) {
        if (ticket.getStatus() != TicketStatus.ASSIGNED && 
            ticket.getStatus() != TicketStatus.ACCEPTED && 
            ticket.getStatus() != TicketStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("当前状态不允许转办");
        }

        Department targetDept = departmentRepository.findById(targetDepartmentId)
            .orElseThrow(() -> new IllegalArgumentException("目标部门不存在"));

        Department fromDept = ticket.getCurrentDepartment();
        ticket.setCurrentDepartment(targetDept);
        ticket.setHandler(null);

        routingService.recordRouting(ticket, fromDept, targetDept, 
            operator, TicketRoutingHistory.RoutingType.TRANSFER, reason);

        slaService.calculateAndSetSla(ticket);

        TicketFlow flow = createTicketFlow(ticket, ticket.getStatus(), TicketStatus.TRANSFERRED, 
                                            operator, fromDept, reason);
        flow.setFlowType("转办");
        flow.setToDepartment(targetDept);
        ticketFlowRepository.save(flow);

        ticket.setStatus(TicketStatus.TRANSFERRED);
        ticket.setUpdatedAt(LocalDateTime.now());
        
        auditService.logOperation("TRANSFER", "Ticket", ticket.getId(),
            String.format("转办: 从 %s 到 %s, 原因: %s", 
                fromDept.getName(), targetDept.getName(), reason),
            null, null, operator);
        
        return ticketRepository.save(ticket);
    }

    @Override
    @Transactional
    public Ticket cooperate(Ticket ticket, User operator, Long[] coDepartmentIds, String requirement) {
        if (ticket.getStatus() != TicketStatus.IN_PROGRESS && ticket.getStatus() != TicketStatus.COOPERATING) {
            throw new IllegalArgumentException("只有办理中或协办中的工单才能发起协办");
        }

        if (coDepartmentIds == null || coDepartmentIds.length == 0) {
            throw new IllegalArgumentException("请选择协办部门");
        }

        for (Long deptId : coDepartmentIds) {
            cooperationService.createCooperation(ticket, operator, deptId, requirement, 24);
        }

        if (ticket.getStatus() != TicketStatus.COOPERATING) {
            ticket.setStatus(TicketStatus.COOPERATING);
            ticketRepository.save(ticket);
        }

        return ticket;
    }

    @Override
    @Transactional
    public Ticket returnToPrevious(Ticket ticket, User operator, String reason) {
        if (ticket.getStatus() != TicketStatus.ACCEPTED && 
            ticket.getStatus() != TicketStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("当前状态不允许退回");
        }

        TicketFlow flow = createTicketFlow(ticket, ticket.getStatus(), TicketStatus.RETURNED, 
                                            operator, ticket.getCurrentDepartment(), "退回原因：" + reason);
        flow.setFlowType("退回");
        ticketFlowRepository.save(flow);

        auditService.logOperation("RETURN", "Ticket", ticket.getId(),
            "退回原因: " + reason, null, null, operator);

        Ticket returnedTicket = routingService.handleReturnedTicket(ticket, operator, reason);

        log.info("工单 {} 已退回并自动转派到上一级部门", ticket.getTicketNumber());

        return returnedTicket;
    }

    @Override
    @Transactional
    public Ticket submitForReview(Ticket ticket, User operator, String result) {
        if (ticket.getStatus() != TicketStatus.IN_PROGRESS && 
            ticket.getStatus() != TicketStatus.COOPERATING) {
            throw new IllegalArgumentException("只有办理中或协办中的工单才能提交审核");
        }

        if (cooperationService.hasActiveCooperations(ticket)) {
            throw new IllegalArgumentException("存在未完成的协办，无法提交审核");
        }

        return transition(ticket, TicketStatus.PENDING_REVIEW, operator, "办理结果：" + result);
    }

    @Override
    @Transactional
    public Ticket complete(Ticket ticket, User operator, String completionContent) {
        if (ticket.getStatus() != TicketStatus.PENDING_REVIEW && 
            ticket.getStatus() != TicketStatus.IN_PROGRESS &&
            ticket.getStatus() != TicketStatus.COOPERATING) {
            throw new IllegalArgumentException("只有待审核、办理中或协办中的工单才能办结");
        }

        if (cooperationService.hasActiveCooperations(ticket)) {
            throw new IllegalArgumentException("存在未完成的协办，无法办结");
        }

        ticket.setCompletedAt(LocalDateTime.now());
        
        AlertLevel currentAlert = slaService.calculateCurrentAlertLevel(ticket);
        ticket.setAlertLevel(currentAlert);
        
        return transition(ticket, TicketStatus.COMPLETED, operator, completionContent);
    }

    @Override
    @Transactional
    public Ticket startVisit(Ticket ticket, User operator) {
        if (ticket.getStatus() != TicketStatus.COMPLETED) {
            throw new IllegalArgumentException("只有已办结的工单才能开始回访");
        }

        return transition(ticket, TicketStatus.VISITING, operator, "开始回访");
    }

    @Override
    @Transactional
    public Ticket close(Ticket ticket, User operator, Integer satisfaction, String comment) {
        if (ticket.getStatus() != TicketStatus.COMPLETED && ticket.getStatus() != TicketStatus.VISITING) {
            throw new IllegalArgumentException("只有已办结或回访中的工单才能关闭");
        }

        if (satisfaction != null) {
            ticket.setSatisfactionScore(satisfaction);
        }
        if (comment != null) {
            ticket.setSatisfactionComment(comment);
        }
        ticket.setClosedAt(LocalDateTime.now());

        return transition(ticket, TicketStatus.CLOSED, operator, 
            "满意度：" + (satisfaction != null ? satisfaction : "未评价") + 
            (comment != null ? "，评价：" + comment : ""));
    }

    @Override
    @Transactional
    public Ticket cancel(Ticket ticket, User operator, String reason) {
        if (ticket.getStatus() == TicketStatus.COMPLETED || 
            ticket.getStatus() == TicketStatus.VISITING ||
            ticket.getStatus() == TicketStatus.CLOSED ||
            ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new IllegalArgumentException("当前状态不允许取消");
        }

        return transition(ticket, TicketStatus.CANCELLED, operator, "取消原因：" + reason);
    }

    @Transactional
    public Ticket escalate(Ticket ticket, User operator, String reason) {
        return routingService.escalateTicket(ticket, operator, reason);
    }

    private TicketFlow createTicketFlow(Ticket ticket, TicketStatus fromStatus, TicketStatus toStatus,
                                         User operator, Department fromDepartment, String remark) {
        TicketFlow flow = new TicketFlow();
        flow.setTicket(ticket);
        flow.setFromStatus(fromStatus);
        flow.setToStatus(toStatus);
        flow.setOperator(operator);
        flow.setOperatorName(operator != null ? operator.getRealName() : "系统");
        flow.setFromDepartment(fromDepartment);
        flow.setToDepartment(ticket.getCurrentDepartment());
        flow.setRemark(remark);
        flow.setFlowType("状态流转");
        
        return ticketFlowRepository.save(flow);
    }
}
