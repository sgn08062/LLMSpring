package com.example.LlmSpring.task;

import java.time.LocalDateTime;

public class TaskLogVO {
    private Long logId;
    private String type;
    private String content;
    private String metaData;
    private LocalDateTime createAt;
    private Long taskId;
    private String userId;
}
