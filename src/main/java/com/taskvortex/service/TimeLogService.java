package com.taskvortex.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taskvortex.dto.TimeLogRequest;
import com.taskvortex.entity.Task;
import com.taskvortex.entity.TimeLog;
import com.taskvortex.entity.User;
import com.taskvortex.repository.TaskRepository;
import com.taskvortex.repository.TimeLogRepository;
import com.taskvortex.repository.UserRepository;

@Service
public class TimeLogService {

    @Autowired
    private TimeLogRepository timeLogRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public TimeLog logTime(TimeLogRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Task task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new RuntimeException("Task not found"));

        TimeLog log = TimeLog.builder()
                .user(user)
                .task(task)
                .logDate(request.getLogDate())
                .loggedHours(request.getLoggedHours())
                .description(request.getDescription())
                .build();

        return timeLogRepository.save(log);
    }

    public Double getUserTotalLoggedHoursForTask(Long taskId, Long userId) {
        return timeLogRepository.getTotalLoggedHoursForTaskAndUser(taskId, userId);
    }

    public List<TimeLog> getLogsForTask(Long taskId) {
        return timeLogRepository.findByTaskIdOrderByLogDateDesc(taskId);
    }

    public Double getTotalLoggedHours(Long taskId) {
        return timeLogRepository.getTotalLoggedHoursForTask(taskId);
    }
}