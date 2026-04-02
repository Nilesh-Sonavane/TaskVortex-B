package com.taskvortex.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taskvortex.dto.TimeLogRequest;
import com.taskvortex.entity.TimeLog;
import com.taskvortex.service.TimeLogService;

@RestController
@RequestMapping("/api/time-logs")
@CrossOrigin(origins = "*") // Update based on your security config
public class TimeLogController {

    @Autowired
    private TimeLogService timeLogService;

    // 1. Submit a new time log from the Angular popup
    @PostMapping("/log")
    public ResponseEntity<?> addTimeLog(@RequestBody TimeLogRequest request) {
        try {
            TimeLog savedLog = timeLogService.logTime(request);
            return ResponseEntity.ok(savedLog);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 2. Get all time logs for the Task Details page (Optional: to show a history
    // of who worked when)
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<TimeLog>> getLogsForTask(@PathVariable Long taskId) {
        return ResponseEntity.ok(timeLogService.getLogsForTask(taskId));
    }

    // 3. Get the total sum of logged hours (to power the Progress Bar)
    @GetMapping("/task/{taskId}/total-hours")
    public ResponseEntity<Map<String, Double>> getTotalHours(@PathVariable Long taskId) {
        Double total = timeLogService.getTotalLoggedHours(taskId);

        Map<String, Double> response = new HashMap<>();
        response.put("totalLoggedHours", total);
        return ResponseEntity.ok(response);
    }

    // Get PERSONALIZED time logs for a specific user and task ---
    @GetMapping("/task/{taskId}/user/{userId}/hours")
    public ResponseEntity<java.util.Map<String, Double>> getUserTaskHours(
            @PathVariable Long taskId,
            @PathVariable Long userId) {

        // Call the service method we created earlier
        Double totalHours = timeLogService.getUserTotalLoggedHoursForTask(taskId, userId);

        // If null, return 0.0
        double finalHours = (totalHours != null) ? totalHours : 0.0;

        // Return as a JSON object so Angular can read 'res.totalLoggedHours' easily
        java.util.Map<String, Double> response = new java.util.HashMap<>();
        response.put("totalLoggedHours", finalHours);

        return ResponseEntity.ok(response);
    }
}