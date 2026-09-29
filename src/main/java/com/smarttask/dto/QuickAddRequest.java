package com.smarttask.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QuickAddRequest {
    @NotBlank(message = "Type something to add a task")
    private String text;
}
