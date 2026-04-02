package com.taskvortex.dto;

public record EmployeeHeaderDTO(
        String name,
        String avatarUrl,
        Double efficiency,
        int completedCount,
        int totalPoints) {
}