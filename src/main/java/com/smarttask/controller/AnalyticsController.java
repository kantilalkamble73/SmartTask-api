package com.smarttask.controller;

import com.smarttask.dto.AnalyticsResponse;
import com.smarttask.security.UserPrincipal;
import com.smarttask.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<AnalyticsResponse> getDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(analyticsService.getDashboardAnalytics(principal.getUser()));
    }
}
