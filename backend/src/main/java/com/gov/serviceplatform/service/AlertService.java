package com.gov.serviceplatform.service;

import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.enums.AlertLevel;
import com.gov.serviceplatform.enums.TicketStatus;
import com.gov.serviceplatform.repository.TicketRepository;
import com.gov.serviceplatform.state.TicketStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final TicketRepository ticketRepository;
    private final AuditService auditService;
    private final SlaService slaService;
    private final TicketStateMachine ticketStateMachine;

    private static final List<TicketStatus> COMPLETED_STATUSES = Arrays.asList(
        TicketStatus.COMPLETED, TicketStatus.CLOSED, TicketStatus.CANCELLED
    );

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void checkAndUpdateAlerts() {
        log.info("开始执行时效预警检查任务: {}", LocalDateTime.now());
        
        LocalDateTime now = LocalDateTime.now();
        
        checkClaimTimeouts(now);
        
        updateYellowWarnings(now);
        
        updateRedWarnings(now);
        
        updateOverdueStatus(now);
        
        updateRemainingHoursForActiveTickets();
        
        log.info("时效预警检查任务完成");
    }

    private void checkClaimTimeouts(LocalDateTime now) {
        log.info("检查超时认领工单");
        
        List<Ticket> unclaimedTickets = ticketRepository.findByStatus(
            TicketStatus.ASSIGNED,
            org.springframework.data.domain.Pageable.unpaged()
        ).getContent();
        
        unclaimedTickets.addAll(ticketRepository.findByStatus(
            TicketStatus.TRANSFERRED,
            org.springframework.data.domain.Pageable.unpaged()
        ).getContent());
        
        for (Ticket ticket : unclaimedTickets) {
            if (ticket.getCreatedAt() == null) {
                continue;
            }
            
            LocalDateTime claimDueTime = slaService.calculateClaimDueTime(ticket);
            
            if (now.isAfter(claimDueTime)) {
                log.warn("工单 {} 超时未认领，触发自动处理", ticket.getTicketNumber());
                ticketStateMachine.handleClaimTimeout(ticket);
            }
        }
    }

    private void updateYellowWarnings(LocalDateTime now) {
        List<Ticket> tickets = ticketRepository.findTicketsNeedingYellowWarning(
            now, COMPLETED_STATUSES, AlertLevel.NORMAL
        );
        
        for (Ticket ticket : tickets) {
            if (ticket.getYellowWarningTime() != null && 
                now.isAfter(ticket.getYellowWarningTime())) {
                ticket.setAlertLevel(AlertLevel.YELLOW_WARNING);
                ticketRepository.save(ticket);
                log.warn("工单 {} 触发黄牌预警", ticket.getTicketNumber());
                
                auditService.logOperation("YELLOW_WARNING", "Ticket", ticket.getId(),
                    "时效黄牌警告 - 剩余时间: " + ticket.getRemainingHours() + "小时", 
                    AlertLevel.NORMAL.name(), AlertLevel.YELLOW_WARNING.name(), null);
            }
        }
    }

    private void updateRedWarnings(LocalDateTime now) {
        List<Ticket> tickets = ticketRepository.findTicketsNeedingRedWarning(
            now, COMPLETED_STATUSES, Arrays.asList(AlertLevel.NORMAL, AlertLevel.YELLOW_WARNING)
        );
        
        for (Ticket ticket : tickets) {
            if (ticket.getRedWarningTime() != null && 
                now.isAfter(ticket.getRedWarningTime()) &&
                ticket.getAlertLevel() != AlertLevel.RED_WARNING &&
                ticket.getAlertLevel() != AlertLevel.OVERDUE) {
                ticket.setAlertLevel(AlertLevel.RED_WARNING);
                ticketRepository.save(ticket);
                log.warn("工单 {} 触发红牌预警", ticket.getTicketNumber());
                
                auditService.logOperation("RED_WARNING", "Ticket", ticket.getId(),
                    "时效红牌警告 - 剩余时间: " + ticket.getRemainingHours() + "小时",
                    ticket.getAlertLevel() != null ? ticket.getAlertLevel().name() : null, 
                    AlertLevel.RED_WARNING.name(), null);
            }
        }
    }

    private void updateOverdueStatus(LocalDateTime now) {
        List<Ticket> tickets = ticketRepository.findOverdueTickets(now, COMPLETED_STATUSES);
        
        for (Ticket ticket : tickets) {
            if (ticket.getDueTime() != null && 
                now.isAfter(ticket.getDueTime()) &&
                ticket.getAlertLevel() != AlertLevel.OVERDUE) {
                ticket.setAlertLevel(AlertLevel.OVERDUE);
                ticket.setRemainingHours(0);
                ticketRepository.save(ticket);
                log.warn("工单 {} 已逾期", ticket.getTicketNumber());
                
                auditService.logOperation("OVERDUE", "Ticket", ticket.getId(),
                    "工单逾期 - 应完成时间: " + ticket.getDueTime(),
                    ticket.getAlertLevel() != null ? ticket.getAlertLevel().name() : null, 
                    AlertLevel.OVERDUE.name(), null);
            }
        }
    }

    private void updateRemainingHoursForActiveTickets() {
        List<TicketStatus> activeStatuses = Arrays.asList(
            TicketStatus.ASSIGNED, TicketStatus.ACCEPTED, 
            TicketStatus.IN_PROGRESS, TicketStatus.TRANSFERRED,
            TicketStatus.COOPERATING, TicketStatus.PENDING_REVIEW
        );
        
        for (TicketStatus status : activeStatuses) {
            List<Ticket> tickets = ticketRepository.findByStatus(
                status, org.springframework.data.domain.Pageable.unpaged()
            ).getContent();
            
            for (Ticket ticket : tickets) {
                slaService.updateRemainingHours(ticket);
            }
        }
    }

    @Transactional
    public void recalculateSlaForTicket(Ticket ticket) {
        log.info("重新计算工单 {} 的SLA时间", ticket.getTicketNumber());
        
        slaService.calculateAndSetSlaTimes(ticket);
        ticket.setAlertLevel(AlertLevel.NORMAL);
        ticketRepository.save(ticket);
        
        auditService.logOperation(
            "SLA_RECALCULATE", 
            "Ticket", 
            ticket.getId(),
            "重新计算SLA时间",
            null,
            "截止时间: " + ticket.getDueTime(),
            null
        );
    }

    public List<Ticket> getHighAlertTickets() {
        return ticketRepository.findByAlertLevelIn(
            Arrays.asList(AlertLevel.YELLOW_WARNING, AlertLevel.RED_WARNING, AlertLevel.OVERDUE)
        );
    }

    public long countAlertsByLevel(Long departmentId, AlertLevel level) {
        return ticketRepository.countByDepartmentAndAlertLevel(departmentId, level);
    }
}
