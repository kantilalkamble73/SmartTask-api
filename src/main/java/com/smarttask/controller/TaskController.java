package com.smarttask.controller;

import com.smarttask.dto.QuickAddRequest;
import com.smarttask.dto.TaskRequest;
import com.smarttask.dto.TaskResponse;
import com.smarttask.entity.Status;
import com.smarttask.entity.User;
import com.smarttask.security.UserPrincipal;
import com.smarttask.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getAllTasks(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskService.getAllTasksForUser(user(principal)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskService.getTaskById(id, user(principal)));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody TaskRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskService.createTask(request, user(principal)));
    }

    /** Natural-language quick add, e.g. {"text": "Submit report friday high 2h #work"}. */
    @PostMapping("/quick-add")
    public ResponseEntity<TaskResponse> quickAdd(@Valid @RequestBody QuickAddRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskService.quickAdd(request.getText(), user(principal)));
    }

    /** Lets the UI show a live preview of how quick-add text will be parsed, without saving it. */
    @GetMapping("/quick-add/preview")
    public ResponseEntity<TaskRequest> previewQuickAdd(@RequestParam String text) {
        return ResponseEntity.ok(taskService.previewQuickAdd(text));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(@PathVariable Long id,
                                                    @Valid @RequestBody TaskRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskService.updateTask(id, request, user(principal)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TaskResponse> updateStatus(@PathVariable Long id,
                                                      @RequestBody Map<String, String> body,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        Status status = Status.valueOf(body.get("status").toUpperCase());
        return ResponseEntity.ok(taskService.updateStatus(id, status, user(principal)));
    }

    /** Drag-and-drop reschedule from the calendar / matrix view: {"dueDate": "2026-10-05"}. */
    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<TaskResponse> reschedule(@PathVariable Long id,
                                                    @RequestBody Map<String, String> body,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        String raw = body.get("dueDate");
        LocalDate date = (raw == null || raw.isBlank()) ? null : LocalDate.parse(raw);
        return ResponseEntity.ok(taskService.reschedule(id, date, user(principal)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        taskService.deleteTask(id, user(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/next-best")
    public ResponseEntity<TaskResponse> getNextBestTask(@AuthenticationPrincipal UserPrincipal principal) {
        return taskService.getNextBestTask(user(principal))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<TaskResponse>> getOverdueTasks(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(taskService.getOverdueTasks(user(principal)));
    }

    private User user(UserPrincipal principal) {
        return principal.getUser();
    }
}
