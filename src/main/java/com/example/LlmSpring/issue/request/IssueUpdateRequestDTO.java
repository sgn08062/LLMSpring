package com.example.LlmSpring.issue.request;

import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이슈 정보 수정을 위한 DTO
 * 전달된 필드만 수정하기 위해 래퍼 클래스(Integer)를 사용합니다.
 */

@Getter
@NoArgsConstructor
public class IssueUpdateRequestDTO {
    private String title;
    private String description;
    private Integer priority; // null 체크를 위해 Integer 사용
    private LocalDate dueDate;
    private String status;    // UNASSIGNED | IN_PROGRESS | DONE
    private String linkedCommitSha;
    private String linkedCommitMessage;
    private String linkedCommitUrl;
}