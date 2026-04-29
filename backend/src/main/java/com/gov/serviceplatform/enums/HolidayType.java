package com.gov.serviceplatform.enums;

public enum HolidayType {
    NATIONAL_HOLIDAY("法定节假日"),
    WEEKEND("周末"),
    SPECIAL_HOLIDAY("特殊假日"),
    WORKING_WEEKEND("调休工作日");

    private final String description;

    HolidayType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
