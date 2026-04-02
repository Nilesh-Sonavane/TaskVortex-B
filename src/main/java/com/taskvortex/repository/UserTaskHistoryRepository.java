package com.taskvortex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taskvortex.entity.UserTaskHistory;

@Repository
public interface UserTaskHistoryRepository extends JpaRepository<UserTaskHistory, Long> {

    Optional<UserTaskHistory> findByTaskIdAndAssigneeId(Long taskId, Long assigneeId);

    List<UserTaskHistory> findByAssigneeIdAndActionDateBetween(Long assigneeId, java.time.LocalDateTime start,
            java.time.LocalDateTime end);

}