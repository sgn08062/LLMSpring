package com.example.LlmSpring.dailyreport;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DailyReportVO {
    private Long reportId;
    private LocalDate reportDate;
    private String title;
    private String content;
    private String summary; //3줄 요약
    private Boolean originalContent; //true: 초안, false: 수정됨
    private String status; //DRAFT(작성중), //PUBLISHED(발행됨)
    private Integer commitCount;
    private LocalDateTime updatedAt;
    private String drFilePath;
    private Boolean isPublished;
    private Long projectId;
    private String userId;
}
