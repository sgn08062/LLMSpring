package com.example.LlmSpring.controller;

import com.example.LlmSpring.project.ProjectAccessService;
import com.example.LlmSpring.task.TaskLogVO;
import com.example.LlmSpring.task.request.TaskRequestDTO;
import com.example.LlmSpring.task.response.TaskResponseDTO;
import com.example.LlmSpring.task.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskService taskService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ProjectAccessService projectAccessService;

    @GetMapping("/my-role")
    public ResponseEntity<Map<String, String>> getMyRole(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId) {

        // [읽기 권한] DELETE 상태면 OWNER만 접근 가능
        projectAccessService.validateReadAccess(projectId, userId);

        System.out.println("=== 권한 조회 요청 ===");
        System.out.println("User ID: " + userId);
        System.out.println("Project ID: " + projectId);

        String role = taskService.getMyRole(projectId, userId);

        System.out.println("DB 조회 결과 Role: " + role);
        System.out.println("=====================");

        return ResponseEntity.ok(Map.of("role", role));
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createTask(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @RequestBody TaskRequestDTO requestDTO) {

        // [쓰기 권한] DONE 또는 DELETE 상태면 접근 불가
        projectAccessService.validateWriteAccess(projectId, userId);

        taskService.createTask(projectId, userId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Created"));
    }

    @GetMapping
    public ResponseEntity<List<TaskResponseDTO>> getTaskList(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId) {
        // [읽기 권한]
        projectAccessService.validateReadAccess(projectId, userId);
        return ResponseEntity.ok(taskService.getTaskList(projectId));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponseDTO> getTaskDetail(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId) {

        // [읽기 권한]
        projectAccessService.validateReadAccess(projectId, userId);
        return ResponseEntity.ok(taskService.getTaskDetail(taskId));
    }

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<Map<String, String>> updateStatus(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> body) {

        // [쓰기 권한]
        projectAccessService.validateWriteAccess(projectId, userId);

        taskService.updateStatus(taskId, userId, body.get("status"));
        return ResponseEntity.ok(Map.of("message", "Status Updated"));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<Map<String, String>> updateTask(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestBody TaskRequestDTO requestDTO) {

        // [쓰기 권한]
        projectAccessService.validateWriteAccess(projectId, userId);

        taskService.updateTask(taskId, userId, requestDTO);
        return ResponseEntity.ok(Map.of("message", "Updated"));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Map<String, String>> deleteTask(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId) {

        // [쓰기 권한]
        projectAccessService.validateWriteAccess(projectId, userId);

        taskService.deleteTask(taskId, userId);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    @GetMapping("/{taskId}/checklists")
    public List<com.example.LlmSpring.task.TaskCheckListVO> getCheckLists(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId) {

        // [읽기 권한]
        projectAccessService.validateReadAccess(projectId, userId);

        return taskService.getCheckLists(taskId);
    }

    @PostMapping("/{taskId}/checklists")
    public ResponseEntity<Map<String, String>> addCheckList(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> body) {

        // [쓰기 권한]
        projectAccessService.validateWriteAccess(projectId, userId);

        taskService.addCheckList(taskId, userId, body.get("content"));
        return ResponseEntity.ok(Map.of("message", "Checklist Added"));
    }

    @DeleteMapping("/{taskId}/checklists/{checklistId}")
    public ResponseEntity<Map<String, String>> deleteCheckList(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @PathVariable Long checklistId) {

        // [쓰기 권한]
        projectAccessService.validateWriteAccess(projectId, userId);

        taskService.deleteCheckList(taskId, checklistId, userId);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    @PatchMapping("/{taskId}/checklists/{checklistId}")
    public ResponseEntity<Map<String, String>> toggleCheckList(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @PathVariable Long checklistId,
            @RequestBody Map<String, Boolean> body) {

        // [쓰기 권한]
        projectAccessService.validateWriteAccess(projectId, userId);

        taskService.toggleCheckList(checklistId, body.get("is_done"), taskId, userId);
        return ResponseEntity.ok(Map.of("message", "Toggled"));
    }

    @GetMapping("/{taskId}/chats")
    public List<Map<String, Object>> getChats(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId) {

        // [읽기 권한]
        projectAccessService.validateReadAccess(projectId, userId);

        return taskService.getChats(taskId);
    }

    @PostMapping("/{taskId}/chats")
    public ResponseEntity<String> addChat(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> body) {

        // [쓰기 권한]
        projectAccessService.validateWriteAccess(projectId, userId);

        String content = body.get("content");
        taskService.addChat(taskId, userId, content);

        Map<String, Object> chatMessage = new HashMap<>();
        chatMessage.put("userId", userId);
        chatMessage.put("content", content);
        chatMessage.put("taskId", taskId);

        messagingTemplate.convertAndSend("/sub/tasks/" + taskId, chatMessage);
        return ResponseEntity.ok("Chat added");
    }

    @GetMapping("/{taskId}/logs")
    public ResponseEntity<List<TaskLogVO>> getLogs(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId) {

        // [읽기 권한]
        projectAccessService.validateReadAccess(projectId, userId);

        return ResponseEntity.ok(taskService.getLogs(taskId));
    }
}