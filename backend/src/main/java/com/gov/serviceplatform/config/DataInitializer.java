package com.gov.serviceplatform.config;

import com.gov.serviceplatform.service.SlaService;
import com.gov.serviceplatform.service.WorkCalendarService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final WorkCalendarService workCalendarService;
    private final SlaService slaService;

    @PostConstruct
    public void init() {
        log.info("开始初始化系统数据...");
        
        try {
            workCalendarService.init();
            log.info("工作日历初始化完成");
        } catch (Exception e) {
            log.error("工作日历初始化失败: {}", e.getMessage());
        }
        
        try {
            slaService.createDefaultSlaConfigs();
            log.info("SLA配置初始化完成");
        } catch (Exception e) {
            log.error("SLA配置初始化失败: {}", e.getMessage());
        }
        
        log.info("系统数据初始化完成");
    }
}
