package com.taskvortex.dto;

import java.time.LocalDate;
import java.util.Set;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectResponse {
    private Long id;
    private String name;
    private String projectKey;
    private String description;
    private String status;
    private Integer progress;
    private LocalDate startDate;
    private LocalDate targetEndDate;

    // Flattened Data
    private String managerName;
    private Long managerId;

    private String departmentName;
    private Long departmentId; // <--- ADD THIS FIELD (Required for Edit Dropdown)

    private int membersCount;
    private Set<MemberDTO> members;
}