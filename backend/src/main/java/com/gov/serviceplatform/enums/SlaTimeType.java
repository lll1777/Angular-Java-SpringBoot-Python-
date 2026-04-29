package com.gov.serviceplatform.enums;

public enum SlaTimeType {
    
    WORK_DAY("工作日", "按工作日计算，每天8小时（含午休1.5小时）"),
    NATURAL_DAY("自然日", "按自然日计算，每天24小时"),
    WORK_HOUR("工作小时", "按工作小时计算，只算工作时间"),
    NATURAL_HOUR("自然小时", "按自然小时计算，连续计时");

    private final String description;
    private final String detail;

    SlaTimeType(String description, String detail) {
        this.description = description;
        this.detail = detail;
    }

    public String getDescription() {
        return description;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isWorkBased() {
        return this == WORK_DAY || this == WORK_HOUR;
    }

    public boolean isDayBased() {
        return this == WORK_DAY || this == NATURAL_DAY;
    }

    public boolean isHourBased() {
        return this == WORK_HOUR || this == NATURAL_HOUR;
    }
}
