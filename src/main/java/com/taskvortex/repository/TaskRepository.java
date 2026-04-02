package com.taskvortex.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taskvortex.dto.TaskDetailDTO;
import com.taskvortex.entity.Task;
import com.taskvortex.entity.TaskStatus;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

        // --- EXISTING BOARD QUERIES ---
        List<Task> findByProjectManagerIdAndParentTaskIsNull(Long managerId);

        List<Task> findByProjectManagerId(Long managerId);

        // Fetch tasks where the user is the current assignee OR they have contributed
        // to it (earned points)
        @Query("SELECT DISTINCT t FROM Task t " +
                        "LEFT JOIN UserTaskPoint utp ON t.id = utp.taskId " +
                        "WHERE t.assigneeId = :userId OR utp.userId = :userId")
        List<Task> findTasksContributedByUserId(@Param("userId") Long userId);

        List<Task> findByAssigneeId(Long userId);

        @Query("SELECT t FROM Task t WHERE " +
                        "(:pIds IS NULL OR t.project.id IN :pIds) AND " +
                        "(:aIds IS NULL OR t.assigneeId IN :aIds) AND " +
                        "(:statuses IS NULL OR t.status IN :statuses) AND " +
                        "(:depts IS NULL OR t.project.department.name IN :depts) AND " +
                        "(:search IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')))")
        List<Task> findBoardTasks(
                        @Param("pIds") List<Long> pIds,
                        @Param("aIds") List<Long> aIds,
                        @Param("statuses") List<TaskStatus> statuses,
                        @Param("depts") List<String> depts,
                        @Param("search") String search);

        @Query("SELECT COUNT(t) FROM Task t WHERE t.assigneeId = :userId AND t.status != 'DEPLOYMENT_COMPLETE'")
        long countActiveTasksByUserId(@Param("userId") Long userId);

        // @Query("SELECT COUNT(t) FROM Task t WHERE t.completedBy = :userId")
        // long countCompletedTasksByOriginalDeveloper(@Param("userId") Long userId);

        // --- REPORTING QUERIES ---

        /**
         * Fetches tasks for the main Performance Table.
         * Now checks if the user is the current assignee OR if they earned points for
         * it.
         */
        @Query("SELECT DISTINCT t FROM Task t " +
                        "LEFT JOIN UserTaskPoint utp ON t.id = utp.taskId " +
                        "WHERE (t.assigneeId = :userId OR utp.userId = :userId) " +
                        "AND t.status IN ('DONE', 'DEPLOYMENT_COMPLETE', 'TESTING_COMPLETE', 'DEVELOPMENT_COMPLETE') " +
                        "AND t.dueDate BETWEEN :startDate AND :endDate " +
                        "AND (:projectId IS NULL OR t.project.id = :projectId)")
        List<Task> findCompletedTasksForUserInMonth(
                        @Param("userId") Long userId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("projectId") Long projectId);

        /**
         * * Fetches DTOs for the Employee Deep Dive.
         * Now checks if the user is the current assignee OR if they earned points for
         * it.
         */
        @Query("SELECT DISTINCT new com.taskvortex.dto.TaskDetailDTO(" +
                        "t.id, " +
                        "t.title, " +
                        "t.taskPoints, " +
                        "t.workingHours, " +
                        "(SELECT COALESCE(SUM(l.loggedHours), 0.0) FROM TimeLog l WHERE l.task.id = t.id), " +
                        "t.status, " +
                        "t.project.name) " +
                        "FROM Task t " +
                        "LEFT JOIN UserTaskPoint utp ON t.id = utp.taskId " +
                        "WHERE (t.assigneeId = :userId OR utp.userId = :userId) " +
                        "AND t.dueDate BETWEEN :startDate AND :endDate")
        List<TaskDetailDTO> findTasksByUserIdAndDateRange(
                        @Param("userId") Long userId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);
}