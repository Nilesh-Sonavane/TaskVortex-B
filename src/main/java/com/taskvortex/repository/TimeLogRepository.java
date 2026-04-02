package com.taskvortex.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taskvortex.dto.TimeLogDTO;
import com.taskvortex.entity.TimeLog;

@Repository
public interface TimeLogRepository extends JpaRepository<TimeLog, Long> {

        // Get all logs for a task, newest first
        List<TimeLog> findByTaskIdOrderByLogDateDesc(Long taskId);

        // Sum up all logged hours for a specific task
        @Query("SELECT COALESCE(SUM(t.loggedHours), 0.0) FROM TimeLog t WHERE t.task.id = :taskId")
        Double getTotalLoggedHoursForTask(@Param("taskId") Long taskId);

        // Get total logged hours for a user within a specific month and project
        @Query("SELECT COALESCE(SUM(t.loggedHours), 0.0) FROM TimeLog t " +
                        "WHERE t.user.id = :userId " +
                        "AND t.logDate BETWEEN :startDate AND :endDate " +
                        "AND (:projectId IS NULL OR t.task.project.id = :projectId)")
        Double getLoggedHoursForUserInMonth(
                        @Param("userId") Long userId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("projectId") Long projectId);

        /**
         * NEW: Used for the Employee Deep Dive (Activity Timeline)
         * Maps to TimeLogDTO: (id, date, hours, description, taskTitle)
         */
        @Query("SELECT new com.taskvortex.dto.TimeLogDTO(" +
                        "l.id, " +
                        "l.logDate, " +
                        "l.loggedHours, " +
                        "l.description, " +
                        "l.task.title) " +
                        "FROM TimeLog l " +
                        "WHERE l.user.id = :userId " +
                        "AND l.logDate BETWEEN :startDate AND :endDate " +
                        "ORDER BY l.logDate DESC")
        List<TimeLogDTO> findLogsByUserIdAndDateRange(
                        @Param("userId") Long userId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        // --- NEW: Get logged hours for a SPECIFIC user on a SPECIFIC task ---
        @Query("SELECT COALESCE(SUM(t.loggedHours), 0.0) FROM TimeLog t " +
                        "WHERE t.user.id = :userId AND t.task.id = :taskId " +
                        "AND t.logDate BETWEEN :startDate AND :endDate")
        Double getLoggedHoursByUserAndTask(
                        @Param("userId") Long userId,
                        @Param("taskId") Long taskId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        @Query("SELECT COALESCE(SUM(t.loggedHours), 0.0) FROM TimeLog t WHERE t.task.id = :taskId AND t.user.id = :userId")
        Double getTotalLoggedHoursForTaskAndUser(@Param("taskId") Long taskId, @Param("userId") Long userId);
}