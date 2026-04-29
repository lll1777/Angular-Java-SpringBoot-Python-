package com.gov.serviceplatform.service;

import com.gov.serviceplatform.entity.SlaConfig;
import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.repository.SlaConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlaService {

    private final SlaConfigRepository slaConfigRepository;
    private final WorkCalendarService workCalendarService;

    private static final int DEFAULT_PROCESSING_HOURS = 72;
    private static final int DEFAULT_CLAIM_HOURS = 4;
    private static final double DEFAULT_YELLOW_PERCENT = 0.75;
    private static final double DEFAULT_RED_PERCENT = 0.90;

    public SlaConfig getSlaConfig(Ticket ticket) {
        if (ticket.getCategory() != null && ticket.getSubCategory() != null) {
            Optional<SlaConfig> configOpt = slaConfigRepository
                .findByCategoryAndSubCategoryAndIsActiveTrue(ticket.getCategory(), ticket.getSubCategory());
            if (configOpt.isPresent()) {
                return configOpt.get();
            }
        }

        if (ticket.getCategory() != null) {
            Optional<SlaConfig> configOpt = slaConfigRepository
                .findByCategoryAndIsActiveTrue(ticket.getCategory());
            if (configOpt.isPresent()) {
                return configOpt.get();
            }
        }

        return createDefaultSlaConfig(ticket);
    }

    private SlaConfig createDefaultSlaConfig(Ticket ticket) {
        SlaConfig config = new SlaConfig();
        config.setCategory(ticket.getCategory() != null ? ticket.getCategory() : "DEFAULT");
        config.setSubCategory(ticket.getSubCategory());
        config.setUseWorkDays(true);
        config.setProcessingHours(DEFAULT_PROCESSING_HOURS);
        config.setClaimHours(DEFAULT_CLAIM_HOURS);
        config.setYellowWarningPercent(DEFAULT_YELLOW_PERCENT);
        config.setRedWarningPercent(DEFAULT_RED_PERCENT);
        config.setIsUrgent(ticket.getIsUrgent());
        config.setUrgentMultiplier(0.5);
        config.setIsActive(true);
        return config;
    }

    public void calculateAndSetSlaTimes(Ticket ticket) {
        if (ticket.getCreatedAt() == null) {
            ticket.setCreatedAt(LocalDateTime.now());
        }

        SlaConfig config = getSlaConfig(ticket);
        
        int processingHours = config.getProcessingHours();
        if (Boolean.TRUE.equals(ticket.getIsUrgent()) && config.getUrgentMultiplier() != null) {
            processingHours = (int) (processingHours * config.getUrgentMultiplier());
        }
        
        ticket.setProcessingHours(processingHours);
        
        LocalDateTime startDateTime = workCalendarService.adjustToWorkDay(ticket.getCreatedAt());
        
        LocalDateTime dueTime;
        LocalDateTime yellowWarningTime;
        LocalDateTime redWarningTime;
        
        if (Boolean.TRUE.equals(config.getUseWorkDays())) {
            dueTime = workCalendarService.addWorkHours(startDateTime, processingHours);
            
            long yellowMinutes = (long) (processingHours * config.getYellowWarningPercent() * 60);
            yellowWarningTime = workCalendarService.addWorkHours(startDateTime, yellowMinutes / 60);
            
            long redMinutes = (long) (processingHours * config.getRedWarningPercent() * 60);
            redWarningTime = workCalendarService.addWorkHours(startDateTime, redMinutes / 60);
        } else {
            dueTime = startDateTime.plusHours(processingHours);
            
            double yellowHours = processingHours * config.getYellowWarningPercent();
            yellowWarningTime = startDateTime.plusHours((long) yellowHours);
            
            double redHours = processingHours * config.getRedWarningPercent();
            redWarningTime = startDateTime.plusHours((long) redHours);
        }
        
        ticket.setDueTime(dueTime);
        ticket.setYellowWarningTime(yellowWarningTime);
        ticket.setRedWarningTime(redWarningTime);
        
        updateRemainingHours(ticket);
        
        log.info("工单 {} SLA时间计算完成: 截止时间={}, 黄牌时间={}, 红牌时间={}",
            ticket.getTicketNumber(), dueTime, yellowWarningTime, redWarningTime);
    }

    public void updateRemainingHours(Ticket ticket) {
        if (ticket.getDueTime() == null) {
            ticket.setRemainingHours(0);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        
        if (now.isAfter(ticket.getDueTime())) {
            ticket.setRemainingHours(0);
        } else {
            SlaConfig config = getSlaConfig(ticket);
            
            long remainingHours;
            if (Boolean.TRUE.equals(config.getUseWorkDays())) {
                remainingHours = workCalendarService.calculateWorkHoursBetween(now, ticket.getDueTime());
            } else {
                remainingHours = java.time.Duration.between(now, ticket.getDueTime()).toHours();
            }
            
            ticket.setRemainingHours((int) Math.max(0, remainingHours));
        }
    }

    public LocalDateTime calculateDueTime(LocalDateTime startTime, int hours, boolean useWorkDays) {
        if (useWorkDays) {
            LocalDateTime adjustedStart = workCalendarService.adjustToWorkDay(startTime);
            return workCalendarService.addWorkHours(adjustedStart, hours);
        } else {
            return startTime.plusHours(hours);
        }
    }

    public int getProcessingHours(Ticket ticket) {
        SlaConfig config = getSlaConfig(ticket);
        int hours = config.getProcessingHours();
        
        if (Boolean.TRUE.equals(ticket.getIsUrgent()) && config.getUrgentMultiplier() != null) {
            hours = (int) (hours * config.getUrgentMultiplier());
        }
        
        return hours;
    }

    public int getClaimHours(Ticket ticket) {
        SlaConfig config = getSlaConfig(ticket);
        return config.getClaimHours() != null ? config.getClaimHours() : DEFAULT_CLAIM_HOURS;
    }

    public LocalDateTime calculateClaimDueTime(Ticket ticket) {
        LocalDateTime startTime = ticket.getCreatedAt();
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
        
        int claimHours = getClaimHours(ticket);
        
        return workCalendarService.addWorkHours(startTime, claimHours);
    }

    public void createDefaultSlaConfigs() {
        List<SlaConfig> existingConfigs = slaConfigRepository.findByIsActiveTrue();
        if (!existingConfigs.isEmpty()) {
            return;
        }

        SlaConfig general = new SlaConfig();
        general.setCategory("DEFAULT");
        general.setUseWorkDays(true);
        general.setProcessingHours(72);
        general.setClaimHours(4);
        general.setYellowWarningPercent(0.75);
        general.setRedWarningPercent(0.90);
        general.setIsActive(true);
        general.setPriority(1);
        slaConfigRepository.save(general);

        SlaConfig urgent = new SlaConfig();
        urgent.setCategory("URGENT");
        urgent.setUseWorkDays(true);
        urgent.setProcessingHours(24);
        urgent.setClaimHours(2);
        urgent.setYellowWarningPercent(0.75);
        urgent.setRedWarningPercent(0.90);
        urgent.setIsUrgent(true);
        urgent.setIsActive(true);
        urgent.setPriority(10);
        slaConfigRepository.save(urgent);

        SlaConfig environment = new SlaConfig();
        environment.setCategory("环境保护");
        environment.setUseWorkDays(true);
        environment.setProcessingHours(48);
        environment.setClaimHours(4);
        environment.setYellowWarningPercent(0.75);
        environment.setRedWarningPercent(0.90);
        environment.setIsActive(true);
        environment.setPriority(5);
        slaConfigRepository.save(environment);

        SlaConfig health = new SlaConfig();
        health.setCategory("医疗卫生");
        health.setUseWorkDays(true);
        health.setProcessingHours(24);
        health.setClaimHours(2);
        health.setYellowWarningPercent(0.75);
        health.setRedWarningPercent(0.90);
        health.setIsActive(true);
        health.setPriority(8);
        slaConfigRepository.save(health);

        log.info("创建了默认SLA配置");
    }
}
