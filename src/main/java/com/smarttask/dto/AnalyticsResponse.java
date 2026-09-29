package com.smarttask.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {
    private long totalTasks;
    private long completedTasks;
    private long pendingTasks;
    private long overdueTasks;
    private double completionRate;            // percentage
    private Double onTimeRate;                // % of completed tasks finished by their due date (null if no data)
    private Double avgCompletionDays;         // average days from creation to completion (null if no data)
    private int productivityScore;            // 0-100 composite
    private int currentStreakDays;            // consecutive days with >=1 completion
    private double pendingHours;              // estimated hours of work still open
    private long completedThisWeek;
    private Map<String, Long> tasksByPriority;
    private Map<String, Long> tasksByStatus;
    private Map<String, Long> tasksByQuadrant;
    private Map<String, Long> tasksByCategory;
    private List<DayCount> productivityTrend; // last 7 days completions (label = "Sep 3")
    private List<DayCount> activityHeatmap;   // last 84 days (date = ISO yyyy-MM-dd)
    private TaskResponse nextBestTask;
    private List<Insight> insights;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayCount {
        private String date;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Insight {
        private String type;     // DANGER / WARNING / SUCCESS / INFO
        private String message;
    }
}
