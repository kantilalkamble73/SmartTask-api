package com.smarttask.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** A realistic plan for today: what fits into the user's available hours, in what order, and what gets deferred. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResponse {
    private double capacityHours;
    private double plannedHours;
    private boolean overloaded;
    private String message;
    private List<PlanItem> plan;
    private List<TaskResponse> deferred;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanItem {
        private TaskResponse task;
        private String start;      // "09:00"
        private String end;        // "10:30"
        private double hours;      // time blocked for this task today
        private boolean mustDo;    // overdue or due today
        private boolean slice;     // large task planned as a partial work session
    }
}
