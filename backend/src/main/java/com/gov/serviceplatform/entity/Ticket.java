package com.gov.serviceplatform.entity;

import com.gov.serviceplatform.enums.AlertLevel;
import com.gov.serviceplatform.enums.SlaTimeType;
import com.gov.serviceplatform.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "tickets")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String ticketNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String subCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "citizen_id")
    private User citizen;

    @Column(name = "citizen_name")
    private String citizenName;

    @Column(name = "citizen_phone")
    private String citizenPhone;

    @Column(length = 200)
    private String address;

    @Column(name = "is_anonymous")
    private Boolean isAnonymous = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_department_id")
    private Department currentDepartment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handler_id")
    private User handler;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AlertLevel alertLevel = AlertLevel.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "sla_time_type", length = 20)
    private SlaTimeType slaTimeType;

    @Column(name = "sla_time_type_description")
    private String slaTimeTypeDescription;

    @Column(name = "sla_processing_value")
    private Integer slaProcessingValue;

    @Column(name = "processing_hours")
    private Integer processingHours;

    @Column(name = "remaining_hours")
    private Integer remainingHours;

    @Column(name = "remaining_days")
    private Integer remainingDays;

    @Column(name = "due_time")
    private LocalDateTime dueTime;

    @Column(name = "yellow_warning_time")
    private LocalDateTime yellowWarningTime;

    @Column(name = "red_warning_time")
    private LocalDateTime redWarningTime;

    @Column(name = "claim_due_time")
    private LocalDateTime claimDueTime;

    @Column(columnDefinition = "TEXT")
    private String aiRecommendation;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "satisfaction_score")
    private Integer satisfactionScore;

    @Column(columnDefinition = "TEXT")
    private String satisfactionComment;

    @Column(name = "is_urgent")
    private Boolean isUrgent = false;

    @Column(name = "priority_level")
    private Integer priorityLevel = 1;

    @Column(columnDefinition = "TEXT")
    private String attachments;

    @Column(name = "transfer_count")
    private Integer transferCount = 0;

    @Column(name = "return_count")
    private Integer returnCount = 0;

    @Column(name = "escalation_count")
    private Integer escalationCount = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "first_assigned_at")
    private LocalDateTime firstAssignedAt;

    @Column(name = "last_transferred_at")
    private LocalDateTime lastTransferredAt;

    @Column(name = "sla_recalculated_at")
    private LocalDateTime slaRecalculatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (ticketNumber == null) {
            ticketNumber = generateTicketNumber();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private String generateTicketNumber() {
        String dateStr = java.time.LocalDate.now().toString().replace("-", "");
        String random = String.format("%06d", (int)(Math.random() * 1000000));
        return "GZ" + dateStr + random;
    }

    public String getSlaDisplay() {
        if (slaTimeType == null) {
            return "未配置";
        }
        String valueStr = slaProcessingValue != null ? String.valueOf(slaProcessingValue) : "0";
        return valueStr + "个" + slaTimeType.getDescription();
    }

    public boolean isSlaWorkBased() {
        return slaTimeType != null && slaTimeType.isWorkBased();
    }
}
