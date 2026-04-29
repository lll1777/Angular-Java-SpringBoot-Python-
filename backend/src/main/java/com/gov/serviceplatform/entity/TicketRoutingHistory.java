package com.gov.serviceplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ticket_routing_history")
public class TicketRoutingHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_department_id")
    private Department fromDepartment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_department_id", nullable = false)
    private Department toDepartment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @Enumerated(EnumType.STRING)
    @Column(name = "routing_type", length = 30)
    private RoutingType routingType;

    @Column(name = "routing_level")
    private Integer routingLevel = 1;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "is_returned")
    private Boolean isReturned = false;

    @Column(name = "return_reason")
    private String returnReason;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum RoutingType {
        INITIAL_ASSIGN("初始派单"),
        TRANSFER("转办"),
        COOPERATION("协办"),
        ESCALATION("升级"),
        RETURNED("退回重派"),
        AUTO_ROUTE("自动路由");

        private final String description;

        RoutingType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
