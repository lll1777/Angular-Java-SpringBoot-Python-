package com.gov.serviceplatform.service;

import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.enums.AlertLevel;
import com.gov.serviceplatform.enums.TicketStatus;
import com.gov.serviceplatform.repository.TicketRepository;
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

    private static final List<TicketStatus> COMPLETED_STATUSES = Arrays.asList(
        TicketStatus.COMPLETED, TicketStatus.CLOSED, TicketStatus.CANCELLED
    );

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void checkAndUpdateAlerts() {
        log.info("开始执行时效预警检查任务: {}", LocalDateTime.now());
        
        LocalDateTime now = LocalDateTime.now();
        
        updateYellowWarnings(now);
        
        updateRedWarnings(now);
        
        updateOverdueStatus(now);
        
        log.info("时效预警检查任务完成");
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
                    "时效黄牌警告", null, AlertLevel.YELLOW_WARNING.name(), null);
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
                    "时效红牌警告", null, AlertLevel.RED_WARNING.name(), null);
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
                ticketRepository.save(ticket);
                log.warn("工单 {} 已逾期", ticket.getTicketNumber());
                
                auditService.logOperation("OVERDUE", "Ticket", ticket.getId(),
                    "工单逾期", null, AlertLevel.OVERDUE.name(), null);
            }
        }
    }

    @Transactional
    public void updateRemainingHours(Ticket ticket) {
        if (ticket.getDueTime() == null || ticket.getCreatedAt() == null) {
            return;
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(ticket.getDueTime())) {
            ticket.setRemainingHours(0);
        } else {
            long hours = java.time.Duration.between(now, ticket.getDueTime()).toHours();
            ticket.setRemainingHours((int) hours);
        }
        
        ticketRepository.save(ticket);
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
