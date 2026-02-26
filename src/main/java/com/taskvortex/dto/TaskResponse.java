package com.taskvortex.dto;

import java.util.List;

import lombok.Data;

@Data
public class TaskResponse {
    private Long id;
    private String title;
    private String description;

    // Display names (for details page)
    private String project;
    private String assigneeName;
    private String assigneeEmail;
    // Raw IDs (for form selection)
    private Long projectId;
    private Long assigneeId;
    private String createdBy;
    private String creatorEmail;

    // --- HIERARCHY TRACKING ---
    /**
     * If this is NOT null, the Angular frontend knows:
     * 1. This is a subtask.
     * 2. Do NOT display the 'Add Subtask' UI.
     * 3. Show a 'Back to Parent' button.
     */
    private Long parentTaskId;

    private String status;
    private String priority;
    private String dueDate;

    /**
     * RECURSIVE MAPPING
     * Note: Per your requirement, this list will be empty if parentTaskId is set.
     */
    private List<TaskResponse> subtasks;

    private List<String> attachments;
}