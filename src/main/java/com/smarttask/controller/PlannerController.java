package com.smarttask.controller;

import com.smarttask.dto.PlanResponse;
import com.smarttask.security.UserPrincipal;
import com.smarttask.service.PlannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "What should I do today?" — builds a realistic time-boxed plan out of the ranked task list. */
@RestController
@RequestMapping("/api/planner")
@RequiredArgsConstructor
public class PlannerController {

    private final PlannerService plannerService;

    @GetMapping("/today")
    public ResponseEntity<PlanResponse> getTodayPlan(@RequestParam(defaultValue = "6") double hours,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(plannerService.buildPlan(principal.getUser(), hours));
    }
}
