package com.smarttask.service;

import com.smarttask.entity.*;
import com.smarttask.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Seeds a fresh account with realistic sample tasks so the dashboard, charts and planner are worth looking at immediately. */
@Service
@RequiredArgsConstructor
public class DemoDataService {

    private final TaskRepository taskRepository;

    public int seed(User user) {
        record Seed(String title, int dueOffset, Priority priority, String category, Double hours, boolean completed, int completedDaysAgo) {}

        List<Seed> seeds = List.of(
                new Seed("Fix production login bug", -1, Priority.CRITICAL, "Work", 1.5, false, 0),
                new Seed("Prepare client demo deck", 0, Priority.HIGH, "Work", 2.0, false, 0),
                new Seed("Reply to investor email", 1, Priority.HIGH, "Work", 0.5, false, 0),
                new Seed("Quarterly roadmap planning", 6, Priority.HIGH, "Work", 3.0, false, 0),
                new Seed("Renew car insurance", 3, Priority.MEDIUM, "Personal", 0.5, false, 0),
                new Seed("Book dentist appointment", 10, Priority.LOW, "Personal", 0.25, false, 0),
                new Seed("Read 'Atomic Habits' chapter 4", 14, Priority.LOW, "Personal", 1.0, false, 0),
                new Seed("Refactor authentication module", 4, Priority.MEDIUM, "Work", 5.0, false, 0),
                new Seed("Team 1:1 with designer", 2, Priority.MEDIUM, "Work", 0.5, false, 0),
                new Seed("Submit expense report", -3, Priority.MEDIUM, "Work", 0.5, false, 0),
                new Seed("Morning workout", 0, Priority.MEDIUM, "Health", 1.0, true, 0),
                new Seed("Pay electricity bill", -2, Priority.HIGH, "Personal", 0.25, true, 1),
                new Seed("Weekly grocery run", -1, Priority.LOW, "Personal", 1.0, true, 1),
                new Seed("Code review for PR #482", -2, Priority.MEDIUM, "Work", 0.75, true, 2),
                new Seed("Write sprint retro notes", -3, Priority.LOW, "Work", 0.5, true, 3),
                new Seed("Update project README", -4, Priority.LOW, "Work", 0.5, true, 4)
        );

        int count = 0;
        for (Seed s : seeds) {
            Task task = Task.builder()
                    .title(s.title())
                    .priority(s.priority())
                    .category(s.category())
                    .estimatedHours(s.hours())
                    .dueDate(LocalDate.now().plusDays(s.dueOffset()))
                    .status(s.completed() ? Status.DONE : Status.TODO)
                    .recurrence(Recurrence.NONE)
                    .user(user)
                    .build();
            if (s.completed()) {
                task.setCompletedAt(LocalDateTime.now().minusDays(s.completedDaysAgo()));
            }
            taskRepository.save(task);
            count++;
        }
        return count;
    }
}
