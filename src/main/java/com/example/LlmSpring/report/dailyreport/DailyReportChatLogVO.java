package com.example.LlmSpring.report.dailyreport;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DailyReportChatLogVO {
    private Long logId;
    private Boolean role; // 0(false): AI, 1(true): User
    private String message;
    private LocalDateTime createdAt;
    private String suggestionContent; //AI가 제안한 수정 내용
    private Boolean isApplied; //적용 여부
    private Long reportId;
}
