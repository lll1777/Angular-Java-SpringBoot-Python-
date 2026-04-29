package com.gov.serviceplatform.service.ai;

import lombok.Data;
import java.util.List;

public interface AIService {
    
    ClassificationResult classifyAndRecommend(String content);
    
    List<SimilarTicketResult> findSimilarTickets(Long ticketId, String content, int limit);
    
    List<KnowledgeResult> recommendKnowledge(String content, int limit);
    
    BatchAssignResult batchClassifyAndAssign(List<String> contents);

    @Data
    class ClassificationResult {
        private String category;
        private String subCategory;
        private String recommendedDepartment;
        private Long recommendedDepartmentId;
        private Double confidence;
        private List<String> keywords;
    }

    @Data
    class SimilarTicketResult {
        private Long ticketId;
        private String ticketNumber;
        private String title;
        private Double similarity;
        private String status;
        private String departmentName;
    }

    @Data
    class KnowledgeResult {
        private Long knowledgeId;
        private String title;
        private String category;
        private String summary;
        private Double relevanceScore;
    }

    @Data
    class BatchAssignResult {
        private int totalCount;
        private int assignedCount;
        private List<ItemResult> results;
    }

    @Data
    class ItemResult {
        private String title;
        private String department;
        private Long departmentId;
        private Double confidence;
    }
}
