package com.gov.serviceplatform.service;

import com.gov.serviceplatform.entity.AuditLog;
import com.gov.serviceplatform.entity.User;
import com.gov.serviceplatform.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void logOperation(String operationType, String moduleName, Long targetId,
                              String operationDesc, String oldValue, String newValue, User operator) {
        AuditLog log = new AuditLog();
        log.setOperationType(operationType);
        log.setModuleName(moduleName);
        log.setTargetType(moduleName);
        log.setTargetId(targetId);
        log.setOperationDesc(operationDesc);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setOperator(operator);
        log.setOperatorName(operator != null ? operator.getRealName() : "系统");
        log.setIsSuccess(true);
        log.setCreatedAt(LocalDateTime.now());
        
        auditLogRepository.save(log);
    }

    @Transactional
    public void logError(String operationType, String moduleName, Long targetId,
                          String operationDesc, String errorMessage, User operator) {
        AuditLog log = new AuditLog();
        log.setOperationType(operationType);
        log.setModuleName(moduleName);
        log.setTargetType(moduleName);
        log.setTargetId(targetId);
        log.setOperationDesc(operationDesc);
        log.setErrorMessage(errorMessage);
        log.setOperator(operator);
        log.setOperatorName(operator != null ? operator.getRealName() : "系统");
        log.setIsSuccess(false);
        log.setCreatedAt(LocalDateTime.now());
        
        auditLogRepository.save(log);
    }

    public Page<AuditLog> getLogsByOperator(Long operatorId, Pageable pageable) {
        return auditLogRepository.findByOperatorId(operatorId, pageable);
    }

    public Page<AuditLog> getLogsByTarget(String targetType, Long targetId, Pageable pageable) {
        return auditLogRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable);
    }

    public List<AuditLog> getAllLogsByTarget(String targetType, Long targetId) {
        return auditLogRepository.findAllByTarget(targetType, targetId);
    }

    public List<AuditLog> getLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return auditLogRepository.findByTimeRange(startTime, endTime);
    }

    public Page<AuditLog> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }
}
