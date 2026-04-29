package com.gov.serviceplatform.entity;

import com.gov.serviceplatform.enums.AlertLevel;
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

    @Column(name = "processing_hours")
    private Integer processingHours;

    @Column(name = "remaining_hours")
    private Integer remainingHours;

    @Column(name = "due_time")
    private LocalDateTime dueTime;

    @Column(name = "yellow_warning_time")
    private LocalDateTime yellowWarningTime;

    @Column(name = "red_warning_time")
    private LocalDateTime redWarningTime;

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
}
