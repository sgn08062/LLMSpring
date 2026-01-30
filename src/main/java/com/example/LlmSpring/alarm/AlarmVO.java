package com.example.LlmSpring.alarm;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AlarmVO {
    private int alarmId;
    private String userId;
    private String senderId;
    private Integer projectId;
    private String type;
    private Integer referenceId;
    private String content;
    private String url;
    private boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;

//    조인해서 가져올 추가 정보들
    private String senderName;
    private String senderFilePath;
    private String projectName;
}
