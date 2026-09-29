package com.smarttask.service;

import com.smarttask.dto.AnalyticsResponse;
import com.smarttask.dto.TaskResponse;
import com.smarttask.entity.Status;
import com.smarttask.entity.Task;
import com.smarttask.entity.User;
import com.smarttask.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TaskRepository taskRepository;
    private final SmartPriorityService smartPriorityService;
    private final TaskService taskService;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("MMM d");

    public AnalyticsResponse getDashboardAnalytics(User user) {
        List<Task> tasks = taskRepository.findByUserId(user.getId());

        long total = tasks.size();
        long completed = tasks.stream().filter(t -> t.getStatus() == Status.DONE).count();
        long pending = total - completed;
        long overdue = tasks.stream().filter(smartPriorityService::isOverdue).count();
        double completionRate = total == 0 ? 0.0 : round2((completed * 10000.0) / total / 100.0);

        Double onTimeRate = computeOnTimeRate(tasks);
        Double avgCompletionDays = computeAvgCompletionDays(tasks);

        double pendingHours = tasks.stream()
                .filter(t -> t.getStatus() != Status.DONE)
                .mapToDouble(t -> t.getEstimatedHours() == null ? 1.0 : t.getEstimatedHours())
                .sum();

        LocalDate weekStart = LocalDate.now().minusDays(6);
        long completedThisWeek = tasks.stream()
                .filter(t -> t.getCompletedAt() != null && !t.getCompletedAt().toLocalDate().isBefore(weekStart))
                .count();

        Map<String, Long> byPriority = tasks.stream()
                .collect(Collectors.groupingBy(t -> t.getPriority().name(), Collectors.counting()));

        Map<String, Long> byStatus = tasks.stream()
                .collect(Collectors.groupingBy(t -> t.getStatus().name(), Collectors.counting()));

        Map<String, Long> byQuadrant = tasks.stream()
                .filter(t -> t.getStatus() != Status.DONE)
                .collect(Collectors.groupingBy(smartPriorityService::determineQuadrant, Collectors.counting()));

        Map<String, Long> byCategory = tasks.stream()
                .collect(Collectors.groupingBy(t -> (t.getCategory() == null || t.getCategory().isBlank()) ? "Uncategorized" : t.getCategory(),
                        Collectors.counting()));

        List<AnalyticsResponse.DayCount> trend = buildDayTrend(tasks, 7, DAY_FMT);
        List<AnalyticsResponse.DayCount> heatmap = buildDayTrend(tasks, 84, DateTimeFormatter.ISO_LOCAL_DATE);
        int streak = calculateStreak(tasks);

        int productivityScore = computeProductivityScore(completionRate, overdue, streak, onTimeRate);

        TaskResponse nextBest = taskService.getNextBestTask(user).orElse(null);

        List<AnalyticsResponse.Insight> insights = buildInsights(overdue, byQuadrant, nextBest, completionRate, streak, pendingHours, onTimeRate);

        return AnalyticsResponse.builder()
                .totalTasks(total)
                .completedTasks(completed)
                .pendingTasks(pending)
                .overdueTasks(overdue)
                .completionRate(completionRate)
                .onTimeRate(onTimeRate)
                .avgCompletionDays(avgCompletionDays)
                .productivityScore(productivityScore)
                .currentStreakDays(streak)
                .pendingHours(round2(pendingHours))
                .completedThisWeek(completedThisWeek)
                .tasksByPriority(byPriority)
                .tasksByStatus(byStatus)
                .tasksByQuadrant(byQuadrant)
                .tasksByCategory(byCategory)
                .productivityTrend(trend)
                .activityHeatmap(heatmap)
                .nextBestTask(nextBest)
                .insights(insights)
                .build();
    }

    private List<AnalyticsResponse.DayCount> buildDayTrend(List<Task> tasks, int daysBack, DateTimeFormatter fmt) {
        Map<LocalDate, Long> completionsByDay = tasks.stream()
                .filter(t -> t.getCompletedAt() != null)
                .collect(Collectors.groupingBy(t -> t.getCompletedAt().toLocalDate(), Collectors.counting()));

        List<AnalyticsResponse.DayCount> result = new ArrayList<>();
        for (int i = daysBack - 1; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            long count = completionsByDay.getOrDefault(day, 0L);
            result.add(new AnalyticsResponse.DayCount(day.format(fmt), count));
        }
        return result;
    }

    /** Consecutive days (ending today or yesterday) with at least one completed task. */
    private int calculateStreak(List<Task> tasks) {
        Set<LocalDate> completionDays = tasks.stream()
                .filter(t -> t.getCompletedAt() != null)
                .map(t -> t.getCompletedAt().toLocalDate())
                .collect(Collectors.toSet());

        if (completionDays.isEmpty()) return 0;

        LocalDate cursor = LocalDate.now();
        if (!completionDays.contains(cursor)) {
            cursor = cursor.minusDays(1); // streak can still count if nothing done yet today
            if (!completionDays.contains(cursor)) return 0;
        }

        int streak = 0;
        while (completionDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private Double computeOnTimeRate(List<Task> tasks) {
        List<Task> withDeadline = tasks.stream()
                .filter(t -> t.getStatus() == Status.DONE && t.getDueDate() != null && t.getCompletedAt() != null)
                .toList();
        if (withDeadline.isEmpty()) return null;
        long onTime = withDeadline.stream()
                .filter(t -> !t.getCompletedAt().toLocalDate().isAfter(t.getDueDate()))
                .count();
        return round2((onTime * 10000.0) / withDeadline.size() / 100.0);
    }

    private Double computeAvgCompletionDays(List<Task> tasks) {
        List<Task> done = tasks.stream()
                .filter(t -> t.getStatus() == Status.DONE && t.getCompletedAt() != null)
                .toList();
        if (done.isEmpty()) return null;
        double avg = done.stream()
                .mapToLong(t -> java.time.temporal.ChronoUnit.HOURS.between(t.getCreatedAt(), t.getCompletedAt()))
                .average().orElse(0) / 24.0;
        return round2(Math.max(0, avg));
    }

    /** Composite 0-100 productivity score: completion rate, overdue penalty, streak bonus, on-time bonus. */
    private int computeProductivityScore(double completionRate, long overdue, int streak, Double onTimeRate) {
        double score = completionRate * 0.5;
        score += Math.min(streak, 10) * 2.0;               // up to +20 for a 10-day streak
        score += (onTimeRate == null ? 50 : onTimeRate) * 0.2; // up to +20
        score -= Math.min(overdue, 10) * 3.0;               // up to -30 for overdue pile-up
        return (int) Math.max(0, Math.min(100, Math.round(score)));
    }

    private List<AnalyticsResponse.Insight> buildInsights(long overdue, Map<String, Long> byQuadrant, TaskResponse nextBest,
                                                            double completionRate, int streak, double pendingHours, Double onTimeRate) {
        List<AnalyticsResponse.Insight> list = new ArrayList<>();

        if (overdue > 0) {
            list.add(insight("DANGER", "You have " + overdue + " overdue task" + (overdue > 1 ? "s" : "")
                    + " — clearing these first will raise your smart score the fastest."));
        }
        long doNow = byQuadrant.getOrDefault("DO_NOW", 0L);
        if (doNow >= 3) {
            list.add(insight("WARNING", doNow + " tasks are both urgent and important right now. Consider blocking focus time today."));
        }
        if (streak >= 3) {
            list.add(insight("SUCCESS", streak + "-day completion streak — keep it going."));
        }
        if (pendingHours > 20) {
            list.add(insight("WARNING", String.format("%.0fh of open work is stacked up — some of it may be worth delegating or dropping.", pendingHours)));
        }
        if (onTimeRate != null && onTimeRate >= 90 && completionRate >= 50) {
            list.add(insight("SUCCESS", "Excellent on-time delivery rate (" + Math.round(onTimeRate) + "%)."));
        }
        if (nextBest != null && overdue == 0 && doNow == 0) {
            list.add(insight("INFO", "Nothing urgent is on fire. Good time to work ahead on \"" + nextBest.getTitle() + "\"."));
        }
        if (list.isEmpty()) {
            list.add(insight("INFO", "You're all caught up. Add a task to keep the momentum going."));
        }
        return list;
    }

    private AnalyticsResponse.Insight insight(String type, String message) {
        return AnalyticsResponse.Insight.builder().type(type).message(message).build();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
