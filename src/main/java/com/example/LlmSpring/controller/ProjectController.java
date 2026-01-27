package com.example.LlmSpring.controller;

import com.example.LlmSpring.project.request.ProjectCreateRequestDTO;
import com.example.LlmSpring.project.ProjectService;
import com.example.LlmSpring.project.request.ProjectStatusRequestDTO;
import com.example.LlmSpring.project.request.ProjectUpdateRequestDTO;
import com.example.LlmSpring.project.response.ProjectListResponseDTO;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor // @Autowired 대신 권장하는 final + @RequiredArgsConstructor 방식
public class ProjectController {

    private final ProjectService projectService;

    // 프로젝트 생성 API 진입점
    @PostMapping
    public ResponseEntity<Integer> createProject(
            @RequestBody ProjectCreateRequestDTO dto,
            @RequestParam("userId") String userId) {
        // 서비스 호출을 통해 프로젝트 생성 로직 실행
        int projectId = projectService.createProject(dto, userId);

        // 생성 완료 후 생성된 ID와 함께 201 Created 응답
        return ResponseEntity.status(HttpStatus.CREATED).body(projectId);
    }

    // 프로젝트 수정 API 진입점
    @PutMapping("/{projectId}")
    public ResponseEntity<String> updateProject(
            @PathVariable("projectId") int projectId,
            @RequestBody ProjectUpdateRequestDTO dto) {

        int result = projectService.updateProject(projectId, dto);

        if (result == 1) {
            return ResponseEntity.ok("프로젝트 수정이 완료되었습니다.");
        } else {
            // 수정된 행이 0인 경우 (ID가 없거나 이미 삭제된 프로젝트인 경우)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("수정할 프로젝트를 찾을 수 없습니다.");
        }
    }

    // 프로젝트 상태 수정 API 진입점 (ACTIVE <-> DONE)
    @PatchMapping("/{projectId}/status")
    public ResponseEntity<String> updateProjectStatus(
            @PathVariable("projectId") int projectId,
            @RequestBody ProjectStatusRequestDTO dto) {

        int result = projectService.updateProjectStatus(projectId, dto.getStatus());

        if (result == 1) {
            String message = "DONE".equals(dto.getStatus())
                    ? "프로젝트가 완료 처리되어 이제 수정할 수 없습니다."
                    : "프로젝트가 다시 활성화되었습니다.";
            return ResponseEntity.ok(message);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("대상을 찾을 수 없습니다.");
        }
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<String> deleteProject(@PathVariable("projectId") int projectId) {
        int result = projectService.deleteProject(projectId);

        if (result == 1) {
            return ResponseEntity.ok("프로젝트가 삭제 대기 상태로 변경되었습니다. (7일 후 완전 삭제)");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("삭제할 프로젝트를 찾을 수 없거나 이미 삭제되었습니다.");
        }
    }

    @GetMapping
    public ResponseEntity<List<ProjectListResponseDTO>> getProjectList(
            @RequestParam(value = "type", defaultValue = "active") String type,
            @RequestParam("userId") String userId) { // 사용자가 참여중인 프로젝트 목록 조회

        List<ProjectListResponseDTO> list;

        if ("active".equals(type)) {
            list = projectService.getActiveProjects(userId);
        } else if ("done".equals(type)) {
            list = projectService.getDoneProjects(userId);
        } else if ("trash".equals(type)) {
            list = projectService.getTrashProjects(userId);
        } else {
            list = new ArrayList<>();
        }
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{projectId}/restore")
    public ResponseEntity<String> restoreProject(
            @PathVariable("projectId") int projectId,
            @RequestParam("userId") String userId) { // 프로젝트 삭제 취소 API

        try {
            int result = projectService.restoreProject(projectId, userId);

            if (result == 1) {
                return ResponseEntity.ok("프로젝트가 성공적으로 복구되었습니다.");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("복구 처리 중 오류가 발생했습니다.");
            }
        } catch (RuntimeException e) {
            // 도메인 규칙 위반 시 에러 메시지 반환
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

}
