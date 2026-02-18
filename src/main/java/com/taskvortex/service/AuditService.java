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

    // Method for User object (Used in updateTask)
    public void logAction(String action, Long entityId, String details, User performer) {
        saveLog(action, entityId, details, performer);
    }

    // Method for String Email/Label (Used in createTask and removeAttachment)
    // This fixes your "Java(67108979)" compilation error
    public void logAction(String action, Long entityId, String details, String performerInfo) {
        User performer = userRepository.findByEmail(performerInfo).orElse(null);
        saveLog(action, entityId, details, performer);
    }

    private void saveLog(String action, Long entityId, String details, User performer) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityName("Task");
        log.setEntityId(entityId);
        log.setDetails(details);
        log.setPerformedBy(performer);
        log.setTimestamp(LocalDateTime.now());
        auditLogRepository.save(log);
    }
}