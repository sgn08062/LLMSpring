package com.example.LlmSpring.controller;

import com.example.LlmSpring.issue.IssueService;
import com.example.LlmSpring.issue.request.IssueAssigneeRequestDTO;
import com.example.LlmSpring.issue.request.IssueCreateRequestDTO;
import com.example.LlmSpring.issue.request.IssueUpdateRequestDTO;
import com.example.LlmSpring.issue.response.IssueDetailResponseDTO;
import com.example.LlmSpring.util.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;
    private final JWTService jwtService;

    /**
     * [이슈 생성 API]
     * POST /api/projects/{projectId}/issues
     * Authorization 헤더에서 토큰을 추출하여 요청자를 식별합니다.
     */
    @PostMapping
    public ResponseEntity<?> createIssue(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("projectId") int projectId,
            @RequestBody IssueCreateRequestDTO dto) {

        // 1. 토큰에서 유저 ID 추출
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
        }

        try {
            // 2. 이슈 생성 로직 호출
            int issueId = issueService.createIssue(projectId, userId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(issueId);
        } catch (RuntimeException e) {
            // 권한 부족, 우선순위 오류 등 예외 처리
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * [이슈 아카이브 API]
     * DELETE /api/projects/{projectId}/issues/{issueId}
     */
    @DeleteMapping("/{issueId}")
    public ResponseEntity<?> archiveIssue(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("projectId") int projectId,
            @PathVariable("issueId") int issueId) {

        // 1. 토큰 검증 및 유저 ID 추출
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
        }

        try {
            // 2. 아카이브 로직 호출
            issueService.archiveIssue(projectId, issueId, userId);
            return ResponseEntity.ok("이슈가 성공적으로 아카이브되었습니다.");
        } catch (RuntimeException e) {
            // 권한 부족, 데이터 불일치 등 예외 메시지 반환
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * [이슈 담당자 추가 API]
     * POST /api/projects/{projectId}/issues/{issueId}/assignees
     */
    @PostMapping("/{issueId}/assignees")
    public ResponseEntity<?> addAssignee(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("projectId") int projectId,
            @PathVariable("issueId") int issueId,
            @RequestBody IssueAssigneeRequestDTO dto) {

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String requesterId = jwtService.verifyTokenAndUserId(token);

        if (requesterId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
        }

        try {
            issueService.addAssignee(projectId, issueId, requesterId, dto.getUserId());
            return ResponseEntity.status(HttpStatus.CREATED).body("담당자가 성공적으로 추가되었습니다.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * [이슈 담당자 제거 API]
     * DELETE /api/projects/{projectId}/issues/{issueId}/assignees/{userId}
     */
    @DeleteMapping("/{issueId}/assignees/{userId}")
    public ResponseEntity<?> removeAssignee(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("projectId") int projectId,
            @PathVariable("issueId") int issueId,
            @PathVariable("userId") String targetUserId) {

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String requesterId = jwtService.verifyTokenAndUserId(token);

        if (requesterId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
        }

        try {
            issueService.removeAssignee(projectId, issueId, requesterId, targetUserId);
            return ResponseEntity.ok("담당자가 성공적으로 제거되었습니다.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * [이슈 상세 정보 수정 API]
     * PATCH /api/projects/{projectId}/issues/{issueId}
     */
    @PatchMapping("/{issueId}")
    public ResponseEntity<?> updateIssue(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("projectId") int projectId,
            @PathVariable("issueId") int issueId,
            @RequestBody IssueUpdateRequestDTO dto) {

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증이 유효하지 않습니다.");
        }

        try {
            issueService.updateIssue(projectId, issueId, userId, dto);
            return ResponseEntity.ok("이슈가 성공적으로 수정되었습니다.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }


    /**
     * [이슈 목록 조회 API]
     * GET /api/projects/{projectId}/issues/
     */
    @GetMapping
    public ResponseEntity<?> list(@RequestHeader("Authorization") String auth,
                                  @PathVariable int projectId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) Integer priority,
                                  @RequestParam(required = false) String assigneeId,
                                  @RequestParam(required = false) String createdStart,
                                  @RequestParam(required = false) String createdEnd,
                                  @RequestParam(required = false) String dueStart,
                                  @RequestParam(required = false) String dueEnd,
                                  @RequestParam(defaultValue = "createdAt_desc") String sort) {
        String uid = jwtService.verifyTokenAndUserId(auth.replace("Bearer ", ""));
        return ResponseEntity.ok(issueService.getIssueList(
                projectId, uid, status, priority, assigneeId,
                createdStart, createdEnd, dueStart, dueEnd, sort
        ));
    }

    /**
     * [이슈 상세 조회 API]
     * GET /api/projects/{projectId}/issues/{issueId}
     */
    @GetMapping("/{issueId}")
    public ResponseEntity<?> getIssueDetail(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("projectId") int projectId,
            @PathVariable("issueId") int issueId) {

        String token = authHeader.replace("Bearer ", "");
        String userId = jwtService.verifyTokenAndUserId(token);

        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            IssueDetailResponseDTO response = issueService.getIssueDetail(projectId, issueId, userId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            // "삭제된 이슈입니다" 또는 "찾을 수 없습니다" 등의 메시지 반환
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }


}