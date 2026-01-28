package com.example.LlmSpring.sidebar.response;

import lombok.Data;

import java.util.List;

@Data
public class ProjectSidebarResponseDTO {
    private Long projectId;
    private String projectName;
    private String projectStatus;
    private String dailyReportTime;
    private boolean isReportWritten; // 오늘 리포트 썼는지 (true/false)
    private List<SidebarTaskDTO> myTasks;

    @Data
    public static class SidebarTaskDTO {
        private Long taskId;
        private String title;
        private Integer priority;
    }
}
