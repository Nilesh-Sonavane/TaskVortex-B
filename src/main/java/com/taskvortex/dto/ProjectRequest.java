package com.taskvortex.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectRequest {
    @NotBlank(message = "Project name is required")
    private String name;

    @NotBlank(message = "Project key is required")
    private String key;

    private String description;

    @NotNull(message = "Manager is required")
    private Long managerId;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    // --- REMOVE @NotNull HERE ---
    private LocalDate endDate;

    private Long departmentId;
    private String status;
    private List<Long> memberIds;
}