package com.example.LlmSpring.project;

import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 프로젝트 기본 정보를 담는 Value Object
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectVO {
    private Integer projectId;           // 프로젝트 고유 식별자 (PK)
    private String name;                // 프로젝트 명
    private String description;         // 프로젝트 상세 설명
    private LocalDateTime startDate;    // 프로젝트 시작 일시
    private LocalDateTime endDate;      // 프로젝트 마감 일시
    private String status;              // 프로젝트 상태 (ACTIVE: 진행 중, DONE: 완료)
    private Integer progress;           // 진행률
    private String githubRepoUrl;       // 연결된 GitHub 저장소 URL
    private String githubDefaultBranch; // GitHub 기본 브랜치 명
    private String githubConnectedStatus; // GitHub 연결 상태
    private LocalTime dailyReportTime;  // 일일 보고 알림 시간
    private LocalDateTime deletedAt;    // 삭제 유예 종료 일시 (NULL이면 미삭제 상태)
    private LocalDateTime updatedAt;    // 최종 수정 일시
}