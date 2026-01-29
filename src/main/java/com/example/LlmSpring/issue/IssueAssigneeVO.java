package com.example.LlmSpring.issue;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueAssigneeVO {
    private Integer issueId;
    private Integer projectId;
    private String userId;
    private LocalDateTime assignedAt;
}