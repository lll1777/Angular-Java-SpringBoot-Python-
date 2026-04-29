package com.gov.serviceplatform.dto;

import lombok.Data;

@Data
public class TicketCreateDTO {
    private String title;
    private String content;
    private String category;
    private String subCategory;
    private String citizenName;
    private String citizenPhone;
    private String address;
    private Boolean isAnonymous;
    private Boolean isUrgent;
    private Integer priorityLevel;
    private Long departmentId;
}
