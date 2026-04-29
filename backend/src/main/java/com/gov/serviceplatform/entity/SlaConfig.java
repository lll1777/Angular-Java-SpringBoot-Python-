package com.gov.serviceplatform.entity;

import com.gov.serviceplatform.enums.SlaTimeType;
import com.gov.serviceplatform.enums.SlaWarningType;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sla_config")
public class SlaConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(length = 100)
    private String subCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SlaTimeType processingTimeType = SlaTimeType.WORK_DAY;

    @Column(name = "processing_value", nullable = false)
    private Integer processingValue = 5;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SlaTimeType claimTimeType = SlaTimeType.WORK_HOUR;

    @Column(name = "claim_value")
    private Integer claimValue = 4;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SlaWarningType warningType = SlaWarningType.PERCENTAGE;

    @Column(name = "yellow_warning_percent")
    private Double yellowWarningPercent = 0.75;

    @Column(name = "red_warning_percent")
    private Double redWarningPercent = 0.90;

    @Column(name = "yellow_warning_remaining_hours")
    private Integer yellowWarningRemainingHours;

    @Column(name = "red_warning_remaining_hours")
    private Integer redWarningRemainingHours;

    @Column(name = "yellow_warning_remaining_days")
    private Integer yellowWarningRemainingDays;

    @Column(name = "red_warning_remaining_days")
    private Integer redWarningRemainingDays;

    @Column(name = "is_urgent")
    private Boolean isUrgent = false;

    @Column(name = "urgent_multiplier")
    private Double urgentMultiplier = 0.5;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "priority")
    private Integer priority = 1;

    @Column(length = 500)
    private String description;

    @Column(name = "work_hours_per_day")
    private Integer workHoursPerDay = 8;

    @Column(name = "work_start_time")
    private java.time.LocalTime workStartTime = java.time.LocalTime.of(9, 0);

    @Column(name = "work_end_time")
    private java.time.LocalTime workEndTime = java.time.LocalTime.of(18, 0);

    @Column(name = "lunch_start_time")
    private java.time.LocalTime lunchStartTime = java.time.LocalTime.of(12, 0);

    @Column(name = "lunch_end_time")
    private java.time.LocalTime lunchEndTime = java.time.LocalTime.of(13, 30);

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public int getProcessingHours() {
        if (processingTimeType.isDayBased()) {
            if (processingTimeType == SlaTimeType.WORK_DAY) {
                return processingValue * (workHoursPerDay != null ? workHoursPerDay : 8);
            } else {
                return processingValue * 24;
            }
        }
        return processingValue;
    }

    public int getClaimHours() {
        if (claimTimeType == null) {
            return 4;
        }
        if (claimTimeType.isDayBased()) {
            if (claimTimeType == SlaTimeType.WORK_DAY) {
                return claimValue * (workHoursPerDay != null ? workHoursPerDay : 8);
            } else {
                return claimValue * 24;
            }
        }
        return claimValue != null ? claimValue : 4;
    }

    public boolean useWorkDays() {
        return processingTimeType != null && processingTimeType.isWorkBased();
    }
}
