package com.taskvortex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taskvortex.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    // Fetch history for a specific task, newest first
    List<AuditLog> findByEntityIdAndEntityNameOrderByTimestampDesc(Long entityId, String entityName);

    List<AuditLog> findByEntityIdInAndEntityNameOrderByTimestampDesc(List<Long> entityIds, String entityName);
}