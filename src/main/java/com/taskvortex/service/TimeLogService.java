package com.taskvortex.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taskvortex.dto.TimeLogRequest;
import com.taskvortex.entity.ActiveTimer;
import com.taskvortex.entity.Task;
import com.taskvortex.entity.TimeLog;
import com.taskvortex.entity.User;
import com.taskvortex.repository.ActiveTimerRepository;
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

    @Autowired
    private ActiveTimerRepository activeTimerRepository;

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

    @Transactional
    public ActiveTimer startTimer(Long taskId, Long userId) {
        Optional<ActiveTimer> existingGlobalTimer = activeTimerRepository.findByUserId(userId);

        if (existingGlobalTimer.isPresent()) {
            Long runningTaskId = existingGlobalTimer.get().getTask().getId();
            throw new RuntimeException(
                    "You already have an active timer running on Task #TV-" + runningTaskId + ". Please stop it first.");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));

        ActiveTimer newTimer = ActiveTimer.builder()
                .user(user)
                .task(task)
                .startTime(java.time.LocalDateTime.now())
                .build();

        return activeTimerRepository.save(newTimer);
    }

    @Transactional
    public TimeLog stopTimer(Long taskId, Long userId, String description) {
        ActiveTimer activeTimer = activeTimerRepository.findByUserIdAndTaskId(userId, taskId)
                .orElseThrow(() -> new RuntimeException("No active timer found!"));

        // Calculate hours
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.Duration duration = java.time.Duration.between(activeTimer.getStartTime(), now);

        double exactHours = duration.toSeconds() / 3600.0;
        double loggedHours = Math.max(exactHours, 0.01);

        loggedHours = Math.round(loggedHours * 100.0) / 100.0;

        TimeLog log = TimeLog.builder()
                .user(activeTimer.getUser())
                .task(activeTimer.getTask())
                .logDate(java.time.LocalDate.now())
                .loggedHours(loggedHours)
                .description(description != null ? description : "Worked via Timer")
                .build();

        TimeLog savedLog = timeLogRepository.save(log);

        activeTimerRepository.delete(activeTimer);

        return savedLog;
    }

    public ActiveTimer getActiveTimer(Long taskId, Long userId) {
        return activeTimerRepository.findByUserIdAndTaskId(userId, taskId).orElse(null);
    }
}