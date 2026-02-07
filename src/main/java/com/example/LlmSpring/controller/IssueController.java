package com.example.LlmSpring.controller;

import com.example.LlmSpring.issue.IssueService;
import com.example.LlmSpring.issue.request.IssueAssigneeRequestDTO;
import com.example.LlmSpring.issue.request.IssueCreateRequestDTO;
import com.example.LlmSpring.issue.request.IssueUpdateRequestDTO;
import com.example.LlmSpring.issue.response.IssueDetailResponseDTO;
import com.example.LlmSpring.project.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 추가됨
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;
    private final ProjectAccessService projectAccessService;

    /**
     * [이슈 생성 API]
     * POST /api/projects/{projectId}/issues
     */
    @PostMapping
    public ResponseEntity<?> createIssue(
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") int projectId,
            @RequestBody IssueCreateRequestDTO dto) {

        // [쓰기 권한] DONE 또는 DELETE 상태면 접근 불가
        projectAccessService.validateWriteAccess((long) projectId, userId);

        try {
            int issueId = issueService.createIssue(projectId, userId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(issueId);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * [이슈 아카이브 API]
     * DELETE /api/projects/{projectId}/issues/{issueId}
     */
    @DeleteMapping("/{issueId}")
    public ResponseEntity<?> archiveIssue(
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") int projectId,
            @PathVariable("issueId") int issueId) {

        // [쓰기 권한] DONE 또는 DELETE 상태면 접근 불가
        projectAccessService.validateWriteAccess((long) projectId, userId);

        try {
            issueService.archiveIssue(projectId, issueId, userId);
            return ResponseEntity.ok("이슈가 성공적으로 아카이브되었습니다.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * [이슈 담당자 추가 API]
     * POST /api/projects/{projectId}/issues/{issueId}/assignees
     */
    @PostMapping("/{issueId}/assignees")
    public ResponseEntity<?> addAssignee(
            @AuthenticationPrincipal String requesterId,
            @PathVariable("projectId") int projectId,
            @PathVariable("issueId") int issueId,
            @RequestBody IssueAssigneeRequestDTO dto) {

        // [쓰기 권한] DONE 또는 DELETE 상태면 접근 불가
        projectAccessService.validateWriteAccess((long) projectId, requesterId);

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
            @AuthenticationPrincipal String requesterId,
            @PathVariable("projectId") int projectId,
            @PathVariable("issueId") int issueId,
            @PathVariable("userId") String targetUserId) {

        // [쓰기 권한] DONE 또는 DELETE 상태면 접근 불가
        projectAccessService.validateWriteAccess((long) projectId, requesterId);

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
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") int projectId,
            @PathVariable("issueId") int issueId,
            @RequestBody IssueUpdateRequestDTO dto) {

        // [쓰기 권한] DONE 또는 DELETE 상태면 접근 불가
        projectAccessService.validateWriteAccess((long) projectId, userId);

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
    public ResponseEntity<?> list(@AuthenticationPrincipal String uid,
                                  @PathVariable int projectId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) Integer priority,
                                  @RequestParam(required = false) String assigneeId,
                                  @RequestParam(required = false) String createdStart,
                                  @RequestParam(required = false) String createdEnd,
                                  @RequestParam(required = false) String dueStart,
                                  @RequestParam(required = false) String dueEnd,
                                  @RequestParam(defaultValue = "createdAt_desc") String sort) {


        // [읽기 권한] DELETE 상태면 OWNER만 접근 가능
        projectAccessService.validateReadAccess((long) projectId, uid);

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
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") int projectId,
            @PathVariable("issueId") int issueId) {

        // [읽기 권한] DELETE 상태면 OWNER만 접근 가능
        projectAccessService.validateReadAccess((long) projectId, userId);

        try {
            IssueDetailResponseDTO response = issueService.getIssueDetail(projectId, issueId, userId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            // "삭제된 이슈입니다" 또는 "찾을 수 없습니다" 등의 메시지 반환
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}