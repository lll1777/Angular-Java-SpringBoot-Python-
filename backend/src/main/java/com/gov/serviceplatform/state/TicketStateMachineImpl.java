package com.gov.serviceplatform.state;

import com.gov.serviceplatform.entity.*;
import com.gov.serviceplatform.enums.AlertLevel;
import com.gov.serviceplatform.enums.TicketStatus;
import com.gov.serviceplatform.repository.*;
import com.gov.serviceplatform.service.AuditService;
import com.gov.serviceplatform.service.SlaService;
import com.gov.serviceplatform.service.WorkCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketStateMachineImpl implements TicketStateMachine {

    private final TicketRepository ticketRepository;
    private final TicketFlowRepository ticketFlowRepository;
    private final DepartmentRepository departmentRepository;
    private final CooperationRecordRepository cooperationRecordRepository;
    private final SlaService slaService;
    private final WorkCalendarService workCalendarService;
    private final AuditService auditService;

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = new HashMap<>();

    static {
        ALLOWED_TRANSITIONS.put(TicketStatus.SUBMITTED, Set.of(TicketStatus.ASSIGNED, TicketStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(TicketStatus.ASSIGNED, Set.of(TicketStatus.ACCEPTED, TicketStatus.TRANSFERRED, TicketStatus.RETURNED, TicketStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(TicketStatus.ACCEPTED, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.TRANSFERRED, TicketStatus.RETURNED));
        ALLOWED_TRANSITIONS.put(TicketStatus.IN_PROGRESS, Set.of(
            TicketStatus.TRANSFERRED, TicketStatus.COOPERATING, 
            TicketStatus.RETURNED, TicketStatus.PENDING_REVIEW, 
            TicketStatus.COMPLETED, TicketStatus.ASSIGNED
        ));
        ALLOWED_TRANSITIONS.put(TicketStatus.TRANSFERRED, Set.of(TicketStatus.ACCEPTED, TicketStatus.RETURNED, TicketStatus.ASSIGNED));
        ALLOWED_TRANSITIONS.put(TicketStatus.COOPERATING, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.PENDING_REVIEW, TicketStatus.COMPLETED));
        ALLOWED_TRANSITIONS.put(TicketStatus.RETURNED, Set.of(TicketStatus.ACCEPTED, TicketStatus.TRANSFERRED, TicketStatus.ASSIGNED, TicketStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(TicketStatus.PENDING_REVIEW, Set.of(TicketStatus.COMPLETED, TicketStatus.IN_PROGRESS));
        ALLOWED_TRANSITIONS.put(TicketStatus.COMPLETED, Set.of(TicketStatus.VISITING, TicketStatus.CLOSED, TicketStatus.IN_PROGRESS));
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

        createTicketFlow(savedTicket, fromStatus, targetStatus, operator, fromDepartment, remark, "状态流转");

        auditService.logOperation(
            "STATUS_TRANSITION", 
            "Ticket", 
            ticket.getId(),
            String.format("从 %s 转换到 %s", fromStatus.getDescription(), targetStatus.getDescription()),
            fromStatus.name(),
            targetStatus.name(),
            operator
        );

        return savedTicket;
    }

    @Override
    @Transactional
    public Ticket assign(Ticket ticket, User operator) {
        if (ticket.getStatus() != TicketStatus.SUBMITTED && ticket.getStatus() != TicketStatus.RETURNED) {
            throw new IllegalArgumentException("只有已提交或已退回的工单才能派单");
        }
        if (ticket.getCurrentDepartment() == null) {
            throw new IllegalArgumentException("请先指定承办部门");
        }

        slaService.calculateAndSetSlaTimes(ticket);
        ticket.setAlertLevel(AlertLevel.NORMAL);

        log.info("工单 {} 派单到部门: {}", ticket.getTicketNumber(), ticket.getCurrentDepartment().getName());

        return transition(ticket, TicketStatus.ASSIGNED, operator, "派单到部门: " + ticket.getCurrentDepartment().getName());
    }

    @Override
    @Transactional
    public Ticket autoAssign(Ticket ticket) {
        log.info("开始自动派单: 工单 {}", ticket.getTicketNumber());

        if (ticket.getCurrentDepartment() == null) {
            log.warn("工单 {} 没有指定部门，无法自动派单", ticket.getTicketNumber());
            return ticket;
        }

        if (ticket.getStatus() == TicketStatus.SUBMITTED || ticket.getStatus() == TicketStatus.RETURNED) {
            return assign(ticket, null);
        }

        return ticket;
    }

    @Override
    @Transactional
    public Ticket accept(Ticket ticket, User operator) {
        if (ticket.getStatus() != TicketStatus.ASSIGNED && ticket.getStatus() != TicketStatus.TRANSFERRED) {
            throw new IllegalArgumentException("只有已派单或已转办的工单才能接单");
        }
        
        ticket.setHandler(operator);
        ticket.setAcceptedAt(LocalDateTime.now());
        
        log.info("工单 {} 被 {} 接单", ticket.getTicketNumber(), operator.getRealName());

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
            ticket.getStatus() != TicketStatus.IN_PROGRESS &&
            ticket.getStatus() != TicketStatus.RETURNED) {
            throw new IllegalArgumentException("当前状态不允许转办");
        }

        Department targetDept = departmentRepository.findById(targetDepartmentId)
            .orElseThrow(() -> new IllegalArgumentException("目标部门不存在"));

        Department fromDept = ticket.getCurrentDepartment();
        ticket.setCurrentDepartment(targetDept);
        ticket.setHandler(null);

        String remark = "转办原因: " + (reason != null ? reason : "无");
        TicketFlow flow = createTicketFlow(ticket, ticket.getStatus(), TicketStatus.TRANSFERRED, 
                                            operator, fromDept, remark, "转办");
        flow.setToDepartment(targetDept);
        ticketFlowRepository.save(flow);

        ticket.setStatus(TicketStatus.TRANSFERRED);
        ticket.setUpdatedAt(LocalDateTime.now());
        
        log.info("工单 {} 从 {} 转办到 {}", ticket.getTicketNumber(), 
            fromDept != null ? fromDept.getName() : "未指定", targetDept.getName());

        auditService.logOperation(
            "TRANSFER", 
            "Ticket", 
            ticket.getId(),
            String.format("从部门 %s 转办到 %s，原因: %s", 
                fromDept != null ? fromDept.getName() : "未指定", 
                targetDept.getName(), reason),
            fromDept != null ? fromDept.getName() : null,
            targetDept.getName(),
            operator
        );

        return ticketRepository.save(ticket);
    }

    @Override
    @Transactional
    public Ticket cooperate(Ticket ticket, User operator, Long[] coDepartmentIds, String requirement) {
        if (ticket.getStatus() != TicketStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("只有办理中的工单才能发起协办");
        }

        if (coDepartmentIds == null || coDepartmentIds.length == 0) {
            throw new IllegalArgumentException("请指定协办部门");
        }

        for (Long deptId : coDepartmentIds) {
            Department coDept = departmentRepository.findById(deptId)
                .orElseThrow(() -> new IllegalArgumentException("协办部门不存在: " + deptId));

            CooperationRecord record = new CooperationRecord();
            record.setTicket(ticket);
            record.setInitiatingDepartment(ticket.getCurrentDepartment());
            record.setCoDepartment(coDept);
            record.setInitiator(operator);
            record.setStatus("PENDING");
            record.setRequirement(requirement);
            record.setRequiredHours(24);
            
            LocalDateTime dueTime = workCalendarService.addWorkHours(LocalDateTime.now(), 24);
            record.setDueTime(dueTime);
            
            cooperationRecordRepository.save(record);
            
            log.info("工单 {} 向部门 {} 发起协办", ticket.getTicketNumber(), coDept.getName());
        }

        return transition(ticket, TicketStatus.COOPERATING, operator, "发起协办，需求: " + requirement);
    }

    @Override
    @Transactional
    public CooperationRecord acceptCooperation(Long cooperationId, User operator) {
        CooperationRecord record = cooperationRecordRepository.findById(cooperationId)
            .orElseThrow(() -> new IllegalArgumentException("协办记录不存在"));

        if (!"PENDING".equals(record.getStatus())) {
            throw new IllegalArgumentException("该协办已被处理");
        }

        record.setStatus("IN_PROGRESS");
        record.setCoHandler(operator);
        record.setAcceptedAt(LocalDateTime.now());

        CooperationRecord savedRecord = cooperationRecordRepository.save(record);
        
        log.info("用户 {} 接受协办任务 {}", operator.getRealName(), cooperationId);

        auditService.logOperation(
            "ACCEPT_COOPERATION", 
            "CooperationRecord", 
            cooperationId,
            "接受协办任务",
            "PENDING",
            "IN_PROGRESS",
            operator
        );

        return savedRecord;
    }

    @Override
    @Transactional
    public CooperationRecord completeCooperation(Long cooperationId, User operator, String response) {
        CooperationRecord record = cooperationRecordRepository.findById(cooperationId)
            .orElseThrow(() -> new IllegalArgumentException("协办记录不存在"));

        if (!"IN_PROGRESS".equals(record.getStatus())) {
            throw new IllegalArgumentException("该协办不在办理中");
        }

        record.setStatus("COMPLETED");
        record.setResponse(response);
        record.setCompletedAt(LocalDateTime.now());

        CooperationRecord savedRecord = cooperationRecordRepository.save(record);
        
        log.info("协办任务 {} 已完成", cooperationId);

        Ticket ticket = record.getTicket();
        long pendingCount = cooperationRecordRepository.countByCoDepartmentIdAndStatus(
            record.getCoDepartment().getId(), "PENDING");
        long inProgressCount = cooperationRecordRepository.countByCoDepartmentIdAndStatus(
            record.getCoDepartment().getId(), "IN_PROGRESS");
        
        if (pendingCount == 0 && inProgressCount == 0 && ticket.getStatus() == TicketStatus.COOPERATING) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticketRepository.save(ticket);
            log.info("所有协办任务已完成，工单 {} 恢复到办理中状态", ticket.getTicketNumber());
        }

        auditService.logOperation(
            "COMPLETE_COOPERATION", 
            "CooperationRecord", 
            cooperationId,
            "完成协办: " + response,
            "IN_PROGRESS",
            "COMPLETED",
            operator
        );

        return savedRecord;
    }

    @Override
    @Transactional
    public Ticket returnToPrevious(Ticket ticket, User operator, String reason) {
        if (ticket.getStatus() != TicketStatus.ASSIGNED && 
            ticket.getStatus() != TicketStatus.ACCEPTED && 
            ticket.getStatus() != TicketStatus.IN_PROGRESS &&
            ticket.getStatus() != TicketStatus.TRANSFERRED) {
            throw new IllegalArgumentException("当前状态不允许退回");
        }

        log.info("工单 {} 被退回，原因: {}", ticket.getTicketNumber(), reason);

        return transition(ticket, TicketStatus.RETURNED, operator, "退回原因: " + reason);
    }

    @Override
    @Transactional
    public Ticket escalate(Ticket ticket, User operator, String reason) {
        log.info("工单 {} 升级处理，原因: {}", ticket.getTicketNumber(), reason);

        ticket.setPriorityLevel(Math.min(ticket.getPriorityLevel() + 1, 3));
        
        if (ticket.getPriorityLevel() >= 2) {
            ticket.setIsUrgent(true);
        }

        slaService.calculateAndSetSlaTimes(ticket);

        createTicketFlow(ticket, ticket.getStatus(), ticket.getStatus(), operator, 
            ticket.getCurrentDepartment(), "升级原因: " + reason, "升级");

        auditService.logOperation(
            "ESCALATE", 
            "Ticket", 
            ticket.getId(),
            "工单升级，原因: " + reason,
            null,
            "优先级: " + ticket.getPriorityLevel(),
            operator
        );

        return ticketRepository.save(ticket);
    }

    @Override
    @Transactional
    public Ticket autoEscalate(Ticket ticket) {
        log.warn("工单 {} 自动升级（超时未处理）", ticket.getTicketNumber());
        return escalate(ticket, null, "系统自动升级：超时未处理");
    }

    @Override
    @Transactional
    public Ticket handleReturnAndReassign(Ticket ticket, User operator, String returnReason) {
        log.info("处理退回工单 {}，开始重新派单", ticket.getTicketNumber());

        Ticket returnedTicket = returnToPrevious(ticket, operator, returnReason);
        
        if (returnedTicket.getCurrentDepartment() != null) {
            log.info("退回工单 {} 自动重新派单", ticket.getTicketNumber());
            return autoAssign(returnedTicket);
        }

        return returnedTicket;
    }

    @Override
    @Transactional
    public void handleClaimTimeout(Ticket ticket) {
        if (ticket.getStatus() != TicketStatus.ASSIGNED && ticket.getStatus() != TicketStatus.TRANSFERRED) {
            return;
        }

        log.warn("工单 {} 超时未认领，触发自动升级和转派", ticket.getTicketNumber());

        autoEscalate(ticket);

        createTicketFlow(ticket, ticket.getStatus(), ticket.getStatus(), null, 
            ticket.getCurrentDepartment(), "超时未认领，触发自动处理", "超时处理");

        auditService.logOperation(
            "CLAIM_TIMEOUT", 
            "Ticket", 
            ticket.getId(),
            "超时未认领，触发自动处理",
            null,
            null,
            null
        );
    }

    @Override
    @Transactional
    public Ticket submitForReview(Ticket ticket, User operator, String result) {
        if (ticket.getStatus() != TicketStatus.IN_PROGRESS && ticket.getStatus() != TicketStatus.COOPERATING) {
            throw new IllegalArgumentException("只有办理中或协办中的工单才能提交审核");
        }

        return transition(ticket, TicketStatus.PENDING_REVIEW, operator, "办理结果: " + result);
    }

    @Override
    @Transactional
    public Ticket complete(Ticket ticket, User operator, String completionContent) {
        if (ticket.getStatus() != TicketStatus.PENDING_REVIEW && 
            ticket.getStatus() != TicketStatus.IN_PROGRESS &&
            ticket.getStatus() != TicketStatus.COOPERATING) {
            throw new IllegalArgumentException("只有待审核、办理中或协办中的工单才能办结");
        }

        ticket.setCompletedAt(LocalDateTime.now());
        
        slaService.updateRemainingHours(ticket);

        log.info("工单 {} 办结", ticket.getTicketNumber());

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

        log.info("工单 {} 关闭", ticket.getTicketNumber());

        String remark = "满意度：" + (satisfaction != null ? satisfaction : "未评价") + 
            (comment != null ? "，评价：" + comment : "");

        return transition(ticket, TicketStatus.CLOSED, operator, remark);
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

        log.info("工单 {} 取消，原因: {}", ticket.getTicketNumber(), reason);

        return transition(ticket, TicketStatus.CANCELLED, operator, "取消原因: " + reason);
    }

    @Override
    public List<CooperationRecord> getCooperationRecords(Ticket ticket) {
        return cooperationRecordRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
    }

    private TicketFlow createTicketFlow(Ticket ticket, TicketStatus fromStatus, TicketStatus toStatus,
                                         User operator, Department fromDepartment, String remark, String flowType) {
        TicketFlow flow = new TicketFlow();
        flow.setTicket(ticket);
        flow.setFromStatus(fromStatus);
        flow.setToStatus(toStatus);
        flow.setOperator(operator);
        flow.setOperatorName(operator != null ? operator.getRealName() : "系统");
        flow.setFromDepartment(fromDepartment);
        flow.setToDepartment(ticket.getCurrentDepartment());
        flow.setRemark(remark);
        flow.setFlowType(flowType);
        
        return ticketFlowRepository.save(flow);
    }
}
