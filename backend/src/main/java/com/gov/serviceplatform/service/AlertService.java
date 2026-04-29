package com.gov.serviceplatform.service;

import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.enums.AlertLevel;
import com.gov.serviceplatform.enums.SlaTimeType;
import com.gov.serviceplatform.enums.TicketStatus;
import com.gov.serviceplatform.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final TicketRepository ticketRepository;
    private final AuditService auditService;
    private final SlaService slaService;
    private final WorkCalendarService workCalendarService;

    private static final List<TicketStatus> COMPLETED_STATUSES = Arrays.asList(
        TicketStatus.COMPLETED, TicketStatus.CLOSED, TicketStatus.CANCELLED
    );

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void checkAndUpdateAlerts() {
        log.info("开始执行时效预警检查任务: {}", LocalDateTime.now());
        
        LocalDateTime now = LocalDateTime.now();
        
        checkClaimTimeouts(now);
        
        updateYellowWarnings(now);
        
        updateRedWarnings(now);
        
        updateOverdueStatus(now);
        
        updateRemainingTimeForActiveTickets();
        
        log.info("时效预警检查任务完成");
    }

    @Transactional
    public void checkAndUpdateAlertsForTicket(Ticket ticket) {
        LocalDateTime now = LocalDateTime.now();
        
        checkSingleTicketClaimTimeout(ticket, now);
        
        checkSingleTicketWarnings(ticket, now);
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
            checkSingleTicketClaimTimeout(ticket, now);
        }
    }

    private void checkSingleTicketClaimTimeout(Ticket ticket, LocalDateTime now) {
        if (ticket.getClaimDueTime() == null) {
            LocalDateTime claimDueTime = slaService.calculateClaimDueTime(ticket);
            ticket.setClaimDueTime(claimDueTime);
            ticketRepository.save(ticket);
        }
        
        if (now.isAfter(ticket.getClaimDueTime())) {
            log.warn("工单 {} 超时未认领，触发自动处理", ticket.getTicketNumber());
            
            String detail = buildSlaTimeoutDetail(ticket, "认领超时", 
                ticket.getClaimDueTime(), now, ticket.getSlaTimeType());
            
            auditService.logOperation("CLAIM_TIMEOUT", "Ticket", ticket.getId(),
                detail, 
                "等待认领", "超时自动处理", 
                null);
        }
    }

    private void updateYellowWarnings(LocalDateTime now) {
        List<Ticket> tickets = ticketRepository.findTicketsNeedingYellowWarning(
            now, COMPLETED_STATUSES, AlertLevel.NORMAL
        );
        
        for (Ticket ticket : tickets) {
            if (ticket.getYellowWarningTime() != null && 
                now.isAfter(ticket.getYellowWarningTime()) &&
                ticket.getAlertLevel() == AlertLevel.NORMAL) {
                
                ticket.setAlertLevel(AlertLevel.YELLOW_WARNING);
                ticketRepository.save(ticket);
                
                log.warn("工单 {} 触发黄牌预警", ticket.getTicketNumber());
                
                String detail = buildSlaWarningDetail(ticket, "黄牌预警", 
                    ticket.getYellowWarningTime(), ticket.getDueTime(), now);
                
                auditService.logOperation("YELLOW_WARNING", "Ticket", ticket.getId(),
                    detail, 
                    AlertLevel.NORMAL.name(), AlertLevel.YELLOW_WARNING.name(), 
                    null);
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
                
                String oldLevel = ticket.getAlertLevel() != null ? ticket.getAlertLevel().name() : "NORMAL";
                
                ticket.setAlertLevel(AlertLevel.RED_WARNING);
                ticketRepository.save(ticket);
                
                log.warn("工单 {} 触发红牌预警", ticket.getTicketNumber());
                
                String detail = buildSlaWarningDetail(ticket, "红牌预警", 
                    ticket.getRedWarningTime(), ticket.getDueTime(), now);
                
                auditService.logOperation("RED_WARNING", "Ticket", ticket.getId(),
                    detail, 
                    oldLevel, AlertLevel.RED_WARNING.name(), 
                    null);
            }
        }
    }

    private void updateOverdueStatus(LocalDateTime now) {
        List<Ticket> tickets = ticketRepository.findOverdueTickets(now, COMPLETED_STATUSES);
        
        for (Ticket ticket : tickets) {
            if (ticket.getDueTime() != null && 
                now.isAfter(ticket.getDueTime()) &&
                ticket.getAlertLevel() != AlertLevel.OVERDUE) {
                
                String oldLevel = ticket.getAlertLevel() != null ? ticket.getAlertLevel().name() : "NORMAL";
                
                ticket.setAlertLevel(AlertLevel.OVERDUE);
                ticket.setRemainingHours(0);
                ticket.setRemainingDays(0);
                ticketRepository.save(ticket);
                
                log.warn("工单 {} 已逾期", ticket.getTicketNumber());
                
                String detail = buildSlaOverdueDetail(ticket, now);
                
                auditService.logOperation("OVERDUE", "Ticket", ticket.getId(),
                    detail, 
                    oldLevel, AlertLevel.OVERDUE.name(), 
                    null);
            }
        }
    }

    private void checkSingleTicketWarnings(Ticket ticket, LocalDateTime now) {
        if (COMPLETED_STATUSES.contains(ticket.getStatus())) {
            return;
        }
        
        if (ticket.getAlertLevel() == AlertLevel.NORMAL && 
            ticket.getYellowWarningTime() != null && 
            now.isAfter(ticket.getYellowWarningTime())) {
            
            ticket.setAlertLevel(AlertLevel.YELLOW_WARNING);
            ticketRepository.save(ticket);
            
            String detail = buildSlaWarningDetail(ticket, "黄牌预警", 
                ticket.getYellowWarningTime(), ticket.getDueTime(), now);
            
            auditService.logOperation("YELLOW_WARNING", "Ticket", ticket.getId(),
                detail, 
                AlertLevel.NORMAL.name(), AlertLevel.YELLOW_WARNING.name(), 
                null);
        }
        
        if ((ticket.getAlertLevel() == AlertLevel.NORMAL || 
             ticket.getAlertLevel() == AlertLevel.YELLOW_WARNING) &&
            ticket.getRedWarningTime() != null && 
            now.isAfter(ticket.getRedWarningTime()) &&
            ticket.getAlertLevel() != AlertLevel.RED_WARNING &&
            ticket.getAlertLevel() != AlertLevel.OVERDUE) {
            
            String oldLevel = ticket.getAlertLevel() != null ? ticket.getAlertLevel().name() : "NORMAL";
            
            ticket.setAlertLevel(AlertLevel.RED_WARNING);
            ticketRepository.save(ticket);
            
            String detail = buildSlaWarningDetail(ticket, "红牌预警", 
                ticket.getRedWarningTime(), ticket.getDueTime(), now);
            
            auditService.logOperation("RED_WARNING", "Ticket", ticket.getId(),
                detail, 
                oldLevel, AlertLevel.RED_WARNING.name(), 
                null);
        }
        
        if (ticket.getAlertLevel() != AlertLevel.OVERDUE &&
            ticket.getDueTime() != null && 
            now.isAfter(ticket.getDueTime())) {
            
            String oldLevel = ticket.getAlertLevel() != null ? ticket.getAlertLevel().name() : "NORMAL";
            
            ticket.setAlertLevel(AlertLevel.OVERDUE);
            ticket.setRemainingHours(0);
            ticket.setRemainingDays(0);
            ticketRepository.save(ticket);
            
            String detail = buildSlaOverdueDetail(ticket, now);
            
            auditService.logOperation("OVERDUE", "Ticket", ticket.getId(),
                detail, 
                oldLevel, AlertLevel.OVERDUE.name(), 
                null);
        }
    }

    private void updateRemainingTimeForActiveTickets() {
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

    private String buildSlaWarningDetail(Ticket ticket, String warningType,
                                          LocalDateTime warningTime, LocalDateTime dueTime,
                                          LocalDateTime now) {
        StringBuilder detail = new StringBuilder();
        detail.append(warningType).append("触发\n");
        
        if (ticket.getSlaTimeType() != null) {
            detail.append("SLA类型: ").append(ticket.getSlaTimeType().getDescription()).append("\n");
            detail.append("处理时限: ").append(ticket.getSlaProcessingValue() != null 
                ? ticket.getSlaProcessingValue() : 0)
                .append("个").append(ticket.getSlaTimeType().getDescription()).append("\n");
        }
        
        detail.append("预警时间: ").append(formatTime(warningTime)).append("\n");
        detail.append("截止时间: ").append(formatTime(dueTime)).append("\n");
        detail.append("当前时间: ").append(formatTime(now)).append("\n");
        
        if (ticket.getRemainingHours() != null) {
            detail.append("剩余小时: ").append(ticket.getRemainingHours());
        }
        if (ticket.getRemainingDays() != null) {
            detail.append(", 剩余天数: ").append(ticket.getRemainingDays());
        }
        
        if (ticket.getSlaTimeType() != null && ticket.getSlaTimeType().isWorkBased()) {
            int workDays = workCalendarService.countWorkDaysBetween(
                now.toLocalDate(), 
                dueTime != null ? dueTime.toLocalDate() : now.toLocalDate()
            );
            detail.append("\n剩余工作日: ").append(workDays);
        }
        
        return detail.toString();
    }

    private String buildSlaOverdueDetail(Ticket ticket, LocalDateTime now) {
        StringBuilder detail = new StringBuilder();
        detail.append("工单逾期\n");
        
        if (ticket.getSlaTimeType() != null) {
            detail.append("SLA类型: ").append(ticket.getSlaTimeType().getDescription()).append("\n");
            detail.append("处理时限: ").append(ticket.getSlaProcessingValue() != null 
                ? ticket.getSlaProcessingValue() : 0)
                .append("个").append(ticket.getSlaTimeType().getDescription()).append("\n");
        }
        
        detail.append("应完成时间: ").append(formatTime(ticket.getDueTime())).append("\n");
        detail.append("当前时间: ").append(formatTime(now)).append("\n");
        
        if (ticket.getDueTime() != null) {
            long overdueMinutes;
            if (ticket.getSlaTimeType() != null && ticket.getSlaTimeType().isWorkBased()) {
                overdueMinutes = workCalendarService.calculateWorkMinutesBetween(ticket.getDueTime(), now);
            } else {
                overdueMinutes = java.time.Duration.between(ticket.getDueTime(), now).toMinutes();
            }
            detail.append("逾期时长: ").append(formatDuration(overdueMinutes));
        }
        
        return detail.toString();
    }

    private String buildSlaTimeoutDetail(Ticket ticket, String timeoutType,
                                          LocalDateTime timeoutTime, LocalDateTime now,
                                          SlaTimeType timeType) {
        StringBuilder detail = new StringBuilder();
        detail.append(timeoutType).append("\n");
        detail.append("超时时限: ").append(formatTime(timeoutTime)).append("\n");
        detail.append("当前时间: ").append(formatTime(now)).append("\n");
        
        if (timeoutTime != null) {
            long overdueMinutes;
            if (timeType != null && timeType.isWorkBased()) {
                overdueMinutes = workCalendarService.calculateWorkMinutesBetween(timeoutTime, now);
            } else {
                overdueMinutes = java.time.Duration.between(timeoutTime, now).toMinutes();
            }
            detail.append("超时时长: ").append(formatDuration(overdueMinutes));
        }
        
        return detail.toString();
    }

    private String formatTime(LocalDateTime time) {
        if (time == null) {
            return "未设置";
        }
        return time.format(FORMATTER);
    }

    private String formatDuration(long minutes) {
        if (minutes < 60) {
            return minutes + "分钟";
        }
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (hours < 24) {
            return hours + "小时" + (remainingMinutes > 0 ? remainingMinutes + "分钟" : "");
        }
        long days = hours / 24;
        long remainingHours = hours % 24;
        return days + "天" + (remainingHours > 0 ? remainingHours + "小时" : "");
    }

    @Transactional
    public void recalculateSlaForTicket(Ticket ticket) {
        log.info("重新计算工单 {} 的SLA时间", ticket.getTicketNumber());
        
        LocalDateTime previousDueTime = ticket.getDueTime();
        AlertLevel previousAlertLevel = ticket.getAlertLevel();
        
        slaService.recalculateSla(ticket, "手动重新计算", "MANUAL_RECALCULATION");
        
        ticket.setAlertLevel(AlertLevel.NORMAL);
        ticketRepository.save(ticket);
        
        StringBuilder detail = new StringBuilder();
        detail.append("SLA重新计算\n");
        detail.append("原截止时间: ").append(formatTime(previousDueTime)).append("\n");
        detail.append("新截止时间: ").append(formatTime(ticket.getDueTime())).append("\n");
        detail.append("原预警级别: ").append(previousAlertLevel != null ? previousAlertLevel.name() : "无").append("\n");
        detail.append("新预警级别: NORMAL");
        
        if (ticket.getSlaTimeType() != null) {
            detail.append("\nSLA类型: ").append(ticket.getSlaTimeType().getDescription());
            detail.append("\n处理时限: ").append(ticket.getSlaProcessingValue() != null 
                ? ticket.getSlaProcessingValue() : 0)
                .append("个").append(ticket.getSlaTimeType().getDescription());
        }
        
        auditService.logOperation(
            "SLA_RECALCULATE", 
            "Ticket", 
            ticket.getId(),
            detail.toString(),
            previousDueTime != null ? previousDueTime.toString() : null,
            ticket.getDueTime() != null ? ticket.getDueTime().toString() : null,
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

    public String getSlaDisplay(Ticket ticket) {
        return slaService.getSlaDisplay(ticket);
    }
}
