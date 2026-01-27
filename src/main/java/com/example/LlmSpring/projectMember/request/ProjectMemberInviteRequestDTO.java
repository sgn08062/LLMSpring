package com.example.LlmSpring.projectMember.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProjectMemberInviteRequestDTO {
    private String userId; // 초대할 사용자 ID
}
