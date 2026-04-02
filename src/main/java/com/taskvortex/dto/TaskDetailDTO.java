package com.taskvortex.dto;

import com.taskvortex.entity.TaskStatus;

public record TaskDetailDTO(Long id, String title, Integer points, Double estHours, Double loggedHours,
                TaskStatus status,
                String projectName) {
}
