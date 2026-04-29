package com.gov.serviceplatform.entity;

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

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "sub_category", length = 100)
    private String subCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "processing_hours", nullable = false)
    private Integer processingHours;

    @Column(name = "use_work_days", nullable = false)
    private Boolean useWorkDays = true;

    @Column(name = "yellow_warning_ratio", nullable = false)
    private Double yellowWarningRatio = 0.75;

    @Column(name = "red_warning_ratio", nullable = false)
    private Double redWarningRatio = 0.9;

    @Column(name = "accept_timeout_hours")
    private Integer acceptTimeoutHours = 4;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "priority_level")
    private Integer priorityLevel = 1;

    @Column(columnDefinition = "TEXT")
    private String description;

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
