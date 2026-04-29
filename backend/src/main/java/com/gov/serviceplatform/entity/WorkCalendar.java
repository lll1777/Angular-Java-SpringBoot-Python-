package com.gov.serviceplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "work_calendar", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"date"})
})
public class WorkCalendar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "is_workday", nullable = false)
    private Boolean isWorkday;

    @Column(name = "is_holiday", nullable = false)
    private Boolean isHoliday;

    @Column(length = 100)
    private String holidayName;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(length = 50)
    private String remark;

    @Column(name = "work_start_time")
    private String workStartTime;

    @Column(name = "work_end_time")
    private String workEndTime;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (dayOfWeek == null && date != null) {
            dayOfWeek = date.getDayOfWeek().getValue();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
