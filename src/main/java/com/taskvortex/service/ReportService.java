package com.taskvortex.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taskvortex.dto.EmployeeDetailResponse;
import com.taskvortex.dto.EmployeeHeaderDTO;
import com.taskvortex.dto.PerformanceReportResponse;
import com.taskvortex.dto.TaskDetailDTO;
import com.taskvortex.dto.TimeLogDTO;
import com.taskvortex.entity.Task;
import com.taskvortex.entity.TaskStatus;
import com.taskvortex.entity.User;
import com.taskvortex.entity.UserTaskHistory;
import com.taskvortex.entity.UserTaskPoint;
import com.taskvortex.repository.TaskRepository;
import com.taskvortex.repository.TimeLogRepository;
import com.taskvortex.repository.UserRepository;
import com.taskvortex.repository.UserTaskHistoryRepository;
import com.taskvortex.repository.UserTaskPointRepository;

@Service
public class ReportService {

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private TaskRepository taskRepository;

        @Autowired
        private TimeLogRepository timeLogRepository;

        @Autowired
        private UserTaskPointRepository userTaskPointRepository;

                @Autowired
        private UserTaskHistoryRepository userTaskHistoryRepository;

        // ==========================================
        // 1. MONTHLY PERFORMANCE REPORT (UPDATED WITH HISTORY)
        // ==========================================
        public PerformanceReportResponse getMonthlyPerformance(String yearMonthStr, String projectIdStr, String role,
                        Long loggedInUserId) {
                YearMonth yearMonth = YearMonth.parse(yearMonthStr);
                LocalDate startDate = yearMonth.atDay(1);
                LocalDate endDate = yearMonth.atEndOfMonth();
                LocalDateTime startDateTime = startDate.atStartOfDay();
                LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

                Long projectId = "ALL".equalsIgnoreCase(projectIdStr) ? null : Long.parseLong(projectIdStr);

                String cleanRole = (role != null) ? role.trim().toUpperCase() : "EMPLOYEE";

                System.out.println("==== REPORT API TRIGGERED ====");
                System.out.println("Requested By User ID : " + loggedInUserId);
                System.out.println("Requested Role       : " + cleanRole);

                final List<User> targetUsers = new ArrayList<>();

                if ("ADMIN".equals(cleanRole) || "SUPER_ADMIN".equals(cleanRole)) {
                        targetUsers.addAll(userRepository.findAll());
                        System.out.println("Action: Admin fetched all " + targetUsers.size() + " users.");
                } else if ("MANAGER".equals(cleanRole)) {
                        List<User> team = userRepository.findTeamMembersByManagerId(loggedInUserId);
                        targetUsers.addAll(team);
                        System.out.println("Action: Manager fetched " + team.size() + " team members.");

                        userRepository.findById(loggedInUserId).ifPresent(manager -> {
                                if (!targetUsers.contains(manager)) {
                                        targetUsers.add(manager);
                                }
                        });
                } else {
                        userRepository.findById(loggedInUserId).ifPresent(targetUsers::add);
                        System.out.println("Action: Employee fetched only themselves.");
                }

                List<PerformanceReportResponse.EmployeeReport> employeeReports = new ArrayList<>();

                int teamTotalPoints = 0;
                double teamTotalEst = 0.0;
                double teamTotalLogged = 0.0;

                for (User user : targetUsers) {
                        // 🔥 NAYA LOGIC: Current Task table ki jagah History Table me scan karo
                        List<UserTaskHistory> historyList = userTaskHistoryRepository
                                        .findByAssigneeIdAndActionDateBetween(user.getId(), startDateTime, endDateTime);

                        Map<Long, UserTaskHistory> completedTasks = new HashMap<>();

                        for (UserTaskHistory h : historyList) {
                                boolean isCompleted = h.getStatus() != null &&
                                                (h.getStatus().endsWith("_COMPLETE") || h.getStatus().equals("DONE")
                                                                || h.getStatus().equals("COMPLETED"));

                                if (isCompleted) {
                                        if (projectId != null) {
                                                Task original = taskRepository.findById(h.getTaskId()).orElse(null);
                                                if (original != null && original.getProject() != null
                                                                && original.getProject().getId().equals(projectId)) {
                                                        completedTasks.put(h.getTaskId(), h);
                                                }
                                        } else {
                                                completedTasks.put(h.getTaskId(), h);
                                        }
                                }
                        }

                        Double loggedHours = timeLogRepository.getLoggedHoursForUserInMonth(
                                        user.getId(), startDate, endDate, projectId);
                        loggedHours = (loggedHours != null) ? loggedHours : 0.0;

                        if (completedTasks.isEmpty() && loggedHours == 0.0)
                                continue;

                        int points = completedTasks.values().stream()
                                        .mapToInt(t -> t.getTaskPoints() != null ? t.getTaskPoints() : 0).sum();
                        double estHours = completedTasks.values().stream()
                                        .mapToDouble(t -> t.getWorkingHours() != null ? t.getWorkingHours() : 0.0)
                                        .sum();

                        employeeReports.add(PerformanceReportResponse.EmployeeReport.builder()
                                        .id(user.getId())
                                        .name(user.getFirstName() + " " + user.getLastName())
                                        .avatar(generateAvatarUrl(user))
                                        .tasksCompleted(completedTasks.size())
                                        .points(points)
                                        .estHours(round(estHours))
                                        .loggedHours(round(loggedHours))
                                        .efficiency(calculateEfficiency(estHours, loggedHours))
                                        .build());

                        teamTotalPoints += points;
                        teamTotalEst += estHours;
                        teamTotalLogged += loggedHours;
                }

                return PerformanceReportResponse.builder()
                                .kpiData(PerformanceReportResponse.KPIData.builder()
                                                .totalPoints(teamTotalPoints)
                                                .totalLoggedHours(round(teamTotalLogged))
                                                .teamEfficiency(calculateEfficiency(teamTotalEst, teamTotalLogged))
                                                .build())
                                .employeeReports(employeeReports)
                                .build();
        }

