package com.example.LlmSpring.controller;

import com.example.LlmSpring.task.TaskCheckListVO;
import com.example.LlmSpring.task.request.TaskRequestDTO;
import com.example.LlmSpring.task.response.TaskResponseDTO;
import com.example.LlmSpring.task.TaskService;
import com.example.LlmSpring.util.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskService taskService;
    private final JWTService jwtService;

    private String getUserId(String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return jwtService.verifyTokenAndUserId(token);
    }

    //1. 업무 생성
    @PostMapping
    public ResponseEntity<String> createTask(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long projectID,
            @RequestBody TaskRequestDTO requestDTO) {
        String userId = getUserId(authHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        taskService.createTask(projectID, userId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body("Created");
    }

    //2. 업무 목록 조회
    @GetMapping
    public ResponseEntity<List<TaskResponseDTO>> getTaskList(@PathVariable Long projectID) {
        return ResponseEntity.ok(taskService.getTaskList(projectID));
    }

    //3. 상세 조회
    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponseDTO> getTaskDetail(@PathVariable Long taskId) {
        return ResponseEntity.ok(taskService.getTaskDetail(taskId));
    }

    //4. 상태 변경
    @PatchMapping("/{taskId}/status")
    public ResponseEntity<String> updateTaskStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> body) {
        String userId = getUserId(authHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        taskService.updateStatus(taskId, userId, body.get("status"));
        return ResponseEntity.ok("Status updated");
    }

    //5. 수정
    @PutMapping("/{taskId}")
    public ResponseEntity<String> updateTask(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long taskId,
            @RequestBody TaskRequestDTO requestDTO) {
        String userId = getUserId(authHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        taskService.updateTask(taskId, userId, requestDTO);
        return ResponseEntity.ok("Updated");
    }

    //6. 삭제
    @DeleteMapping("/{taskId}")
    public ResponseEntity<String> deleteTask(@PathVariable Long taskId) {
        taskService.deleteTask(taskId);
        return ResponseEntity.ok("Deleted");
    }

    //체크리스트
    @GetMapping("/{taskId}/checklists")
    public List<TaskCheckListVO> getCheckLists(@PathVariable Long taskId) {
        return taskService.getCheckLists(taskId);
    }

    @PostMapping("/{taskId}/checklists")
    public String addCheckList(@PathVariable Long taskId, @RequestBody Map<String, String> body) {
        taskService.addCheckList(taskId, body.get("content"));
        return "Checklist Added";
    }

    @DeleteMapping("/{taskId}/checklists/{checklistId}")
    public Map<String, String> deleteCheckList(@PathVariable Long checklistId) {
        taskService.deleteCheckList(checklistId);
        return Map.of("message", "삭제되었습니다.");
    }

    @PatchMapping("/{taskId}/checklists/{checklistId}")
    public String toggleCheckList(@PathVariable Long checklistId, @RequestBody Map<String, Boolean> body) {
        taskService.toggleCheckList(checklistId, body.get("is_done"));
        return "Toggled";
    }

    //채팅
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

        taskService.addChat(taskId, userId, body.get("content"));
        return ResponseEntity.ok("Chat added");
    }

    //로그
    @GetMapping("/{taskId}/logs")
    public List<Map<String, Object>> getLogs(@PathVariable Long taskId) {
        return taskService.getLogs(taskId);
    }

}
