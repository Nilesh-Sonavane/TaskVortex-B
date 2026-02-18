package com.taskvortex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.taskvortex.entity.Task;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // Fetches only top-level tasks (no parent) for the specific manager
    List<Task> findByProjectManagerIdAndParentTaskIsNull(Long managerId);

    // Keep this if you still need to find every single task regardless of level
    List<Task> findByProjectManagerId(Long managerId);

}