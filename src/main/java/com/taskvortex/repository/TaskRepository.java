package com.taskvortex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taskvortex.entity.Task;
import com.taskvortex.entity.TaskStatus;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

        // Fetches only top-level tasks (no parent) for the specific manager
        List<Task> findByProjectManagerIdAndParentTaskIsNull(Long managerId);

        // Keep this if you still need to find every single task regardless of level
        List<Task> findByProjectManagerId(Long managerId);

        List<Task> findByAssigneeId(Long userId);

        @Query("SELECT t FROM Task t WHERE " +
                        "(:pIds IS NULL OR t.project.id IN :pIds) AND " +
                        "(:aIds IS NULL OR t.assigneeId IN :aIds) AND " +
                        "(:statuses IS NULL OR t.status IN :statuses) AND " +
                        "(:depts IS NULL OR t.project.department.name IN :depts) AND " + // New Filter Line
                        "(:search IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')))")
        List<Task> findBoardTasks(
                        @Param("pIds") List<Long> pIds,
                        @Param("aIds") List<Long> aIds,
                        @Param("statuses") List<TaskStatus> statuses,
                        @Param("depts") List<String> depts,
                        @Param("search") String search);

        @Query("SELECT COUNT(t) FROM Task t WHERE t.assigneeId = :userId AND t.status != 'DEPLOYMENT_COMPLETE'")
        long countActiveTasksByUserId(@Param("userId") Long userId);

}