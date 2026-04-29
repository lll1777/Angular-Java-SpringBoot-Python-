package com.gov.serviceplatform.enums;

public enum SlaWarningType {
    
    PERCENTAGE("百分比预警", "按处理时限的百分比计算预警时间"),
    REMAINING_HOURS("剩余小时预警", "按剩余工作小时数计算预警时间"),
    REMAINING_DAYS("剩余天数预警", "按剩余工作日数计算预警时间"),
    FIXED_TIME("固定时间预警", "按固定时间点触发预警");

    private final String description;

    SlaWarningType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
