package com.smarttask.service;

import com.smarttask.entity.Priority;
import com.smarttask.entity.Status;
import com.smarttask.entity.Task;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * The "smart" engine of the application.
 *
 * Every task is scored on three axes and combined into a single 0-100
 * "smartScore" used to sort work for the user, plus classified into an
 * Eisenhower-matrix quadrant (urgent x important).
 *
 * Weights:
 *   Urgency    (how close the due date is)       -> 45%
 *   Importance (user-declared priority)          -> 35%
 *   Effort     (quick wins are nudged up)        -> 20%
 * Plus a small boost for tasks already IN_PROGRESS, so half-finished
 * work doesn't get buried under newer, shinier tasks.
 *
 * The engine is also explainable: {@link #explain(Task)} returns a plain
 * language reason so the UI can answer "why is this task ranked here?".
 */
@Service
public class SmartPriorityService {

    private static final double URGENCY_WEIGHT = 0.45;
    private static final double IMPORTANCE_WEIGHT = 0.35;
    private static final double EFFORT_WEIGHT = 0.20;
    private static final double IN_PROGRESS_BOOST = 6.0;

    public double calculateSmartScore(Task task) {
        if (task.getStatus() == Status.DONE) {
            return 0.0;
        }

        double urgency = urgencyScore(task.getDueDate());
        double importance = importanceScore(task.getPriority());
        double effort = effortScore(task.getEstimatedHours());
        double boost = task.getStatus() == Status.IN_PROGRESS ? IN_PROGRESS_BOOST : 0.0;

        double score = (urgency * URGENCY_WEIGHT)
                + (importance * IMPORTANCE_WEIGHT)
                + (effort * EFFORT_WEIGHT)
                + boost;

        return Math.min(100.0, Math.round(score * 100.0) / 100.0);
    }

    /** Eisenhower quadrant: is it urgent AND/OR important? */
    public String determineQuadrant(Task task) {
        boolean urgent = urgencyScore(task.getDueDate()) >= 65;
        boolean important = importanceScore(task.getPriority()) >= 75;

        if (urgent && important) return "DO_NOW";
        if (!urgent && important) return "SCHEDULE";
        if (urgent && !important) return "DELEGATE";
        return "ELIMINATE";
    }

    public long daysUntilDue(LocalDate dueDate) {
        if (dueDate == null) return Long.MAX_VALUE;
        return ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
    }

    public boolean isOverdue(Task task) {
        return task.getStatus() != Status.DONE
                && task.getDueDate() != null
                && task.getDueDate().isBefore(LocalDate.now());
    }

    /** Human readable explanation of the score, e.g. "Overdue by 2 days, critical importance, quick win." */
    public String explain(Task task) {
        if (task.getStatus() == Status.DONE) return "Completed.";

        List<String> parts = new ArrayList<>();
        if (task.getDueDate() == null) {
            parts.add("No deadline set");
        } else {
            long d = daysUntilDue(task.getDueDate());
            if (d < 0) parts.add("Overdue by " + Math.abs(d) + (Math.abs(d) == 1 ? " day" : " days"));
            else if (d == 0) parts.add("Due today");
            else if (d == 1) parts.add("Due tomorrow");
            else parts.add("Due in " + d + " days");
        }

        Priority p = task.getPriority() == null ? Priority.MEDIUM : task.getPriority();
        parts.add(p.name().toLowerCase() + " importance");

        Double h = task.getEstimatedHours();
        if (h != null && h > 0 && h <= 1) parts.add("quick win");
        else if (h != null && h > 8) parts.add("large task, best done in slices");

        if (task.getStatus() == Status.IN_PROGRESS) parts.add("already in progress");

        return String.join(", ", parts) + ".";
    }

    // ---------------------------------------------------------------
    // Individual sub-scores (0-100 each)
    // ---------------------------------------------------------------

    public double urgencyScore(LocalDate dueDate) {
        if (dueDate == null) return 20.0; // no deadline = low-medium urgency
        long days = daysUntilDue(dueDate);

        if (days < 0) return 100.0;   // overdue: maximum urgency
        if (days == 0) return 95.0;   // due today
        if (days <= 1) return 85.0;
        if (days <= 3) return 70.0;
        if (days <= 7) return 50.0;
        if (days <= 14) return 30.0;
        return 15.0;
    }

    public double importanceScore(Priority priority) {
        if (priority == null) return 50.0;
        return switch (priority) {
            case CRITICAL -> 100.0;
            case HIGH -> 75.0;
            case MEDIUM -> 50.0;
            case LOW -> 25.0;
        };
    }

    /** Rewards quick wins slightly; big multi-day tasks get a smaller boost so they get scheduled, not rushed. */
    public double effortScore(Double estimatedHours) {
        if (estimatedHours == null || estimatedHours <= 0) return 50.0;
        if (estimatedHours <= 1) return 100.0;
        if (estimatedHours <= 3) return 75.0;
        if (estimatedHours <= 8) return 50.0;
        return 25.0;
    }
}
