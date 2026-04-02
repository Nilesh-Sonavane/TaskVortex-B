package com.taskvortex.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taskvortex.entity.UserTaskPoint;

@Repository
public interface UserTaskPointRepository extends JpaRepository<UserTaskPoint, Long> {

    // Finds the specific record for a user on a specific task
    Optional<UserTaskPoint> findByUserIdAndTaskId(Long userId, Long taskId);
}