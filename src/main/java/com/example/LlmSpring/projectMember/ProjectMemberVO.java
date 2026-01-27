package com.example.LlmSpring.projectMember;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMemberVO {

    private Integer projectId;    // project_id (FK, PK)
    private String userId;        // user_id (FK, PK)
    private String role;          // role (OWNER | ADMIN | MEMBER)
    private String status;        // status (INVITED | ACTIVE)
    private LocalDateTime joinedAt; // joined_at
    private LocalDateTime deletedAt; // deleted_at (멤버 탈퇴/내보내기 시 기록)

}