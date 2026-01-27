package com.example.LlmSpring.controller;

import com.example.LlmSpring.projectMember.ProjectMemberService;
import com.example.LlmSpring.projectMember.request.ProjectMemberInviteRequestDTO;
import com.example.LlmSpring.projectMember.request.ProjectMemberRemoveRequestDTO;
import com.example.LlmSpring.projectMember.request.ProjectMemberRoleRequestDTO;
import com.example.LlmSpring.projectMember.response.ProjectMemberResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    /**
     * 프로젝트 참여 멤버 목록 조회 API
     * GET /api/projects/{projectId}/members?userId={currentUserId}
     */
    @GetMapping("/{projectId}/members")
    public ResponseEntity<?> getProjectMembers(
            @PathVariable("projectId") int projectId,
            @RequestParam("userId") String userId) {

        try {
            List<ProjectMemberResponseDTO> members = projectMemberService.getMemberList(projectId, userId);
            return ResponseEntity.ok(members);
        } catch (RuntimeException e) {
            // 권한 없음(403 Forbidden) 응답
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * 프로젝트 참여 멤버 목록 조회 API
     * POST /api/projects/{projectId}/members/invite
     */
    @PostMapping("/{projectId}/members/invite")
    public ResponseEntity<String> inviteMember(
            @PathVariable("projectId") int projectId,
            @RequestParam("userId") String inviterId, // 현재 요청자(초대하는 사람)
            @RequestBody ProjectMemberInviteRequestDTO dto) {

        try {
            projectMemberService.inviteMember(projectId, inviterId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body("성공적으로 초대되었습니다.");
        } catch (RuntimeException e) {
            // 요구사항에 따른 예외 메시지 반환
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * 프로젝트 멤버 역할 변경 API
     * PATCH /api/projects/{projectId}/members/role
     */
    @PatchMapping("/{projectId}/members/role")
    public ResponseEntity<String> updateMemberRole(
            @PathVariable("projectId") int projectId,
            @RequestParam("userId") String requesterId, // 요청자 ID
            @RequestBody ProjectMemberRoleRequestDTO dto) {

        try {
            projectMemberService.updateMemberRole(projectId, requesterId, dto);
            return ResponseEntity.ok("멤버의 역할이 성공적으로 변경되었습니다.");
        } catch (RuntimeException e) {
            // 보안 및 비즈니스 규칙 위반 시 400 에러 반환
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * 멤버 제거 API (추방, 나가기, 초대취소)
     * POST /api/projects/{projectId}/members/remove?userId={currentUserId}
     */
    @PostMapping("/{projectId}/members/remove")
    public ResponseEntity<String> removeMember(
            @PathVariable("projectId") int projectId,
            @RequestParam("userId") String userId,
            @RequestBody ProjectMemberRemoveRequestDTO dto) {

        try {
            projectMemberService.removeMember(projectId, userId, dto);
            String message = dto.getAction() + " 처리가 완료되었습니다.";
            return ResponseEntity.ok(message);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

}
