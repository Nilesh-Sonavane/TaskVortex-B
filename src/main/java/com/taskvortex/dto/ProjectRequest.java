package com.taskvortex.dto;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(min = 3, message = "Project name must be at least 3 characters")
    private String name;

    // "key" is only required for CREATE, but for UPDATE it might be ignored or
    // read-only
    private String key;

    private String description;

    @NotNull(message = "Project Manager is required")
    private Long managerId;

    // --- NEW FIELDS NEEDED FOR EDIT PAGE ---
    private Long departmentId; // <--- ADD THIS
    private String status; // <--- ADD THIS (ACTIVE, ON_HOLD, etc.)
    private Integer progress; // <--- ADD THIS (Optional, if you edit progress manually)
    // ---------------------------------------

    @NotNull(message = "Start Date is required")
    private LocalDate startDate;

    @NotNull(message = "End Date is required")
    private LocalDate endDate;

    // We receive a list of User IDs from the frontend checkboxes
    private Set<Long> memberIds;
}