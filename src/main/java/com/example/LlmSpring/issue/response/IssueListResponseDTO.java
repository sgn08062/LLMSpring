package com.example.LlmSpring.issue.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이슈 목록 조회를 위한 응답 DTO
 * 각 이슈의 기본 정보와 담당자 목록(assignees)을 포함합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueListResponseDTO {
    private Integer issueId;
    private String title;
    private String status;
    private Integer priority;
    private LocalDate dueDate;
    private String createdBy;
    private LocalDateTime createdAt;
    private List<String> assignees;
}