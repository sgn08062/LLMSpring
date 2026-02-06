package com.example.LlmSpring.report.dailyreport;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DailyReportVO {
    private Long reportId;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate reportDate;

    private String title;
    private String content;
    private String summary; // 3줄 요약
    private Boolean originalContent; // true: 초안, false: 수정됨
    private String status; // DRAFT(작성중), PUBLISHED(발행됨)
    private Integer commitCount;
    private LocalDateTime updatedAt;
    private String drFilePath;
    private Boolean isPublished;
    private Long projectId;
    private String userId;
    private String role;
}
