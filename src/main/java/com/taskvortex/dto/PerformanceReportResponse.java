package com.taskvortex.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceReportResponse {
    private KPIData kpiData;
    private List<EmployeeReport> employeeReports;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KPIData {
        private Integer totalPoints;
        private Double totalLoggedHours;
        private Integer teamEfficiency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployeeReport {
        private Long id;
        private String name;
        private String avatar;
        private Integer tasksCompleted;
        private Integer points;
        private Double estHours;
        private Double loggedHours;
        private Integer efficiency;
    }
}