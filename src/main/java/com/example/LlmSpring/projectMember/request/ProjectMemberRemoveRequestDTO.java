package com.example.LlmSpring.projectMember.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProjectMemberRemoveRequestDTO {
    private String targetUserId; // 작업을 당할 유저 아이디
    private String action;       // 행위: "추방", "나가기", "초대취소"
}