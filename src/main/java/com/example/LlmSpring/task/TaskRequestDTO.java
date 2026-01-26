package com.example.LlmSpring.task;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class TaskRequestDTO {
    private String title;
    private String content;
    private List<String> assigneeIds; // 담당자 ID 목록
    private String branch;
    private Integer priority;
    private LocalDate dueDate;
    private String status;
}
