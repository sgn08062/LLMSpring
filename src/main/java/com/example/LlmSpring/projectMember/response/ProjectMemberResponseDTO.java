package com.example.LlmSpring.projectMember.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberResponseDTO {
    private String userId;     // 사용자 ID
    private String name;       // 사용자 이름 (User 테이블 조인)
    private String email;      // 사용자 이메일 (User 테이블 조인)
    private String filePath;   // 프로필 이미지 경로 (User 테이블 조인)
    private String role;       // 역할 (OWNER, ADMIN, MEMBER)
    private String status;     // 상태 (INVITED, ACTIVE, me)
    private LocalDateTime joinedAt; // 참여일
}