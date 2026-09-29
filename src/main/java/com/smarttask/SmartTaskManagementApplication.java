package com.smarttask;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Smart Task Management System
 * -----------------------------
 * Entry point of the application. This backend exposes a REST API that
 * combines standard task CRUD with a "smart engine" that automatically:
 *   1. Scores every task (urgency + importance + effort) -> smartScore
 *   2. Classifies tasks into an Eisenhower quadrant (Do Now / Schedule / Delegate / Eliminate)
 *   3. Recommends the single "next best task" to work on
 *   4. Produces productivity analytics (completion rate, streaks, trends)
 */
@SpringBootApplication
public class SmartTaskManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartTaskManagementApplication.class, args);
    }
}
