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

    @Column(nullable = false, length = 100)
    private String category;

    @Column(length = 100)
    private String subCategory;

    @Column(nullable = false)
    private Boolean useWorkDays = true;

    @Column(name = "processing_hours", nullable = false)
    private Integer processingHours = 72;

    @Column(name = "claim_hours")
    private Integer claimHours = 4;

    @Column(name = "yellow_warning_percent", nullable = false)
    private Double yellowWarningPercent = 0.75;

    @Column(name = "red_warning_percent", nullable = false)
    private Double redWarningPercent = 0.90;

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
