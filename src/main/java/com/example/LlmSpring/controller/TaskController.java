package com.example.LlmSpring.controller;

import com.example.LlmSpring.task.TaskLogVO;
import com.example.LlmSpring.task.request.TaskRequestDTO;
import com.example.LlmSpring.task.response.TaskResponseDTO;
import com.example.LlmSpring.task.TaskService;
import com.example.LlmSpring.util.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskService taskService;
    private final JWTService jwtService;
    private final SimpMessagingTemplate messagingTemplate;

    private String getUserId(String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return jwtService.verifyTokenAndUserId(token);
    }

    // [수정] 내 권한 확인 API (디버깅 로그 추가)
    @GetMapping("/my-role")
    public ResponseEntity<Map<String, String>> getMyRole(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long projectId) {

        String userId = getUserId(authHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // 서버 로그 출력 (이게 서버 콘솔에 어떻게 찍히는지 확인하세요!)
        System.out.println("=== 권한 조회 요청 ===");
        System.out.println("User ID: " + userId);
        System.out.println("Project ID: " + projectId);

        String role = taskService.getMyRole(projectId, userId);

        System.out.println("DB 조회 결과 Role: " + role);
        System.out.println("=====================");

        return ResponseEntity.ok(Map.of("role", role));
    }

    // --- (아래부터는 기존 코드 유지) ---

    @PostMapping
    public ResponseEntity<Map<String, String>> createTask(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long projectId,
            @RequestBody TaskRequestDTO requestDTO) {
        String userId = getUserId(authHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        taskService.createTask(projectId, userId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Created"));
    }

    @GetMapping
    public ResponseEntity<List<TaskResponseDTO>> getTaskList(@PathVariable Long projectId) {
        return ResponseEntity.ok(taskService.getTaskList(projectId));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponseDTO> getTaskDetail(@PathVariable Long taskId) {
        return ResponseEntity.ok(taskService.getTaskDetail(taskId));
    }

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<Map<String, String>> updateStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> body) {
        String userId = getUserId(authHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        taskService.updateStatus(taskId, userId, body.get("status"));
        return ResponseEntity.ok(Map.of("message", "Status Updated"));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<Map<String, String>> updateTask(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long taskId,
            @RequestBody TaskRequestDTO requestDTO) {
        String userId = getUserId(authHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        taskService.updateTask(taskId, userId, requestDTO);
        return ResponseEntity.ok(Map.of("message", "Updated"));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Map<String, String>> deleteTask(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long taskId) {
        String userId = getUserId(authHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        taskService.deleteTask(taskId, userId);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    @GetMapping("/{taskId}/checklists")
    public List<com.example.LlmSpring.task.TaskCheckListVO> getCheckLists(@PathVariable Long taskId) {
        return taskService.getCheckLists(taskId);
    }

    @PostMapping("/{taskId}/checklists")
    public ResponseEntity<Map<String, String>> addCheckList(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> body) {
        String userId = getUserId(authHeader);
        taskService.addCheckList(taskId, userId, body.get("content"));
        return ResponseEntity.ok(Map.of("message", "Checklist Added"));
    }

    @DeleteMapping("/{taskId}/checklists/{checklistId}")
    public ResponseEntity<Map<String, String>> deleteCheckList(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long taskId,
            @PathVariable Long checklistId) {
        String userId = getUserId(authHeader);
        taskService.deleteCheckList(taskId, checklistId, userId);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    @PatchMapping("/{taskId}/checklists/{checklistId}")
    public ResponseEntity<Map<String, String>> toggleCheckList(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long taskId,
            @PathVariable Long checklistId,
            @RequestBody Map<String, Boolean> body) {
        String userId = getUserId(authHeader);
        taskService.toggleCheckList(checklistId, body.get("is_done"), taskId, userId);
        return ResponseEntity.ok(Map.of("message", "Toggled"));
    }

    @GetMapping("/{taskId}/chats")
    public List<Map<String, Object>> getChats(@PathVariable Long taskId) {
        return taskService.getChats(taskId);
    }

    @PostMapping("/{taskId}/chats")
    public ResponseEntity<String> addChat(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> body) {
        String userId = getUserId(authHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

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
    public ResponseEntity<List<TaskLogVO>> getLogs(@PathVariable Long taskId) {
        return ResponseEntity.ok(taskService.getLogs(taskId));
    }
}