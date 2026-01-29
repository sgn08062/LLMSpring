package com.example.LlmSpring.issue.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueDetailResponseDTO {
    private Integer issueId;
    private String title;
    private String description;
    private String status;
    private Integer priority;
    private LocalDate dueDate;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    private List<AssigneeInfoDTO> assignees; // 담당자 정보 리스트

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssigneeInfoDTO {
        private String userId;
        private String userName;
    }
}