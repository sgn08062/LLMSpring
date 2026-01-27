package com.example.LlmSpring.task;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskChatVO {
    private Long chatId;
    private String content;
    private LocalDateTime date;
    private Long taskId;
    private String userId;
}
