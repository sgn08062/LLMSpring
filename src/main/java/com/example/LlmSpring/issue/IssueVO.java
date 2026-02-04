package com.example.LlmSpring.issue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Issue 테이블과 1:1 매핑되는 Value Object
 *에 정의된 컬럼 구조를 따릅니다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueVO {
    private Integer issueId;    // 이슈 PK (Auto Increment)
    private Integer projectId;  // 소속 프로젝트 ID
    private String title;       // 이슈 제목
    private String description; // 상세 내용
    private String status;      // 상태 (UNASSIGNED | IN_PROGRESS | DONE)
    private Integer priority;   // 우선순위 (CHECK 제약 조건: 0~5)
    private LocalDate dueDate;  // 마감일
    private String createdBy;   // 생성자 유저 ID
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt; // DONE 상태 변경 시 기록
    private LocalDateTime archivedAt; // 아카이브 시 기록
    private String linkedCommitSha;
    private String linkedCommitMessage;
    private String linkedCommitUrl;
    private String creatorName; // 작성자 이름
}