package com.example.LlmSpring.controller;

import com.example.LlmSpring.user.UserService;
import com.example.LlmSpring.user.response.UserSearchResponseDTO;
import com.example.LlmSpring.util.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JWTService jwtService;

    /**
     * 멤버 초대 후보군 검색 API
     * GET /api/users/search?keyword=...&myUserId=...
     * * @param keyword 검색 키워드 (ID 또는 이름)
     * @param : myUserId 요청자 본인의 ID (결과 제외용)
     * @return 검색된 유저 정보 리스트
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("keyword") String keyword) {

        try {
            // 1. 토큰 추출 및 검증
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            String myUserId = jwtService.verifyTokenAndUserId(token);

            // 2. 토큰이 유효하지 않은 경우 (우리 서비스 유저가 아님)
            if (myUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않거나 만료된 토큰입니다.");
            }

            // 3. 필수 파라미터 검증
            if (keyword == null || keyword.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("검색어를 입력해주세요.");
            }

            // 4. 서비스 호출 및 결과 반환 (추출된 myUserId 전달)
            List<UserSearchResponseDTO> results = userService.searchUsersForInvitation(keyword, myUserId);
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("유저 검색 중 서버 오류가 발생했습니다.");
        }
    }
}
