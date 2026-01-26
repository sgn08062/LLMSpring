package com.example.LlmSpring.task;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskCheckListVO {
    private Long checkListId;
    private String content;
    private Boolean status; // true: 완료
    private LocalDateTime createAt;
    private Long taskId;
}
