package com.taskvortex.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class TimeLogRequest {
    private Long userId; // Who is logging the time
    private Long taskId; // Which task
    private LocalDate logDate; // When they worked
    private Double loggedHours; // How long they worked (e.g., 2.5)
    private String description; // What they did
}