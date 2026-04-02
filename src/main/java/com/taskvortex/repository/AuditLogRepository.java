package com.taskvortex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taskvortex.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityNameOrderByTimestampDesc(String entityName);

    List<AuditLog> findAllByOrderByTimestampDesc();

    @Query("SELECT a FROM AuditLog a WHERE a.entityName = 'TASKS' AND (a.entityId = :taskId OR a.entityId IN (SELECT t.id FROM Task t WHERE t.parentTask.id = :taskId)) ORDER BY a.timestamp DESC")
    List<AuditLog> findHistoryForTaskAndSubtasks(@Param("taskId") Long taskId);
    // List<AuditLog> findByEntityIdInAndEntityNameOrderByTimestampDesc(List<Long>
    // entityIds, String entityName);
}