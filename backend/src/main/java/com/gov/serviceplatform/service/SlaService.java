package com.gov.serviceplatform.service;

import com.gov.serviceplatform.entity.Department;
import com.gov.serviceplatform.entity.SlaConfig;
import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.enums.AlertLevel;
import com.gov.serviceplatform.repository.SlaConfigRepository;
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
public class SlaService {

    private final SlaConfigRepository slaConfigRepository;
    private final WorkCalendarService workCalendarService;
    private final AuditService auditService;

    private static final int DEFAULT_PROCESSING_HOURS = 72;
    private static final double DEFAULT_YELLOW_WARNING_RATIO = 0.75;
    private static final double DEFAULT_RED_WARNING_RATIO = 0.9;
    private static final boolean DEFAULT_USE_WORK_DAYS = true;

    @Transactional
    public void calculateAndSetSla(Ticket ticket) {
        SlaConfig config = findMatchingSlaConfig(ticket);
        
        int processingHours = config != null ? config.getProcessingHours() : DEFAULT_PROCESSING_HOURS;
        boolean useWorkDays = config != null ? config.getUseWorkDays() : DEFAULT_USE_WORK_DAYS;
        double yellowWarningRatio = config != null ? config.getYellowWarningRatio() : DEFAULT_YELLOW_WARNING_RATIO;
        double redWarningRatio = config != null ? config.getRedWarningRatio() : DEFAULT_RED_WARNING_RATIO;
        
        LocalDateTime now = LocalDateTime.now();
        
        ticket.setProcessingHours(processingHours);
        
        LocalDateTime dueTime = workCalendarService.calculateDueTime(now, processingHours, useWorkDays);
        ticket.setDueTime(dueTime);
        
        int yellowHours = (int) (processingHours * yellowWarningRatio);
        LocalDateTime yellowWarningTime = workCalendarService.calculateDueTime(now, yellowHours, useWorkDays);
        ticket.setYellowWarningTime(yellowWarningTime);
        
        int redHours = (int) (processingHours * redWarningRatio);
        LocalDateTime redWarningTime = workCalendarService.calculateDueTime(now, redHours, useWorkDays);
        ticket.setRedWarningTime(redWarningTime);
        
        ticket.setRemainingHours(processingHours);
        ticket.setAlertLevel(AlertLevel.NORMAL);
        
        log.info("工单 {} SLA计算完成: 处理时限={}小时, 是否工作日={}, 到期时间={}",
            ticket.getTicketNumber(), processingHours, useWorkDays, dueTime);
        
        auditService.logOperation("SLA_CALCULATE", "Ticket", ticket.getId(),
            String.format("SLA计算: 处理时限=%d小时, 到期时间=%s", processingHours, dueTime),
            null, String.format("processingHours=%d, dueTime=%s", processingHours, dueTime),
            null);
    }

    private SlaConfig findMatchingSlaConfig(Ticket ticket) {
        String category = ticket.getCategory();
        String subCategory = ticket.getSubCategory();
        Department department = ticket.getCurrentDepartment();
        
        List<SlaConfig> configs = slaConfigRepository.findMatchingConfigs(category, subCategory, department);
        
        if (!configs.isEmpty()) {
            return configs.get(0);
        }
        
        if (department != null) {
            Optional<SlaConfig> deptConfig = slaConfigRepository.findByDepartmentIdAndIsActiveTrue(department.getId());
            if (deptConfig.isPresent()) {
                return deptConfig.get();
            }
        }
        
        return null;
    }

    public int getAcceptTimeoutHours(Ticket ticket) {
        SlaConfig config = findMatchingSlaConfig(ticket);
        if (config != null && config.getAcceptTimeoutHours() != null) {
            return config.getAcceptTimeoutHours();
        }
        return 4;
    }

    public void updateRemainingHours(Ticket ticket) {
        if (ticket.getDueTime() == null || ticket.getCreatedAt() == null) {
            return;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        if (now.isAfter(ticket.getDueTime())) {
            ticket.setRemainingHours(0);
        } else {
            SlaConfig config = findMatchingSlaConfig(ticket);
            boolean useWorkDays = config != null ? config.getUseWorkDays() : DEFAULT_USE_WORK_DAYS;
            
            if (useWorkDays) {
                int remainingHours = workCalendarService.calculateRemainingWorkHours(now, ticket.getDueTime());
                ticket.setRemainingHours(remainingHours);
            } else {
                long hours = java.time.Duration.between(now, ticket.getDueTime()).toHours();
                ticket.setRemainingHours((int) hours);
            }
        }
    }

    public AlertLevel calculateCurrentAlertLevel(Ticket ticket) {
        if (ticket.getDueTime() == null) {
            return AlertLevel.NORMAL;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        if (now.isAfter(ticket.getDueTime())) {
            return AlertLevel.OVERDUE;
        }
        
        if (ticket.getRedWarningTime() != null && now.isAfter(ticket.getRedWarningTime())) {
            return AlertLevel.RED_WARNING;
        }
        
        if (ticket.getYellowWarningTime() != null && now.isAfter(ticket.getYellowWarningTime())) {
            return AlertLevel.YELLOW_WARNING;
        }
        
        return AlertLevel.NORMAL;
    }

    public long calculateElapsedWorkHours(Ticket ticket) {
        if (ticket.getCreatedAt() == null) {
            return 0;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        SlaConfig config = findMatchingSlaConfig(ticket);
        boolean useWorkDays = config != null ? config.getUseWorkDays() : DEFAULT_USE_WORK_DAYS;
        
        if (useWorkDays) {
            LocalDateTime dueTimeIfZero = workCalendarService.calculateDueTime(ticket.getCreatedAt(), 0, true);
            LocalDateTime dueTimeIfMax = workCalendarService.calculateDueTime(ticket.getCreatedAt(), Integer.MAX_VALUE, true);
            
            return java.time.Duration.between(ticket.getCreatedAt(), now).toHours();
        } else {
            return java.time.Duration.between(ticket.getCreatedAt(), now).toHours();
        }
    }

    public List<SlaConfig> getAllActiveSlaConfigs() {
        return slaConfigRepository.findByIsActiveTrue();
    }

    public SlaConfig createSlaConfig(SlaConfig config) {
        config.setIsActive(true);
        return slaConfigRepository.save(config);
    }

    public void deactivateSlaConfig(Long id) {
        slaConfigRepository.findById(id).ifPresent(config -> {
            config.setIsActive(false);
            slaConfigRepository.save(config);
        });
    }
}
