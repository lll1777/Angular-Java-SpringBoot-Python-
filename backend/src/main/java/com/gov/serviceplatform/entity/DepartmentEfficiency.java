package com.gov.serviceplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "department_efficiency")
public class DepartmentEfficiency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "statistics_date")
    private LocalDate statisticsDate;

    @Column(name = "total_received")
    private Integer totalReceived = 0;

    @Column(name = "total_completed")
    private Integer totalCompleted = 0;

    @Column(name = "total_overdue")
    private Integer totalOverdue = 0;

    @Column(name = "total_red_warning")
    private Integer totalRedWarning = 0;

    @Column(name = "total_yellow_warning")
    private Integer totalYellowWarning = 0;

    @Column(name = "average_processing_hours")
    private Double averageProcessingHours;

    @Column(name = "average_satisfaction_score")
    private Double averageSatisfactionScore;

    @Column(name = "first_acceptance_rate")
    private Double firstAcceptanceRate;

    @Column(name = "on_time_completion_rate")
    private Double onTimeCompletionRate;

    @Column(name = "satisfaction_rate")
    private Double satisfactionRate;

    @Column(name = "efficiency_score")
    private Double efficiencyScore;

    @Column(name = "rank")
    private Integer rank;

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
}
