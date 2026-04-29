package com.gov.serviceplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ticket_cooperations")
public class TicketCooperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiator_department_id", nullable = false)
    private Department initiatorDepartment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cooperation_department_id", nullable = false)
    private Department cooperationDepartment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiator_id")
    private User initiator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handler_id")
    private User handler;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CooperationStatus status;

    @Column(columnDefinition = "TEXT")
    private String requirement;

    @Column(columnDefinition = "TEXT")
    private String response;

    @Column(name = "processing_hours")
    private Integer processingHours;

    @Column(name = "due_time")
    private LocalDateTime dueTime;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum CooperationStatus {
        PENDING("待接受"),
        ACCEPTED("已接受"),
        PROCESSING("办理中"),
        COMPLETED("已完成"),
        REJECTED("已拒绝"),
        EXPIRED("已超时");

        private final String description;

        CooperationStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = CooperationStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
