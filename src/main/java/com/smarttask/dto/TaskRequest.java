package com.smarttask.dto;

import com.smarttask.entity.Priority;
import com.smarttask.entity.Recurrence;
import com.smarttask.entity.Status;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TaskRequest {
    @NotBlank(message = "Title is required")
    private String title;

    private String description;
    private LocalDate dueDate;
    private Priority priority;
    private Status status;
    private String category;
    private Double estimatedHours;
    private Recurrence recurrence;
}
