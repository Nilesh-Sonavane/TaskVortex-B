package com.taskvortex.dto;

import java.util.List;

import com.taskvortex.entity.Subtask; // Import your subtask entity or DTO

import lombok.Data;

@Data
public class TaskResponse {
    private Long id;
    private String title;
    private String description;

    // Display names (for "View Details" modal)
    private String project;
    private String assigneeName;

    // Raw IDs (for "Edit Task" form selection)
    private Long projectId; // Used to auto-select <select> project
    private Long assigneeId; // Used to resolve member initials/avatar

    private String status;
    private String priority;
    private String dueDate;

    // Collections (for Subtasks and Files)
    private List<Subtask> subtasks; // Used to rebuild the FormArray
    private List<String> attachments; // Used to show "Current Files"
}