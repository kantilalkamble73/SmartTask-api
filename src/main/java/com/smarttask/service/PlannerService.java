package com.smarttask.service;

import com.smarttask.dto.PlanResponse;
import com.smarttask.dto.TaskResponse;
import com.smarttask.entity.Status;
import com.smarttask.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the ranked task list into a realistic plan for today.
 *
 * Algorithm (greedy scheduling with hard constraints):
 *  1. "Must-do" tasks (overdue or due today) are always planned first, even if they exceed capacity
 *     - in which case the plan is flagged as overloaded so the user can renegotiate deadlines.
 *  2. Remaining capacity is filled with the highest smart-score tasks that still fit.
 *  3. Very large tasks (> 4h) are planned as a 2h work slice instead of blocking the whole day.
 *  4. Everything that does not fit is returned as "deferred".
 */
@Service
@RequiredArgsConstructor
public class PlannerService {

    private static final int DAY_START_MINUTES = 9 * 60;
    private static final int BREAK_MINUTES = 10;
    private static final double SLICE_HOURS = 2.0;
    private static final double MAX_UNSPLIT_HOURS = 4.0;

    private final TaskService taskService;

    public PlanResponse buildPlan(User user, double requestedHours) {
        double capacity = Math.max(1.0, Math.min(12.0, requestedHours));

        List<TaskResponse> open = taskService.getAllTasksForUser(user).stream()
                .filter(t -> t.getStatus() != Status.DONE)
                .toList(); // already sorted by smart score, highest first

        List<TaskResponse> must = new ArrayList<>();
        List<TaskResponse> optional = new ArrayList<>();
        for (TaskResponse t : open) {
            if (isMustDo(t)) must.add(t);
            else optional.add(t);
        }

        List<PlanResponse.PlanItem> items = new ArrayList<>();
        List<TaskResponse> deferred = new ArrayList<>();
        double used = 0;
        int cursor = DAY_START_MINUTES;

        for (TaskResponse t : must) {
            double h = blockHours(t);
            items.add(item(t, h, cursor, true));
            cursor += (int) Math.round(h * 60) + BREAK_MINUTES;
            used += h;
        }

        boolean overloaded = used > capacity + 1e-9;

        for (TaskResponse t : optional) {
            double h = blockHours(t);
            if (used + h <= capacity + 1e-9) {
                items.add(item(t, h, cursor, false));
                cursor += (int) Math.round(h * 60) + BREAK_MINUTES;
                used += h;
            } else {
                deferred.add(t);
            }
        }

        return PlanResponse.builder()
                .capacityHours(capacity)
                .plannedHours(Math.round(used * 100.0) / 100.0)
                .overloaded(overloaded)
                .message(buildMessage(items.size(), used, capacity, overloaded, must.size(), deferred.size()))
                .plan(items)
                .deferred(deferred)
                .build();
    }

    private boolean isMustDo(TaskResponse t) {
        return t.isOverdue() || (t.getDueDate() != null && t.getDaysUntilDue() == 0);
    }

    private double blockHours(TaskResponse t) {
        Double e = t.getEstimatedHours();
        double h = (e == null || e <= 0) ? 1.0 : e;
        return h > MAX_UNSPLIT_HOURS ? SLICE_HOURS : h;
    }

    private PlanResponse.PlanItem item(TaskResponse t, double hours, int startMinutes, boolean mustDo) {
        int end = startMinutes + (int) Math.round(hours * 60);
        Double e = t.getEstimatedHours();
        return PlanResponse.PlanItem.builder()
                .task(t)
                .start(fmt(startMinutes))
                .end(fmt(end))
                .hours(hours)
                .mustDo(mustDo)
                .slice(e != null && e > MAX_UNSPLIT_HOURS)
                .build();
    }

    private String fmt(int minutes) {
        return String.format("%02d:%02d", (minutes / 60) % 24, minutes % 60);
    }

    private String buildMessage(int planned, double used, double capacity, boolean overloaded, int mustCount, int deferred) {
        if (planned == 0) return "Nothing to plan. Add a task or enjoy the free day.";
        if (overloaded) {
            return String.format("Deadlines alone need %.1fh but you only have %.1fh. Consider moving some tasks or extending your day.", used, capacity);
        }
        String base = String.format("%d task%s planned, %.1fh of %.1fh used.", planned, planned == 1 ? "" : "s", used, capacity);
        if (mustCount > 0) base += " " + mustCount + " must be done today.";
        if (deferred > 0) base += " " + deferred + " deferred to later.";
        return base;
    }
}
