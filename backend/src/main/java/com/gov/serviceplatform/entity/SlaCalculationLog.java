package com.gov.serviceplatform.entity;

import com.gov.serviceplatform.enums.SlaTimeType;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sla_calculation_log")
public class SlaCalculationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Column(name = "ticket_number", length = 30)
    private String ticketNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SlaTimeType timeType;

    @Column(name = "time_type_description")
    private String timeTypeDescription;

    @Column(name = "processing_value")
    private Integer processingValue;

    @Column(name = "work_hours_per_day")
    private Integer workHoursPerDay;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "calculated_due_time")
    private LocalDateTime calculatedDueTime;

    @Column(name = "yellow_warning_time")
    private LocalDateTime yellowWarningTime;

    @Column(name = "red_warning_time")
    private LocalDateTime redWarningTime;

    @Column(name = "claim_due_time")
    private LocalDateTime claimDueTime;

    @Column(name = "calculation_reason")
    private String calculationReason;

    @Column(name = "sla_config_id")
    private Long slaConfigId;

    @Column(name = "sla_config_category")
    private String slaConfigCategory;

    @Column(name = "trigger_event")
    private String triggerEvent;

    @Column(name = "previous_due_time")
    private LocalDateTime previousDueTime;

    @Column(name = "is_urgent")
    private Boolean isUrgent = false;

    @Column(name = "urgent_multiplier")
    private Double urgentMultiplier;

    @Column(columnDefinition = "TEXT")
    private String calculationDetail;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
