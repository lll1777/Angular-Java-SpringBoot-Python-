package com.gov.serviceplatform.dto;

import com.gov.serviceplatform.enums.AlertLevel;
import com.gov.serviceplatform.enums.TicketStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TicketQueryDTO {
    private String ticketNumber;
    private String title;
    private String citizenName;
    private String citizenPhone;
    private TicketStatus status;
    private AlertLevel alertLevel;
    private Long departmentId;
    private Long handlerId;
    private String category;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean isUrgent;
}
