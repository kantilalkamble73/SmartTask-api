package com.smarttask.controller;

import com.smarttask.security.UserPrincipal;
import com.smarttask.service.DemoDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoDataService demoDataService;

    @PostMapping("/seed")
    public ResponseEntity<Map<String, Integer>> seed(@AuthenticationPrincipal UserPrincipal principal) {
        int created = demoDataService.seed(principal.getUser());
        return ResponseEntity.ok(Map.of("created", created));
    }
}
