package com.gov.serviceplatform.enums;

public enum UserRole {
    CITIZEN("市民"),
    DEPARTMENT_STAFF("部门承办人"),
    DEPARTMENT_HEAD("部门负责人"),
    MONITOR_STAFF("效能监察员"),
    ADMIN("系统管理员");

    private final String description;

    UserRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
