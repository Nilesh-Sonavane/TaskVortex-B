package com.taskvortex.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taskvortex.entity.ActiveTimer;

@Repository
public interface ActiveTimerRepository extends JpaRepository<ActiveTimer, Long> {

    Optional<ActiveTimer> findByUserIdAndTaskId(Long userId, Long taskId);

    // Check if user has ANY active timer running on ANY task
    Optional<ActiveTimer> findByUserId(Long userId);
}