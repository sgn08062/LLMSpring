package com.example.LlmSpring.sidebar.response;

import lombok.Data;

@Data
public class ProjectSidebarResponseDTO {
    private Long projectId;
    private String projectName;
    private boolean isReportWritten; // 오늘 리포트 썼는지 (true/false)
    private int myTaskCount; // 내 잔여 업무 개수
}
