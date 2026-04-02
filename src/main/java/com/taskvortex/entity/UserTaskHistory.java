package com.taskvortex.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users_task_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTaskHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId; // Primary Key

    private Long taskId; // Original Task ID
    private Long parentTaskId; // For subtasks

    // --- REPLICA FIELDS (Snapshot data) ---
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String status;
    private String priority;
    private Long assigneeId; // Nilesh, Sachin, etc.
    private Integer taskPoints;
    private Double workingHours;

    // --- METADATA ---
    private LocalDateTime actionDate;
}