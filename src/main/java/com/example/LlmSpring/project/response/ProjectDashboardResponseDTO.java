package com.example.LlmSpring.project.response;

import lombok.Data;

@Data
public class ProjectDashboardResponseDTO {
    private int totalTaskCount;      // 전체 업무 수
    private int completedTaskCount;  // 완료된 업무 수
    private int inProgressTaskCount; // 진행중인 업무 수
    private int openIssueCount;      // 해결 안 된 이슈 수
    private int memberCount;         // 참여 멤버 수
}
