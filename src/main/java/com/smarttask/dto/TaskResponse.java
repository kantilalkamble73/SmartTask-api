package com.smarttask.dto;

import com.smarttask.entity.Priority;
import com.smarttask.entity.Recurrence;
import com.smarttask.entity.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * What the client actually sees for a task: the raw fields PLUS everything
 * the smart engine derives (score, quadrant, overdue flag).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {
    private Long id;
    private String title;
    private String description;
    private LocalDate dueDate;
    private Priority priority;
    private Status status;
    private String category;
    private Double estimatedHours;
    private Recurrence recurrence;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    // ---- smart engine output ----
    private Double smartScore;
    private String quadrant;       // DO_NOW / SCHEDULE / DELEGATE / ELIMINATE
    private boolean overdue;
    private long daysUntilDue;     // negative if overdue

    // ---- explainability: why did the engine give this score? ----
    private Double urgencyScore;
    private Double importanceScore;
    private Double effortScore;
    private String reason;
}
