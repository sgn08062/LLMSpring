package com.example.LlmSpring.controller;

import com.example.LlmSpring.user.UserService;
import com.example.LlmSpring.user.response.UserSearchResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 멤버 초대 후보군 검색 API
     * GET /api/users/search?keyword=...&myUserId=...
     * * @param keyword 검색 키워드 (ID 또는 이름)
     * @param myUserId 요청자 본인의 ID (결과 제외용)
     * @return 검색된 유저 정보 리스트
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(
            @RequestParam("keyword") String keyword,
            @RequestParam("myUserId") String myUserId) {

        try {
            // 1. 필수 파라미터 검증 (400 에러 처리)
            if (keyword == null || keyword.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("검색어를 입력해주세요.");
            }
            if (myUserId == null || myUserId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("요청자 식별 정보가 없습니다.");
            }

            // 2. 서비스 호출 및 결과 반환
            List<UserSearchResponseDTO> results = userService.searchUsersForInvitation(keyword, myUserId);
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            // 3. 서버 내부 오류 발생 시 (500 에러 처리)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("유저 검색 중 서버 오류가 발생했습니다.");
        }
    }
}
