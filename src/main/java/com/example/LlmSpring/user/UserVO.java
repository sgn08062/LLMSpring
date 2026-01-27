package com.example.LlmSpring.user;

import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserVO {
    private String userId;
    private String passwordHash;
    private String name;
    private String email;
    private LocalDateTime regDate;
    private String filePath;
    private LocalDateTime deletedAt;
    private String githubId;
    private String githubToken;
}
