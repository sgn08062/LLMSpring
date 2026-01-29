package com.example.LlmSpring.user.response;

import lombok.Builder;
import lombok.Data;

/**
 * 프로젝트 초대 후보 유저 검색 결과를 담는 응답 DTO
 * 보안을 위해 비밀번호 등 민감 정보는 제외하고 식별 및 출력에 필요한 필드만 포함합니다.
 */

@Data
@Builder
public class UserSearchResponseDTO {
    private String userId; // 유저 식별자 (초대 리스트에 담길 핵심 ID)
    private String name;   // 화면 표시용 이름
    private String filePath;
}
