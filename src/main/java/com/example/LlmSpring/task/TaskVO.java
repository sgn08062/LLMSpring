package com.example.LlmSpring.task;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TaskVO {
    private Long taskId;
    private String title;
    private String content;
    private String status;
    private Integer priority;
    private LocalDate dueDate;
    private String branch;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Long projectId;
    private String userId; // 작성자
}
