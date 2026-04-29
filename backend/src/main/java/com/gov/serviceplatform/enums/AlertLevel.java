package com.gov.serviceplatform.enums;

public enum AlertLevel {
    NORMAL("正常"),
    YELLOW_WARNING("黄牌警告"),
    RED_WARNING("红牌警告"),
    OVERDUE("已逾期");

    private final String description;

    AlertLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
