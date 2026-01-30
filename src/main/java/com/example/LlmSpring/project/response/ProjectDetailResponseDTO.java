package com.example.LlmSpring.project.response;

import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProjectDetailResponseDTO {
    private Integer projectId;
    private String name;
    private String description;         // 상세 설명
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status;
    private String githubRepoUrl;       // 깃허브 주소
    private LocalTime dailyReportTime;  // 리포트 시간
    // 필요 시 깃허브 관련 추가 정보 포함
    private String githubDefaultBranch;
    private String githubConnectedStatus;
}