package com.example.LlmSpring.alarm;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    private Boolean isRead;

    // [수정] 날짜 형식을 문자열로 고정 (프론트 계산 오류 방지)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    private String senderName;
    private String senderFilePath;
    private String projectName;
}