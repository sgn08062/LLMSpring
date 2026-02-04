package com.example.LlmSpring.issue.request;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이슈 생성을 위한 요청 DTO
 * 클라이언트로부터 제목, 설명, 우선순위, 마감일, 담당자 목록을 수신합니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IssueCreateRequestDTO {
    private String title;          // 이슈 제목 (필수)
    private String description;    // 이슈 상세 설명 (NULL 허용)
    private int priority;          // 우선순위 (0~5 사이 정수)
    private LocalDate dueDate;     // 마감 기한
    private List<String> assigneeIds; // 담당자 ID 리스트 (NULL 또는 빈 배열 가능)
    private String linkedCommitSha;
    private String linkedCommitMessage;
    private String linkedCommitUrl;
}