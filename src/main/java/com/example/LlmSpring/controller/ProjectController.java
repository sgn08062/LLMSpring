package com.example.LlmSpring.controller;

import com.example.LlmSpring.project.request.ProjectCreateRequestDTO;
import com.example.LlmSpring.project.ProjectService;
import com.example.LlmSpring.project.request.ProjectStatusRequestDTO;
import com.example.LlmSpring.project.request.ProjectUpdateRequestDTO;
import com.example.LlmSpring.project.response.ProjectListResponseDTO;
import com.example.LlmSpring.util.JWTService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor // @Autowired 대신 권장하는 final + @RequiredArgsConstructor 방식
public class ProjectController {

    private final ProjectService projectService;
    private final JWTService jwtService;

    /**
     * [프로젝트 생성 API]
     * JWT 토큰을 통해 요청자를 식별하고 새로운 프로젝트를 생성합니다.
     * 요청자는 자동으로 해당 프로젝트의 소유자(OWNER)로 등록됩니다.
     * * @param authHeader Authorization 헤더 (Bearer {Token})
     * @param dto 프로젝트 생성 정보 (이름, 설명, 마감일 등)
     * @return 생성된 프로젝트 ID
     */
    @PostMapping
    public ResponseEntity<?> createProject(
            @RequestHeader("Authorization") String authHeader, // 헤더에서 토큰 수신
            @RequestBody ProjectCreateRequestDTO dto)
    {

        // 1. 토큰 추출 및 검증
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        // 2. 토큰이 유효하지 않은 경우 예외 처리
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않거나 만료된 토큰입니다.");
        }

        System.out.println(dto.toString());

        // 서비스 호출을 통해 프로젝트 생성 로직 실행
        int projectId = projectService.createProject(dto, userId);

        // 생성 완료 후 생성된 ID와 함께 201 Created 응답
        return ResponseEntity.status(HttpStatus.CREATED).body(projectId);
    }

    /**
     * [프로젝트 정보 수정 API]
     * 프로젝트의 상세 정보를 수정합니다.
     * 보안을 위해 JWT 토큰을 통해 요청자가 해당 프로젝트의 소유자(OWNER)인지 검증합니다.
     * * @param authHeader Authorization 헤더
     * @param projectId 수정할 프로젝트 ID
     * @param dto 수정할 데이터
     * @return 결과 메시지
     */
    @PutMapping("/{projectId}")
    public ResponseEntity<String> updateProject(
            @RequestHeader("Authorization") String authHeader, // 헤더에서 토큰 수신
            @PathVariable("projectId") int projectId,
            @RequestBody ProjectUpdateRequestDTO dto) {

        // 1. 토큰 추출 및 userId 획득
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않거나 만료된 토큰입니다.");
        }

        try {
            // 2. 서비스 호출 시 userId 전달
            int result = projectService.updateProject(projectId, userId, dto);

            if (result == 1) {
                return ResponseEntity.ok("프로젝트 수정이 완료되었습니다.");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("수정할 프로젝트를 찾을 수 없습니다.");
            }
        } catch (RuntimeException e) {
            // 3. 권한 부족(OWNER가 아님) 등의 예외 처리
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * [프로젝트 상태 변경 API]
     * 프로젝트의 상태를 ACTIVE(진행 중) 또는 DONE(완료)으로 변경합니다.
     * 소유자(OWNER)만 상태를 변경할 수 있도록 권한을 체크합니다.
     * * @param authHeader Authorization 헤더
     * @param projectId 상태를 변경할 프로젝트 ID
     * @param dto 변경할 상태 정보 (status: "DONE" 등)
     * @return 상태 변경 완료 메시지
     */
    @PatchMapping("/{projectId}/status")
    public ResponseEntity<String> updateProjectStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("projectId") int projectId,
            @RequestBody ProjectStatusRequestDTO dto) {

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않거나 만료된 토큰입니다.");
        }

        try {
            int result = projectService.updateProjectStatus(projectId, userId, dto.getStatus());
            if (result == 1) {
                String message = "DONE".equals(dto.getStatus())
                        ? "프로젝트가 완료 처리되어 이제 수정할 수 없습니다."
                        : "프로젝트가 다시 활성화되었습니다.";
                return ResponseEntity.ok(message);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("대상을 찾을 수 없습니다.");
            }
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * [프로젝트 삭제 API (Soft Delete)]
     * 프로젝트를 삭제 대기 상태로 변경합니다.
     * 소유자(OWNER) 권한 확인 후, 7일의 유예 기간을 기록합니다.
     * * @param authHeader Authorization 헤더
     * @param projectId 삭제할 프로젝트 ID
     * @return 삭제 대기 알림 메시지
     */
    @DeleteMapping("/{projectId}")
    public ResponseEntity<String> deleteProject(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("projectId") int projectId) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않거나 만료된 토큰입니다.");
        }

        try {
            int result = projectService.deleteProject(projectId, userId);
            if (result == 1) {
                return ResponseEntity.ok("프로젝트가 삭제 대기 상태로 변경되었습니다. (7일 후 완전 삭제)");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("삭제할 프로젝트를 찾을 수 없거나 이미 삭제되었습니다.");
            }
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * [참여 중인 프로젝트 목록 조회 API]
     * 사용자가 참여하고 있는 프로젝트 목록을 타입별(active, done, trash)로 조회합니다.
     * JWT 토큰을 통해 본인의 데이터만 안전하게 가져옵니다.
     * * @param authHeader Authorization 헤더
     * @param type 조회 타입 (active: 진행 중, done: 완료, trash: 휴지통)
     * @return 상태별 프로젝트 목록
     */
    @GetMapping
    public ResponseEntity<?> getProjectList(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(value = "type", defaultValue = "active") String type) {

        // JWT 토큰 검증 및 사용자 식별
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
        }

        List<ProjectListResponseDTO> list;

        // 전달된 type 파라미터에 따라 적절한 서비스 메서드 호출
        if ("done".equals(type)) {
            list = projectService.getDoneProjects(userId);
        } else if ("trash".equals(type)) {
            list = projectService.getTrashProjects(userId);
        } else {
            list = projectService.getActiveProjects(userId);
        }

        return ResponseEntity.ok(list);
    }

    /**
     * [프로젝트 복구 API]
     * 삭제 대기(휴지통) 상태인 프로젝트를 정상 상태로 되돌립니다.
     * 요청자가 소유자(OWNER)인지, 그리고 7일 유예 기간 이내인지 검증합니다.
     * * @param authHeader Authorization 헤더
     * @param projectId 복구할 프로젝트 ID
     * @return 복구 성공 메시지
     */
    @PostMapping("/{projectId}/restore")
    public ResponseEntity<String> restoreProject(
            @RequestHeader("Authorization") String authHeader, // 헤더에서 토큰 수신
            @PathVariable("projectId") int projectId) { // 프로젝트 삭제 취소 API

        // 1. 토큰 추출 및 userId 획득
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않거나 만료된 토큰입니다.");
        }

        try {
            // 2. 서비스 호출 (추출된 userId 전달)
            int result = projectService.restoreProject(projectId, userId);

            if (result == 1) {
                return ResponseEntity.ok("프로젝트가 성공적으로 복구되었습니다.");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("복구 처리 중 오류가 발생했습니다.");
            }
        } catch (RuntimeException e) {
            // 3. 권한 부족(OWNER가 아님) 또는 유예 기간 만료 예외 처리
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

}
