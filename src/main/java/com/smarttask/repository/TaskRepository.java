package com.smarttask.repository;

import com.smarttask.entity.Status;
import com.smarttask.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUserId(Long userId);

    List<Task> findByUserIdAndStatus(Long userId, Status status);

    List<Task> findByUserIdAndDueDateBeforeAndStatusNot(Long userId, LocalDate date, Status status);

    List<Task> findByUserIdAndCompletedAtBetween(Long userId, java.time.LocalDateTime start, java.time.LocalDateTime end);

    long countByUserIdAndStatus(Long userId, Status status);
}
