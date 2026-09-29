package com.smarttask.service;

import com.smarttask.dto.TaskRequest;
import com.smarttask.dto.TaskResponse;
import com.smarttask.entity.Priority;
import com.smarttask.entity.Recurrence;
import com.smarttask.entity.Status;
import com.smarttask.entity.Task;
import com.smarttask.entity.User;
import com.smarttask.exception.BadRequestException;
import com.smarttask.exception.ResourceNotFoundException;
import com.smarttask.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final SmartPriorityService smartPriorityService;
    private final QuickAddParser quickAddParser;

    public List<TaskResponse> getAllTasksForUser(User user) {
        return taskRepository.findByUserId(user.getId()).stream()
                .map(this::toResponse)
                // Smart sort: highest smart score (most important+urgent) first.
                .sorted(Comparator.comparing(TaskResponse::getSmartScore).reversed())
                .toList();
    }

    public TaskResponse getTaskById(Long id, User user) {
        return toResponse(findOwnedTask(id, user));
    }

    public TaskResponse createTask(TaskRequest request, User user) {
        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .dueDate(request.getDueDate())
                .priority(request.getPriority() != null ? request.getPriority() : Priority.MEDIUM)
                .status(request.getStatus() != null ? request.getStatus() : Status.TODO)
                .category(request.getCategory())
                .estimatedHours(request.getEstimatedHours())
                .recurrence(request.getRecurrence() != null ? request.getRecurrence() : Recurrence.NONE)
                .user(user)
                .build();

        return toResponse(taskRepository.save(task));
    }

    /** Creates a task from a free-text sentence, e.g. "Submit report friday high 2h #work". */
    public TaskResponse quickAdd(String text, User user) {
        TaskRequest parsed = quickAddParser.parse(text);
        if (parsed.getTitle() == null || parsed.getTitle().isBlank()) {
            throw new BadRequestException("Add a title to your task, e.g. \"Call the bank tomorrow\"");
        }
        return createTask(parsed, user);
    }

    public TaskRequest previewQuickAdd(String text) {
        return quickAddParser.parse(text);
    }

    public TaskResponse updateTask(Long id, TaskRequest request, User user) {
        Task task = findOwnedTask(id, user);

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setDueDate(request.getDueDate());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        if (request.getCategory() != null) task.setCategory(request.getCategory());
        if (request.getRecurrence() != null) task.setRecurrence(request.getRecurrence());
        task.setEstimatedHours(request.getEstimatedHours());

        boolean justCompleted = applyStatusChange(task, request.getStatus());
        Task saved = taskRepository.save(task);
        if (justCompleted) spawnNextOccurrence(saved);
        return toResponse(saved);
    }

    public TaskResponse updateStatus(Long id, Status newStatus, User user) {
        Task task = findOwnedTask(id, user);
        boolean justCompleted = applyStatusChange(task, newStatus);
        Task saved = taskRepository.save(task);
        if (justCompleted) spawnNextOccurrence(saved);
        return toResponse(saved);
    }

    public TaskResponse reschedule(Long id, LocalDate newDate, User user) {
        Task task = findOwnedTask(id, user);
        task.setDueDate(newDate);
        return toResponse(taskRepository.save(task));
    }

    public void deleteTask(Long id, User user) {
        taskRepository.delete(findOwnedTask(id, user));
    }

    /** The single highest-value task the user should work on right now. */
    public Optional<TaskResponse> getNextBestTask(User user) {
        return taskRepository.findByUserId(user.getId()).stream()
                .filter(t -> t.getStatus() != Status.DONE)
                .map(this::toResponse)
                .max(Comparator.comparing(TaskResponse::getSmartScore));
    }

    public List<TaskResponse> getOverdueTasks(User user) {
        return taskRepository.findByUserIdAndDueDateBeforeAndStatusNot(
                        user.getId(), LocalDate.now(), Status.DONE).stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(TaskResponse::getSmartScore).reversed())
                .toList();
    }

    // ------------------------------------------------------------------

    /** @return true if this change moved the task into DONE for the first time. */
    private boolean applyStatusChange(Task task, Status newStatus) {
        if (newStatus == null) return false;
        Status previous = task.getStatus();
        task.setStatus(newStatus);
        if (newStatus == Status.DONE && previous != Status.DONE) {
            task.setCompletedAt(LocalDateTime.now());
            return true;
        }
        if (newStatus != Status.DONE) {
            task.setCompletedAt(null);
        }
        return false;
    }

    /** Recurring tasks: completing one automatically creates the next occurrence. */
    private void spawnNextOccurrence(Task done) {
        Recurrence r = done.getRecurrence();
        if (r == null || r == Recurrence.NONE) return;

        LocalDate next = advance(done.getDueDate() != null ? done.getDueDate() : LocalDate.now(), r);
        while (next.isBefore(LocalDate.now())) {
            next = advance(next, r); // completed late: roll forward to the next future slot
        }

        Task copy = Task.builder()
                .title(done.getTitle())
                .description(done.getDescription())
                .priority(done.getPriority())
                .category(done.getCategory())
                .estimatedHours(done.getEstimatedHours())
                .recurrence(r)
                .dueDate(next)
                .status(Status.TODO)
                .user(done.getUser())
                .build();
        taskRepository.save(copy);
    }

    private LocalDate advance(LocalDate date, Recurrence r) {
        return switch (r) {
            case DAILY -> date.plusDays(1);
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
            case NONE -> date;
        };
    }

    private Task findOwnedTask(Long id, User user) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        if (!task.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Task not found with id: " + id);
        }
        return task;
    }

    private TaskResponse toResponse(Task task) {
        long days = smartPriorityService.daysUntilDue(task.getDueDate());

        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .dueDate(task.getDueDate())
                .priority(task.getPriority())
                .status(task.getStatus())
                .category(task.getCategory())
                .estimatedHours(task.getEstimatedHours())
                .recurrence(task.getRecurrence() == null ? Recurrence.NONE : task.getRecurrence())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .completedAt(task.getCompletedAt())
                .smartScore(smartPriorityService.calculateSmartScore(task))
                .quadrant(smartPriorityService.determineQuadrant(task))
                .overdue(smartPriorityService.isOverdue(task))
                .daysUntilDue(task.getDueDate() == null ? 0 : days)
                .urgencyScore(smartPriorityService.urgencyScore(task.getDueDate()))
                .importanceScore(smartPriorityService.importanceScore(task.getPriority()))
                .effortScore(smartPriorityService.effortScore(task.getEstimatedHours()))
                .reason(smartPriorityService.explain(task))
                .build();
    }
}
