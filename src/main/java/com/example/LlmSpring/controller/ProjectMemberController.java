package com.example.LlmSpring.controller;

import com.example.LlmSpring.project.ProjectAccessService;
import com.example.LlmSpring.projectMember.ProjectMemberService;
import com.example.LlmSpring.projectMember.request.ProjectMemberInviteRequestDTO;
import com.example.LlmSpring.projectMember.request.ProjectMemberRemoveRequestDTO;
import com.example.LlmSpring.projectMember.request.ProjectMemberRoleRequestDTO;
import com.example.LlmSpring.projectMember.response.ProjectMemberResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 추가됨
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;
    private final ProjectAccessService projectAccessService;

    /**
     * [프로젝트 참여 멤버 목록 조회 API]
     * GET /api/projects/{projectId}/members
     */
    @GetMapping("/{projectId}/members")
    public ResponseEntity<?> getProjectMembers(
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") int projectId) {

        // [읽기 권한] DELETE 상태면 OWNER만 접근 가능
        projectAccessService.validateReadAccess((long) projectId, userId);

        try {
            // 3. 서비스 호출
            List<ProjectMemberResponseDTO> members = projectMemberService.getMemberList(projectId, userId);

            // 조회한 멤버 목록 중 '나(userId)'와 일치하는 멤버의 status를 "me"로 변경
            for (ProjectMemberResponseDTO member : members) {
                if (member.getUserId().equals(userId)) {
                    member.setStatus("me");
                }
            }

            return ResponseEntity.ok(members);

        } catch (RuntimeException e) {
            // 프로젝트 멤버가 아닌 경우 등의 예외 처리 (403 Forbidden)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * [프로젝트 멤버 초대 API]
     * POST /api/projects/{projectId}/members/invite
     */
    @PostMapping("/{projectId}/members/invite")
    public ResponseEntity<String> inviteMember(
            @AuthenticationPrincipal String inviterId,
            @PathVariable("projectId") int projectId,
            @RequestBody ProjectMemberInviteRequestDTO dto) {

        // [관리 권한] OWNER만 가능 + DONE/DELETE 상태 시 차단 (멤버 동결)
        projectAccessService.validateMemberManageAccess((long) projectId, inviterId);

        try {
            // 3. 서비스 호출
            projectMemberService.inviteMember(projectId, inviterId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body("성공적으로 초대되었습니다.");

        } catch (RuntimeException e) {
            // OWNER 권한이 없거나 자기 자신 초대 등 예외 발생 시 400 에러 반환
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * [프로젝트 멤버 역할 변경 API]
     */
    @PatchMapping("/{projectId}/members/role")
    public ResponseEntity<String> updateMemberRole(
            @AuthenticationPrincipal String requesterId,
            @PathVariable("projectId") int projectId,
            @RequestBody ProjectMemberRoleRequestDTO dto) {

        // [관리 권한] OWNER만 가능 + DONE/DELETE 상태 시 차단
        projectAccessService.validateMemberManageAccess((long) projectId, requesterId);

        try {
            // 2. 서비스 호출
            projectMemberService.updateMemberRole(projectId, requesterId, dto);
            return ResponseEntity.ok("멤버의 역할이 성공적으로 변경되었습니다.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * [멤버 제거 API (추방, 나가기, 초대취소)]
     */
    @PostMapping("/{projectId}/members/remove")
    public ResponseEntity<String> removeMember(
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") int projectId,
            @RequestBody ProjectMemberRemoveRequestDTO dto) {

        // [관리 권한] OWNER만 가능 + DONE/DELETE 상태 시 차단 (멤버 동결)
        projectAccessService.validateMemberManageAccess((long) projectId, userId);

        try {
            // 2. 서비스 호출
            projectMemberService.removeMember(projectId, userId, dto);
            String message = dto.getAction() + " 처리가 완료되었습니다.";
            return ResponseEntity.ok(message);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/{projectId}/accept")
    public ResponseEntity<?> acceptInvitation(
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") int projectId
    ){
        try{
            System.out.println("컨트롤러 진입: " + userId + ", 프로젝트 아이디: " + projectId);

            projectMemberService.acceptInvitation(projectId, userId);

            return ResponseEntity.ok(Collections.singletonMap("message", "프로젝트 초대를 수락했습니다"));
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @PostMapping("/{projectId}/decline")
    public ResponseEntity<?> declineInvitation(
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") int projectId
    ) {
        try {
            projectMemberService.declineInvitation(projectId, userId);

            // JSON 응답 반환
            return ResponseEntity.ok(Collections.singletonMap("message", "프로젝트 초대를 거절했습니다."));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * [이슈 담당자 지정용 멤버 목록 조회 API]
     * - 프로젝트의 ACTIVE 상태인 멤버만 반환합니다.
     */
    @GetMapping("/{projectId}/members/assignees")
    public ResponseEntity<?> getIssueAssigneeMembers(
            @AuthenticationPrincipal String userId,
            @PathVariable("projectId") int projectId) {

        // [읽기 권한] DELETE 상태면 OWNER만 접근 가능
        projectAccessService.validateReadAccess((long) projectId, userId);

        // 서비스 호출
        List<ProjectMemberResponseDTO> members = projectMemberService.getIssueAssigneeMemberList(projectId, userId);

        return ResponseEntity.ok(members);
    }
}