package com.example.LlmSpring.task;

import com.fasterxml.jackson.annotation.JsonFormat; // import 추가
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskLogVO {
    private Long logId;
    private String type;
    private String content;
    private String metaData;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime createAt;

    private Long taskId;
    private String userId;
}