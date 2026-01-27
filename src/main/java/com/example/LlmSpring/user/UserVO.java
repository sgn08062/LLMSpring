package com.example.LlmSpring.user;

import lombok.*;
import java.time.LocalDateTime;


/**
 * User 테이블 매핑 VO
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserVO {
    private String userId;        // 유저 ID (PK)
    private String passwordHash;  // 비밀번호 해시
    private String name;          // 유저 이름
    private String email;         // 이메일
    private LocalDateTime regDate; // 가입일
    private String filePath;      // 프로필 이미지 경로
    private String githubId;      // 깃허브 식별 ID
    private String githubToken;   // 깃허브 API 토큰
    private LocalDateTime deletedAt; // 탈퇴 일시 (NULL이면 활성 유저)
}