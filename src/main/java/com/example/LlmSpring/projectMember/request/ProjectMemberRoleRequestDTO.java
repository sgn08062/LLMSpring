package com.example.LlmSpring.projectMember.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProjectMemberRoleRequestDTO {
    private String targetUserId; // 역할을 변경할 대상 ID
    private String role;         // 변경하고자 하는 역할 (ADMIN, MEMBER)
}
