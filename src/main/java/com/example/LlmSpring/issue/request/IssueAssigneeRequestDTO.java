package com.example.LlmSpring.issue.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IssueAssigneeRequestDTO {
    private String userId; // 추가할 담당자 ID
}