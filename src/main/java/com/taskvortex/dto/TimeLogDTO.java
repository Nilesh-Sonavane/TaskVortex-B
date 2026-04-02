package com.taskvortex.dto;

import java.time.LocalDate;

public record TimeLogDTO(Long id, LocalDate date, Double hours, String description, String taskTitle) {
}