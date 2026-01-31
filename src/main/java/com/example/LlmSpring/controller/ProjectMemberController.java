package com.example.LlmSpring.controller;

import com.example.LlmSpring.projectMember.ProjectMemberService;
import com.example.LlmSpring.projectMember.request.ProjectMemberInviteRequestDTO;
import com.example.LlmSpring.projectMember.request.ProjectMemberRemoveRequestDTO;
import com.example.LlmSpring.projectMember.request.ProjectMemberRoleRequestDTO;
import com.example.LlmSpring.projectMember.response.ProjectMemberResponseDTO;
import com.example.LlmSpring.util.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;
    private final JWTService jwtService;

    /**
     * [프로젝트 참여 멤버 목록 조회 API]
     * JWT 토큰으로 요청자를 식별하고, 해당 프로젝트의 멤버인 경우에만 목록을 반환합니다.
     * GET /api/projects/{projectId}/members
     */
    @GetMapping("/{projectId}/members")
    public ResponseEntity<?> getProjectMembers(
            @RequestHeader("Authorization") String authHeader, // 헤더에서 토큰 수신
            @PathVariable("projectId") int projectId) {

        try {
            // 1. 토큰 추출 및 userId 획득
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            String userId = jwtService.verifyTokenAndUserId(token);

            // 2. 인증 실패 처리
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않거나 만료된 토큰입니다.");
            }

            // 3. 서비스 호출 (추출된 userId 전달)
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
     * JWT 토큰으로 요청자가 OWNER인지 확인한 후 새로운 사용자를 초대합니다.
     * POST /api/projects/{projectId}/members/invite
     */
    @PostMapping("/{projectId}/members/invite")
    public ResponseEntity<String> inviteMember(
            @RequestHeader("Authorization") String authHeader, // 헤더에서 토큰 수신
            @PathVariable("projectId") int projectId,
            @RequestBody ProjectMemberInviteRequestDTO dto) {

        try {
            // 1. 토큰 추출 및 inviterId(초대하는 사람) 획득
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            String inviterId = jwtService.verifyTokenAndUserId(token);

            // 2. 인증 실패 처리
            if (inviterId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않거나 만료된 토큰입니다.");
            }

            // 3. 서비스 호출 (추출된 inviterId 전달)
            projectMemberService.inviteMember(projectId, inviterId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body("성공적으로 초대되었습니다.");

        } catch (RuntimeException e) {
            // OWNER 권한이 없거나 자기 자신 초대 등 예외 발생 시 400 에러 반환
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * [프로젝트 멤버 역할 변경 API]
     * JWT 토큰으로 요청자가 OWNER나 ADMIN인지 확인한 후 멤버의 역할을 변경합니다.
     */
    @PatchMapping("/{projectId}/members/role")
    public ResponseEntity<String> updateMemberRole(
            @RequestHeader("Authorization") String authHeader, // 헤더로 변경
            @PathVariable("projectId") int projectId,
            @RequestBody ProjectMemberRoleRequestDTO dto) {

        try {
            // 1. 토큰 추출 및 requesterId 획득
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            String requesterId = jwtService.verifyTokenAndUserId(token);

            if (requesterId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않거나 만료된 토큰입니다.");
            }

            // 2. 서비스 호출 (추출된 requesterId 전달)
            projectMemberService.updateMemberRole(projectId, requesterId, dto);
            return ResponseEntity.ok("멤버의 역할이 성공적으로 변경되었습니다.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * [멤버 제거 API (추방, 나가기, 초대취소)]
     * JWT 토큰으로 본인 확인 및 권한 확인 후 멤버를 프로젝트에서 제거합니다.
     */
    @PostMapping("/{projectId}/members/remove")
    public ResponseEntity<String> removeMember(
            @RequestHeader("Authorization") String authHeader, // 헤더로 변경
            @PathVariable("projectId") int projectId,
            @RequestBody ProjectMemberRemoveRequestDTO dto) {

        try {
            // 1. 토큰 추출 및 userId 획득
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            String userId = jwtService.verifyTokenAndUserId(token);

            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않거나 만료된 토큰입니다.");
            }

            // 2. 서비스 호출 (추출된 userId 전달)
            projectMemberService.removeMember(projectId, userId, dto);
            String message = dto.getAction() + " 처리가 완료되었습니다.";
            return ResponseEntity.ok(message);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/{projectId}/accept")
    public ResponseEntity<?> acceptInvitation(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("projectId") int projectId
    ){
        try{
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            String userId = jwtService.verifyTokenAndUserId(token);

            if(userId == null){
                // 에러 메시지도 JSON으로 맞추려면 Map 사용 권장
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Collections.singletonMap("error", "유효하지 않거나 만료된 토큰입니다"));
            }

            System.out.println("컨트롤러 진입: " + userId + ", 프로젝트 아이디: " + projectId);

            projectMemberService.acceptInvitation(projectId, userId);

            return ResponseEntity.ok(Collections.singletonMap("message", "프로젝트 초대를 수락했습니다"));
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @PostMapping("/{projectId}/decline")
    public ResponseEntity<?> declineInvitation(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("projectId") int projectId
    ) {
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            String userId = jwtService.verifyTokenAndUserId(token);

            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Collections.singletonMap("error", "유효하지 않거나 만료된 토큰입니다"));
            }

            projectMemberService.declineInvitation(projectId, userId);

            // JSON 응답 반환
            return ResponseEntity.ok(Collections.singletonMap("message", "프로젝트 초대를 거절했습니다."));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", e.getMessage()));
        }
    }

}