        // ==========================================
        // 2. EMPLOYEE DETAIL (UPDATED WITH HISTORY SNAPSHOTS)
        // ==========================================

        public EmployeeDetailResponse getEmployeeDetail(Long userId, String monthStr) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                YearMonth ym = YearMonth.parse(monthStr);
                LocalDate start = ym.atDay(1);
                LocalDate end = ym.atEndOfMonth();
                LocalDateTime startDateTime = start.atStartOfDay();
                LocalDateTime endDateTime = end.atTime(23, 59, 59);

                // Fetch data from History table (Snapshot)
                List<UserTaskHistory> userHistories = userTaskHistoryRepository
                                .findByAssigneeIdAndActionDateBetween(userId, startDateTime, endDateTime);

                // Remove duplicate entries (Keep latest snapshot of each task)
                Map<Long, UserTaskHistory> latestTasksMap = new HashMap<>();
                for (UserTaskHistory h : userHistories) {
                        latestTasksMap.put(h.getTaskId(), h);
                }

                List<TimeLogDTO> logs = timeLogRepository.findLogsByUserIdAndDateRange(userId, start, end);
                List<TaskDetailDTO> personalizedTasks = new ArrayList<>();

                int totalEarnedPoints = 0;
                int completedCount = 0;
                double totalEst = 0.0;

                // Calculate total logged hours strictly from TimeLog table for this user
                Double exactTotalLogged = timeLogRepository.getLoggedHoursForUserInMonth(userId, start, end, null);
                double totalLog = (exactTotalLogged != null) ? exactTotalLogged : 0.0;

                for (UserTaskHistory h : latestTasksMap.values()) {
                        // Extract Project Name from Original Task
                        Task originalTask = taskRepository.findById(h.getTaskId()).orElse(null);
                        String projectName = (originalTask != null && originalTask.getProject() != null)
                                        ? originalTask.getProject().getName()
                                        : "Unknown";

                        TaskStatus status = null;
                        try {
                                status = TaskStatus.valueOf(h.getStatus());
                        } catch (Exception ignored) {
                        }

                        Optional<UserTaskPoint> walletOpt = userTaskPointRepository.findByUserIdAndTaskId(userId,
                                        h.getTaskId());

                        int personalizedPoints = 0;
                        if (walletOpt.isPresent() && walletOpt.get().getEarnedPoints() != null) {
                                personalizedPoints = walletOpt.get().getEarnedPoints();
                        }

                        // --- NEW FIX: Fetch exact hours logged ONLY by this user for THIS specific
                        // task ---
                        Double taskSpecificLog = timeLogRepository.getLoggedHoursByUserAndTask(userId, h.getTaskId(),
                                        start, end);
                        double taskLog = (taskSpecificLog != null) ? taskSpecificLog : 0.0;

                        // Fill DTO with snapshot data and user-specific logged hours
                        TaskDetailDTO updatedTask = new TaskDetailDTO(
                                        h.getTaskId(),
                                        h.getTitle(),
                                        personalizedPoints,
                                        h.getWorkingHours(), // estHours
                                        taskLog, // Actual logged hours by THIS user
                                        status,
                                        projectName);

                        personalizedTasks.add(updatedTask);

                        boolean isCompletedByStatus = status != null &&
                                        (status == TaskStatus.DEVELOPMENT_COMPLETE ||
                                                        status == TaskStatus.TESTING_COMPLETE ||
                                                        status == TaskStatus.DEPLOYMENT_COMPLETE ||
                                                        h.getStatus().equals("DONE") ||
                                                        h.getStatus().equals("COMPLETED"));

                        if (personalizedPoints > 0 || isCompletedByStatus) {
                                totalEarnedPoints += personalizedPoints;
                                totalEst += (h.getWorkingHours() != null) ? h.getWorkingHours() : 0.0;

                                if (personalizedPoints > 0) {
                                        completedCount++;
                                }
                        }
                }

                EmployeeHeaderDTO header = new EmployeeHeaderDTO(
                                user.getFirstName() + " " + user.getLastName(),
                                generateAvatarUrl(user),
                                (double) calculateEfficiency(totalEst, totalLog),
                                completedCount,
                                totalEarnedPoints);

                return new EmployeeDetailResponse(user, header, personalizedTasks, logs);
        }

        // ==========================================
        // 3. UTILITY METHODS
        // ==========================================
        private int calculateEfficiency(Double est, Double log) {
                if ((est == null || est <= 0) && (log != null && log > 0)) {
                        return 0;
                }
                if (log == null || log <= 0) {
                        return 0;
                }
                return (int) Math.round((est / log) * 100);
        }

        private String generateAvatarUrl(User user) {
                if (user.getProfileUrl() != null && !user.getProfileUrl().isEmpty())
                        return user.getProfileUrl();
                return "https://ui-avatars.com/api/?name=" + user.getFirstName() + "+" + user.getLastName()
                                + "&background=random&color=fff";
        }

        private double round(double val) {
                return Math.round(val * 10.0) / 10.0;
        }
}