package com.gov.serviceplatform.enums;

public enum TicketStatus {
    SUBMITTED("已提交"),
    ASSIGNED("已派单"),
    ACCEPTED("已接单"),
    IN_PROGRESS("办理中"),
    TRANSFERRED("已转办"),
    COOPERATING("协办中"),
    RETURNED("已退回"),
    PENDING_REVIEW("待审核"),
    COMPLETED("已办结"),
    VISITING("回访中"),
    CLOSED("已关闭"),
    CANCELLED("已取消");

    private final String description;

    TicketStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
