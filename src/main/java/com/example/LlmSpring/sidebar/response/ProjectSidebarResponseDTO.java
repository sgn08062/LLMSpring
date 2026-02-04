package com.example.LlmSpring.sidebar.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProjectSidebarResponseDTO {
    private Long projectId;
    private String projectName;
    private String projectStatus;
    private String dailyReportTime;
    private boolean isReportWritten; // 오늘 리포트 썼는지 (true/false)
    private List<SidebarTaskDTO> myTasks;
    private List<SidebarIssueDTO> myIssues;

    @Data
    public static class SidebarTaskDTO {
        private Long taskId;
        private String title;
        private String status;
        private Integer priority;
        private LocalDateTime dueDate;
    }

    @Data
    public static class SidebarIssueDTO {
        private Long issueId;
        private String title;
        private String status;
        private String priority;
    }
}
