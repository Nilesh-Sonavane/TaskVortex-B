package com.taskvortex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taskvortex.dto.EmployeeDetailResponse;
import com.taskvortex.dto.PerformanceReportResponse;
import com.taskvortex.service.ReportService;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/performance")
    public ResponseEntity<PerformanceReportResponse> getPerformanceReport(
            @RequestParam(name = "month") String month,
            @RequestParam(name = "projectId", defaultValue = "ALL") String projectId,
            @RequestParam(name = "role", defaultValue = "EMPLOYEE") String role,
            @RequestParam(name = "userId") Long loggedInUserId) {

        try {
            PerformanceReportResponse response = reportService.getMonthlyPerformance(month, projectId, role,
                    loggedInUserId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/employee/{id}")
    public ResponseEntity<EmployeeDetailResponse> getEmployeeDetail(
            @PathVariable Long id,
            @RequestParam String month) {

        try {
            EmployeeDetailResponse response = reportService.getEmployeeDetail(id, month);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Log the actual error to your IDE console so you can see WHAT failed
            e.printStackTrace();

            // Return 404 if it's a "not found" issue, or 400 for bad input
            return ResponseEntity.notFound().build();
        }
    }

}