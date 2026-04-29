package com.gov.serviceplatform.service;

import com.gov.serviceplatform.entity.SlaCalculationLog;
import com.gov.serviceplatform.entity.SlaConfig;
import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.enums.SlaTimeType;
import com.gov.serviceplatform.enums.SlaWarningType;
import com.gov.serviceplatform.repository.SlaCalculationLogRepository;
import com.gov.serviceplatform.repository.SlaConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlaService {

    private final SlaConfigRepository slaConfigRepository;
    private final SlaCalculationLogRepository slaCalculationLogRepository;
    private final WorkCalendarService workCalendarService;

    private static final int DEFAULT_PROCESSING_WORK_DAYS = 5;
    private static final int DEFAULT_PROCESSING_HOURS = 40;
    private static final int DEFAULT_CLAIM_HOURS = 4;
    private static final double DEFAULT_YELLOW_PERCENT = 0.75;
    private static final double DEFAULT_RED_PERCENT = 0.90;
    private static final int DEFAULT_WORK_HOURS_PER_DAY = 8;
    private static final LocalTime DEFAULT_WORK_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WORK_END = LocalTime.of(18, 0);
    private static final LocalTime DEFAULT_LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime DEFAULT_LUNCH_END = LocalTime.of(13, 30);

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
        config.setProcessingTimeType(SlaTimeType.WORK_DAY);
        config.setProcessingValue(DEFAULT_PROCESSING_WORK_DAYS);
        config.setClaimTimeType(SlaTimeType.WORK_HOUR);
        config.setClaimValue(DEFAULT_CLAIM_HOURS);
        config.setWarningType(SlaWarningType.PERCENTAGE);
        config.setYellowWarningPercent(DEFAULT_YELLOW_PERCENT);
        config.setRedWarningPercent(DEFAULT_RED_PERCENT);
        config.setIsUrgent(ticket.getIsUrgent());
        config.setUrgentMultiplier(0.5);
        config.setIsActive(true);
        config.setWorkHoursPerDay(DEFAULT_WORK_HOURS_PER_DAY);
        config.setWorkStartTime(DEFAULT_WORK_START);
        config.setWorkEndTime(DEFAULT_WORK_END);
        config.setLunchStartTime(DEFAULT_LUNCH_START);
        config.setLunchEndTime(DEFAULT_LUNCH_END);
        return config;
    }

    @Transactional
    public void calculateAndSetSlaTimes(Ticket ticket) {
        calculateAndSetSlaTimes(ticket, "工单创建", "INITIAL_CALCULATION");
    }

    @Transactional
    public void calculateAndSetSlaTimes(Ticket ticket, String reason, String triggerEvent) {
        if (ticket.getCreatedAt() == null) {
            ticket.setCreatedAt(LocalDateTime.now());
        }

        SlaConfig config = getSlaConfig(ticket);
        
        ticket.setSlaTimeType(config.getProcessingTimeType());
        ticket.setSlaTimeTypeDescription(config.getProcessingTimeType().getDescription());
        ticket.setSlaProcessingValue(config.getProcessingValue());
        
        int processingValue = config.getProcessingValue();
        SlaTimeType timeType = config.getProcessingTimeType();
        
        if (Boolean.TRUE.equals(ticket.getIsUrgent()) && config.getUrgentMultiplier() != null) {
            if (timeType.isDayBased()) {
                processingValue = (int) Math.max(1, processingValue * config.getUrgentMultiplier());
            } else {
                processingValue = (int) Math.max(1, processingValue * config.getUrgentMultiplier());
            }
        }
        
        ticket.setProcessingHours(timeType.isDayBased() 
            ? processingValue * (timeType == SlaTimeType.WORK_DAY ? DEFAULT_WORK_HOURS_PER_DAY : 24)
            : processingValue);

        LocalDateTime startTime = ticket.getCreatedAt();
        
        LocalDateTime adjustedStartTime = adjustStartTime(startTime, timeType, config);
        
        LocalDateTime previousDueTime = ticket.getDueTime();
        
        LocalDateTime dueTime = calculateDueTime(adjustedStartTime, processingValue, timeType, config);
        
        LocalDateTime yellowWarningTime = calculateYellowWarningTime(adjustedStartTime, dueTime, processingValue, config);
        LocalDateTime redWarningTime = calculateRedWarningTime(adjustedStartTime, dueTime, processingValue, config);
        
        LocalDateTime claimDueTime = calculateClaimDueTime(ticket, config);
        
        ticket.setDueTime(dueTime);
        ticket.setYellowWarningTime(yellowWarningTime);
        ticket.setRedWarningTime(redWarningTime);
        ticket.setClaimDueTime(claimDueTime);
        ticket.setSlaRecalculatedAt(LocalDateTime.now());
        
        updateRemainingHours(ticket, config);
        updateRemainingDays(ticket, config);
        
        logSlaCalculation(ticket, config, adjustedStartTime, dueTime, yellowWarningTime, redWarningTime, 
            claimDueTime, reason, triggerEvent, previousDueTime);
        
        log.info("工单 {} SLA时间计算完成: 类型={}, 处理值={}, 截止时间={}, 黄牌={}, 红牌={}",
            ticket.getTicketNumber(), timeType.getDescription(), processingValue, 
            dueTime, yellowWarningTime, redWarningTime);
    }

    private LocalDateTime adjustStartTime(LocalDateTime startTime, SlaTimeType timeType, SlaConfig config) {
        if (timeType.isWorkBased()) {
            return workCalendarService.adjustToWorkDay(startTime);
        }
        return startTime;
    }

    public LocalDateTime calculateDueTime(LocalDateTime startTime, int value, SlaTimeType timeType, SlaConfig config) {
        if (timeType == SlaTimeType.WORK_DAY) {
            int workHours = value * (config.getWorkHoursPerDay() != null ? config.getWorkHoursPerDay() : DEFAULT_WORK_HOURS_PER_DAY);
            return workCalendarService.addWorkHours(startTime, workHours);
        } else if (timeType == SlaTimeType.WORK_HOUR) {
            return workCalendarService.addWorkHours(startTime, value);
        } else if (timeType == SlaTimeType.NATURAL_DAY) {
            return startTime.plusDays(value);
        } else {
            return startTime.plusHours(value);
        }
    }

    private LocalDateTime calculateYellowWarningTime(LocalDateTime startTime, LocalDateTime dueTime, 
                                                      int processingValue, SlaConfig config) {
        SlaWarningType warningType = config.getWarningType();
        SlaTimeType timeType = config.getProcessingTimeType();
        
        if (warningType == SlaWarningType.PERCENTAGE) {
            double percent = config.getYellowWarningPercent() != null ? config.getYellowWarningPercent() : DEFAULT_YELLOW_PERCENT;
            
            if (timeType.isWorkBased()) {
                int totalHours = config.getProcessingHours();
                long warningHours = (long) (totalHours * percent);
                return workCalendarService.addWorkHours(startTime, warningHours);
            } else {
                if (timeType.isDayBased()) {
                    long warningDays = (long) (processingValue * percent);
                    return startTime.plusDays(warningDays);
                } else {
                    long warningHours = (long) (processingValue * percent);
                    return startTime.plusHours(warningHours);
                }
            }
        } else if (warningType == SlaWarningType.REMAINING_HOURS) {
            int remainingHours = config.getYellowWarningRemainingHours() != null 
                ? config.getYellowWarningRemainingHours() : 4;
            
            if (timeType.isWorkBased()) {
                return workCalendarService.subtractWorkHours(dueTime, remainingHours);
            } else {
                return dueTime.minusHours(remainingHours);
            }
        } else if (warningType == SlaWarningType.REMAINING_DAYS) {
            int remainingDays = config.getYellowWarningRemainingDays() != null 
                ? config.getYellowWarningRemainingDays() : 1;
            
            if (timeType.isWorkBased()) {
                int remainingHours = remainingDays * DEFAULT_WORK_HOURS_PER_DAY;
                return workCalendarService.subtractWorkHours(dueTime, remainingHours);
            } else {
                return dueTime.minusDays(remainingDays);
            }
        }
        
        return startTime.plusHours((long) (processingValue * DEFAULT_YELLOW_PERCENT));
    }

    private LocalDateTime calculateRedWarningTime(LocalDateTime startTime, LocalDateTime dueTime, 
                                                    int processingValue, SlaConfig config) {
        SlaWarningType warningType = config.getWarningType();
        SlaTimeType timeType = config.getProcessingTimeType();
        
        if (warningType == SlaWarningType.PERCENTAGE) {
            double percent = config.getRedWarningPercent() != null ? config.getRedWarningPercent() : DEFAULT_RED_PERCENT;
            
            if (timeType.isWorkBased()) {
                int totalHours = config.getProcessingHours();
                long warningHours = (long) (totalHours * percent);
                return workCalendarService.addWorkHours(startTime, warningHours);
            } else {
                if (timeType.isDayBased()) {
                    long warningDays = (long) (processingValue * percent);
                    return startTime.plusDays(warningDays);
                } else {
                    long warningHours = (long) (processingValue * percent);
                    return startTime.plusHours(warningHours);
                }
            }
        } else if (warningType == SlaWarningType.REMAINING_HOURS) {
            int remainingHours = config.getRedWarningRemainingHours() != null 
                ? config.getRedWarningRemainingHours() : 2;
            
            if (timeType.isWorkBased()) {
                return workCalendarService.subtractWorkHours(dueTime, remainingHours);
            } else {
                return dueTime.minusHours(remainingHours);
            }
        } else if (warningType == SlaWarningType.REMAINING_DAYS) {
            int remainingDays = config.getRedWarningRemainingDays() != null 
                ? config.getRedWarningRemainingDays() : 0;
            
            if (timeType.isWorkBased()) {
                int remainingHours = remainingDays * DEFAULT_WORK_HOURS_PER_DAY;
                return workCalendarService.subtractWorkHours(dueTime, remainingHours);
            } else {
                return dueTime.minusDays(remainingDays);
            }
        }
        
        return startTime.plusHours((long) (processingValue * DEFAULT_RED_PERCENT));
    }

    public LocalDateTime calculateClaimDueTime(Ticket ticket) {
        SlaConfig config = getSlaConfig(ticket);
        return calculateClaimDueTime(ticket, config);
    }

    private LocalDateTime calculateClaimDueTime(Ticket ticket, SlaConfig config) {
        LocalDateTime startTime = ticket.getCreatedAt();
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
        
        SlaTimeType claimTimeType = config.getClaimTimeType() != null 
            ? config.getClaimTimeType() : SlaTimeType.WORK_HOUR;
        int claimValue = config.getClaimValue() != null ? config.getClaimValue() : DEFAULT_CLAIM_HOURS;
        
        if (claimTimeType.isWorkBased()) {
            return workCalendarService.addWorkHours(startTime, 
                claimTimeType.isDayBased() ? claimValue * DEFAULT_WORK_HOURS_PER_DAY : claimValue);
        } else {
            return claimTimeType.isDayBased() 
                ? startTime.plusDays(claimValue) 
                : startTime.plusHours(claimValue);
        }
    }

    public void updateRemainingHours(Ticket ticket) {
        SlaConfig config = getSlaConfig(ticket);
        updateRemainingHours(ticket, config);
    }

    private void updateRemainingHours(Ticket ticket, SlaConfig config) {
        if (ticket.getDueTime() == null) {
            ticket.setRemainingHours(0);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        
        if (now.isAfter(ticket.getDueTime())) {
            ticket.setRemainingHours(0);
        } else {
            SlaTimeType timeType = config.getProcessingTimeType();
            long remainingHours;
            
            if (timeType.isWorkBased()) {
                remainingHours = workCalendarService.calculateWorkHoursBetween(now, ticket.getDueTime());
            } else {
                remainingHours = Duration.between(now, ticket.getDueTime()).toHours();
            }
            
            ticket.setRemainingHours((int) Math.max(0, remainingHours));
        }
    }

    private void updateRemainingDays(Ticket ticket, SlaConfig config) {
        if (ticket.getDueTime() == null) {
            ticket.setRemainingDays(0);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        
        if (now.isAfter(ticket.getDueTime())) {
            ticket.setRemainingDays(0);
        } else {
            SlaTimeType timeType = config.getProcessingTimeType();
            long remainingDays;
            
            if (timeType.isWorkBased()) {
                long workHours = workCalendarService.calculateWorkHoursBetween(now, ticket.getDueTime());
                remainingDays = (workHours + DEFAULT_WORK_HOURS_PER_DAY - 1) / DEFAULT_WORK_HOURS_PER_DAY;
            } else {
                remainingDays = Duration.between(now, ticket.getDueTime()).toDays();
            }
            
            ticket.setRemainingDays((int) Math.max(0, remainingDays));
        }
    }

    @Transactional
    public void recalculateSla(Ticket ticket, String reason, String triggerEvent) {
        LocalDateTime previousDueTime = ticket.getDueTime();
        
        calculateAndSetSlaTimes(ticket, reason, triggerEvent);
        
        log.info("工单 {} SLA重新计算: 原因={}, 触发事件={}, 原截止时间={}, 新截止时间={}",
            ticket.getTicketNumber(), reason, triggerEvent, previousDueTime, ticket.getDueTime());
    }

    private void logSlaCalculation(Ticket ticket, SlaConfig config, LocalDateTime startTime, 
                                    LocalDateTime dueTime, LocalDateTime yellowWarningTime, 
                                    LocalDateTime redWarningTime, LocalDateTime claimDueTime,
                                    String reason, String triggerEvent, LocalDateTime previousDueTime) {
        try {
            SlaCalculationLog logEntry = new SlaCalculationLog();
            logEntry.setTicket(ticket);
            logEntry.setTicketNumber(ticket.getTicketNumber());
            logEntry.setTimeType(config.getProcessingTimeType());
            logEntry.setTimeTypeDescription(config.getProcessingTimeType().getDescription());
            logEntry.setProcessingValue(config.getProcessingValue());
            logEntry.setWorkHoursPerDay(config.getWorkHoursPerDay());
            logEntry.setStartTime(startTime);
            logEntry.setCalculatedDueTime(dueTime);
            logEntry.setYellowWarningTime(yellowWarningTime);
            logEntry.setRedWarningTime(redWarningTime);
            logEntry.setClaimDueTime(claimDueTime);
            logEntry.setCalculationReason(reason);
            logEntry.setSlaConfigId(config.getId());
            logEntry.setSlaConfigCategory(config.getCategory());
            logEntry.setTriggerEvent(triggerEvent);
            logEntry.setPreviousDueTime(previousDueTime);
            logEntry.setIsUrgent(ticket.getIsUrgent());
            logEntry.setUrgentMultiplier(config.getUrgentMultiplier());
            
            StringBuilder detail = new StringBuilder();
            detail.append("SLA类型: ").append(config.getProcessingTimeType().getDescription()).append("\n");
            detail.append("处理时限: ").append(config.getProcessingValue());
            if (config.getProcessingTimeType().isDayBased()) {
                detail.append("个").append(config.getProcessingTimeType() == SlaTimeType.WORK_DAY ? "工作" : "自然").append("日");
            } else {
                detail.append("个").append(config.getProcessingTimeType() == SlaTimeType.WORK_HOUR ? "工作" : "自然").append("小时");
            }
            detail.append("\n");
            detail.append("工作时间: ").append(config.getWorkStartTime()).append(" - ").append(config.getWorkEndTime()).append("\n");
            detail.append("午休时间: ").append(config.getLunchStartTime()).append(" - ").append(config.getLunchEndTime()).append("\n");
            detail.append("预警类型: ").append(config.getWarningType().getDescription()).append("\n");
            detail.append("黄牌阈值: ").append(formatWarningThreshold(config, "YELLOW")).append("\n");
            detail.append("红牌阈值: ").append(formatWarningThreshold(config, "RED"));
            
            logEntry.setCalculationDetail(detail.toString());
            
            slaCalculationLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("记录SLA计算日志失败: {}", e.getMessage());
        }
    }

    private String formatWarningThreshold(SlaConfig config, String level) {
        SlaWarningType type = config.getWarningType();
        if (type == SlaWarningType.PERCENTAGE) {
            double percent = "YELLOW".equals(level) 
                ? config.getYellowWarningPercent() 
                : config.getRedWarningPercent();
            return String.format("%.0f%%", percent * 100);
        } else if (type == SlaWarningType.REMAINING_HOURS) {
            int hours = "YELLOW".equals(level)
                ? config.getYellowWarningRemainingHours()
                : config.getRedWarningRemainingHours();
            return "剩余" + hours + "小时";
        } else if (type == SlaWarningType.REMAINING_DAYS) {
            int days = "YELLOW".equals(level)
                ? config.getYellowWarningRemainingDays()
                : config.getRedWarningRemainingDays();
            return "剩余" + days + "天";
        }
        return "";
    }

    public List<SlaCalculationLog> getSlaCalculationHistory(Long ticketId) {
        return slaCalculationLogRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    @Transactional
    public void createDefaultSlaConfigs() {
        List<SlaConfig> existingConfigs = slaConfigRepository.findByIsActiveTrue();
        if (!existingConfigs.isEmpty()) {
            return;
        }

        log.info("创建默认SLA配置...");

        SlaConfig general = new SlaConfig();
        general.setCategory("DEFAULT");
        general.setDescription("默认配置 - 5个工作日办结");
        general.setProcessingTimeType(SlaTimeType.WORK_DAY);
        general.setProcessingValue(5);
        general.setClaimTimeType(SlaTimeType.WORK_HOUR);
        general.setClaimValue(4);
        general.setWarningType(SlaWarningType.PERCENTAGE);
        general.setYellowWarningPercent(0.75);
        general.setRedWarningPercent(0.90);
        general.setIsActive(true);
        general.setPriority(1);
        general.setWorkHoursPerDay(8);
        slaConfigRepository.save(general);

        SlaConfig urgent = new SlaConfig();
        urgent.setCategory("URGENT");
        urgent.setDescription("紧急配置 - 2个工作日办结");
        urgent.setProcessingTimeType(SlaTimeType.WORK_DAY);
        urgent.setProcessingValue(2);
        urgent.setClaimTimeType(SlaTimeType.WORK_HOUR);
        urgent.setClaimValue(2);
        urgent.setWarningType(SlaWarningType.PERCENTAGE);
        urgent.setYellowWarningPercent(0.75);
        urgent.setRedWarningPercent(0.90);
        urgent.setIsUrgent(true);
        urgent.setIsActive(true);
        urgent.setPriority(10);
        slaConfigRepository.save(urgent);

        SlaConfig environment = new SlaConfig();
        environment.setCategory("环境保护");
        environment.setDescription("环境保护 - 3个工作日办结");
        environment.setProcessingTimeType(SlaTimeType.WORK_DAY);
        environment.setProcessingValue(3);
        environment.setClaimTimeType(SlaTimeType.WORK_HOUR);
        environment.setClaimValue(4);
        environment.setWarningType(SlaWarningType.REMAINING_DAYS);
        environment.setYellowWarningRemainingDays(1);
        environment.setRedWarningRemainingDays(0);
        environment.setIsActive(true);
        environment.setPriority(5);
        slaConfigRepository.save(environment);

        SlaConfig health = new SlaConfig();
        health.setCategory("医疗卫生");
        health.setDescription("医疗卫生 - 1个工作日办结");
        health.setProcessingTimeType(SlaTimeType.WORK_DAY);
        health.setProcessingValue(1);
        health.setClaimTimeType(SlaTimeType.WORK_HOUR);
        health.setClaimValue(2);
        health.setWarningType(SlaWarningType.REMAINING_HOURS);
        health.setYellowWarningRemainingHours(4);
        health.setRedWarningRemainingHours(2);
        health.setIsActive(true);
        health.setPriority(8);
        slaConfigRepository.save(health);

        SlaConfig naturalDayTest = new SlaConfig();
        naturalDayTest.setCategory("自然日测试");
        naturalDayTest.setDescription("按自然日计算 - 3个自然日办结");
        naturalDayTest.setProcessingTimeType(SlaTimeType.NATURAL_DAY);
        naturalDayTest.setProcessingValue(3);
        naturalDayTest.setClaimTimeType(SlaTimeType.NATURAL_HOUR);
        naturalDayTest.setClaimValue(8);
        naturalDayTest.setWarningType(SlaWarningType.PERCENTAGE);
        naturalDayTest.setYellowWarningPercent(0.66);
        naturalDayTest.setRedWarningPercent(0.83);
        naturalDayTest.setIsActive(true);
        naturalDayTest.setPriority(2);
        slaConfigRepository.save(naturalDayTest);

        log.info("创建了5个默认SLA配置");
    }

    public String getSlaDisplay(Ticket ticket) {
        if (ticket.getSlaTimeType() == null) {
            return "未配置";
        }
        String valueStr = ticket.getSlaProcessingValue() != null 
            ? String.valueOf(ticket.getSlaProcessingValue()) : "0";
        return valueStr + "个" + ticket.getSlaTimeType().getDescription();
    }
}
