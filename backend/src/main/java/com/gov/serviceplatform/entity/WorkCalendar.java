package com.gov.serviceplatform.entity;

import com.gov.serviceplatform.enums.HolidayType;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Entity
@Table(name = "work_calendar",
       uniqueConstraints = {@UniqueConstraint(columnNames = {"date"})})
public class WorkCalendar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HolidayType holidayType;

    @Column(nullable = false)
    private Boolean isWorkDay = true;

    @Column(name = "work_start_time")
    private LocalTime workStartTime = LocalTime.of(9, 0);

    @Column(name = "work_end_time")
    private LocalTime workEndTime = LocalTime.of(18, 0);

    @Column(name = "lunch_start_time")
    private LocalTime lunchStartTime = LocalTime.of(12, 0);

    @Column(name = "lunch_end_time")
    private LocalTime lunchEndTime = LocalTime.of(13, 30);

    @Column(length = 100)
    private String holidayName;

    @Column(length = 500)
    private String remark;

    @Column(name = "year")
    private Integer year;

    @Column(name = "month")
    private Integer month;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (date != null) {
            year = date.getYear();
            month = date.getMonthValue();
            dayOfWeek = date.getDayOfWeek().getValue();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
