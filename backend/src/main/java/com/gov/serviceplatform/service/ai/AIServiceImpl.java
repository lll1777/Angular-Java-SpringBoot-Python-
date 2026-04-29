package com.gov.serviceplatform.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIServiceImpl implements AIService {

    @Value("${ai.service.url:http://localhost:5000}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, String> CATEGORY_DEPARTMENT_MAP = new HashMap<>();
    
    static {
        CATEGORY_DEPARTMENT_MAP.put("城市管理", "城市管理局");
        CATEGORY_DEPARTMENT_MAP.put("环境卫生", "环境卫生管理处");
        CATEGORY_DEPARTMENT_MAP.put("交通出行", "交通运输局");
        CATEGORY_DEPARTMENT_MAP.put("教育资源", "教育局");
        CATEGORY_DEPARTMENT_MAP.put("医疗卫生", "卫生健康委员会");
        CATEGORY_DEPARTMENT_MAP.put("社会保障", "人力资源和社会保障局");
        CATEGORY_DEPARTMENT_MAP.put("住房保障", "住房和城乡建设局");
        CATEGORY_DEPARTMENT_MAP.put("市场监管", "市场监督管理局");
        CATEGORY_DEPARTMENT_MAP.put("环境保护", "生态环境局");
        CATEGORY_DEPARTMENT_MAP.put("治安管理", "公安局");
        CATEGORY_DEPARTMENT_MAP.put("政务服务", "行政审批服务局");
        CATEGORY_DEPARTMENT_MAP.put("其他", "政府服务热线中心");
    }

    @Override
    public ClassificationResult classifyAndRecommend(String content) {
        try {
            String url = aiServiceUrl + "/api/ai/classify";
            
            Map<String, Object> request = new HashMap<>();
            request.put("content", content);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                ClassificationResult classificationResult = new ClassificationResult();
                classificationResult.setCategory((String) result.get("category"));
                classificationResult.setSubCategory((String) result.get("subCategory"));
                classificationResult.setRecommendedDepartment((String) result.get("recommendedDepartment"));
                classificationResult.setRecommendedDepartmentId(result.get("recommendedDepartmentId") != null 
                    ? ((Number) result.get("recommendedDepartmentId")).longValue() : null);
                classificationResult.setConfidence(result.get("confidence") != null 
                    ? ((Number) result.get("confidence")).doubleValue() : 0.8);
                classificationResult.setKeywords((List<String>) result.get("keywords"));
                return classificationResult;
            }
        } catch (RestClientException e) {
            log.warn("AI服务调用失败，使用本地分类规则: {}", e.getMessage());
        }
        
        return classifyLocally(content);
    }

    @Override
    public List<SimilarTicketResult> findSimilarTickets(Long ticketId, String content, int limit) {
        try {
            String url = aiServiceUrl + "/api/ai/similar";
            
            Map<String, Object> request = new HashMap<>();
            request.put("ticketId", ticketId);
            request.put("content", content);
            request.put("limit", limit);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<List> response = restTemplate.postForEntity(url, entity, List.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> results = response.getBody();
                List<SimilarTicketResult> similarResults = new ArrayList<>();
                for (Map<String, Object> result : results) {
                    SimilarTicketResult sr = new SimilarTicketResult();
                    sr.setTicketId(((Number) result.get("ticketId")).longValue());
                    sr.setTicketNumber((String) result.get("ticketNumber"));
                    sr.setTitle((String) result.get("title"));
                    sr.setSimilarity(((Number) result.get("similarity")).doubleValue());
                    sr.setStatus((String) result.get("status"));
                    sr.setDepartmentName((String) result.get("departmentName"));
                    similarResults.add(sr);
                }
                return similarResults;
            }
        } catch (RestClientException e) {
            log.warn("AI相似诉求服务调用失败: {}", e.getMessage());
        }
        
        return Collections.emptyList();
    }

    @Override
    public List<KnowledgeResult> recommendKnowledge(String content, int limit) {
        try {
            String url = aiServiceUrl + "/api/ai/knowledge";
            
            Map<String, Object> request = new HashMap<>();
            request.put("content", content);
            request.put("limit", limit);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<List> response = restTemplate.postForEntity(url, entity, List.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> results = response.getBody();
                List<KnowledgeResult> knowledgeResults = new ArrayList<>();
                for (Map<String, Object> result : results) {
                    KnowledgeResult kr = new KnowledgeResult();
                    kr.setKnowledgeId(((Number) result.get("knowledgeId")).longValue());
                    kr.setTitle((String) result.get("title"));
                    kr.setCategory((String) result.get("category"));
                    kr.setSummary((String) result.get("summary"));
                    kr.setRelevanceScore(((Number) result.get("relevanceScore")).doubleValue());
                    knowledgeResults.add(kr);
                }
                return knowledgeResults;
            }
        } catch (RestClientException e) {
            log.warn("AI知识库推荐服务调用失败: {}", e.getMessage());
        }
        
        return Collections.emptyList();
    }

    @Override
    public BatchAssignResult batchClassifyAndAssign(List<String> contents) {
        try {
            String url = aiServiceUrl + "/api/ai/batch-assign";
            
            Map<String, Object> request = new HashMap<>();
            request.put("contents", contents);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                BatchAssignResult batchResult = new BatchAssignResult();
                batchResult.setTotalCount((Integer) result.get("totalCount"));
                batchResult.setAssignedCount((Integer) result.get("assignedCount"));
                
                List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("results");
                List<ItemResult> itemResults = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    ItemResult ir = new ItemResult();
                    ir.setTitle((String) item.get("title"));
                    ir.setDepartment((String) item.get("department"));
                    ir.setDepartmentId(item.get("departmentId") != null 
                        ? ((Number) item.get("departmentId")).longValue() : null);
                    ir.setConfidence(((Number) item.get("confidence")).doubleValue());
                    itemResults.add(ir);
                }
                batchResult.setResults(itemResults);
                return batchResult;
            }
        } catch (RestClientException e) {
            log.warn("AI批量交办服务调用失败: {}", e.getMessage());
        }
        
        return batchClassifyLocally(contents);
    }

    private ClassificationResult classifyLocally(String content) {
        ClassificationResult result = new ClassificationResult();
        
        String lowerContent = content.toLowerCase();
        
        String category = "其他";
        String subCategory = "";
        String department = "政府服务热线中心";
        
        if (containsAny(lowerContent, "垃圾", "环境", "卫生", "保洁", "污水", "异味")) {
            category = "环境卫生";
            department = "环境卫生管理处";
        } else if (containsAny(lowerContent, "车", "交通", "拥堵", "停车", "公交", "地铁", "违章")) {
            category = "交通出行";
            department = "交通运输局";
        } else if (containsAny(lowerContent, "学校", "教育", "教师", "学生", "入学", "补课")) {
            category = "教育资源";
            department = "教育局";
        } else if (containsAny(lowerContent, "医院", "医疗", "医生", "药品", "医保", "看病")) {
            category = "医疗卫生";
            department = "卫生健康委员会";
        } else if (containsAny(lowerContent, "社保", "养老", "失业", "保险", "公积金")) {
            category = "社会保障";
            department = "人力资源和社会保障局";
        } else if (containsAny(lowerContent, "房子", "房产", "物业", "拆迁", "安置", "装修")) {
            category = "住房保障";
            department = "住房和城乡建设局";
        } else if (containsAny(lowerContent, "消费", "价格", "质量", "假货", "投诉")) {
            category = "市场监管";
            department = "市场监督管理局";
        } else if (containsAny(lowerContent, "污染", "噪音", "气味", "排放", "环保")) {
            category = "环境保护";
            department = "生态环境局";
        } else if (containsAny(lowerContent, "警察", "盗窃", "诈骗", "治安", "报警")) {
            category = "治安管理";
            department = "公安局";
        } else if (containsAny(lowerContent, "办证", "审批", "许可", "政务")) {
            category = "政务服务";
            department = "行政审批服务局";
        }
        
        result.setCategory(category);
        result.setSubCategory(subCategory);
        result.setRecommendedDepartment(department);
        result.setConfidence(0.7);
        result.setKeywords(extractKeywords(content));
        
        return result;
    }

    private BatchAssignResult batchClassifyLocally(List<String> contents) {
        BatchAssignResult result = new BatchAssignResult();
        List<ItemResult> itemResults = new ArrayList<>();
        
        for (String content : contents) {
            ClassificationResult cr = classifyLocally(content);
            ItemResult ir = new ItemResult();
            ir.setTitle(content.length() > 50 ? content.substring(0, 50) + "..." : content);
            ir.setDepartment(cr.getRecommendedDepartment());
            ir.setConfidence(cr.getConfidence());
            itemResults.add(ir);
        }
        
        result.setTotalCount(contents.size());
        result.setAssignedCount(itemResults.size());
        result.setResults(itemResults);
        
        return result;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractKeywords(String content) {
        List<String> keywords = new ArrayList<>();
        String[] words = content.split("[，。、；：？！，\\s]+");
        for (String word : words) {
            if (word.length() >= 2 && word.length() <= 10) {
                keywords.add(word);
            }
        }
        return keywords;
    }
}
