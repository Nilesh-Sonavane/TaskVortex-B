package com.taskvortex.dto;

import java.util.List;

import com.taskvortex.entity.User;

public record EmployeeDetailResponse(
        User user,
        EmployeeHeaderDTO header,
        List<TaskDetailDTO> tasks,
        List<TimeLogDTO> logs) {
}
