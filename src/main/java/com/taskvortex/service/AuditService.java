package com.taskvortex.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.taskvortex.entity.AuditLog;
import com.taskvortex.entity.User;
import com.taskvortex.repository.AuditLogRepository;
import com.taskvortex.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public void logAction(String entityName, String action, Long entityId, String details, User performer) {
        saveLog(entityName, action, entityId, details, performer);
    }

    public void logAction(String entityName, String action, Long entityId, String details, String performerInfo) {
        User performer = userRepository.findByEmail(performerInfo).orElse(null);
        saveLog(entityName, action, entityId, details, performer);
    }

    private void saveLog(String entityName, String action, Long entityId, String details, User performer) {
        AuditLog log = new AuditLog();
        log.setEntityName(entityName);
        log.setAction(action);
        log.setEntityId(entityId);
        log.setDetails(details);
        log.setPerformedBy(performer);
        log.setTimestamp(LocalDateTime.now());
        auditLogRepository.save(log);
    }
}