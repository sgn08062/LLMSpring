package com.example.LlmSpring.controller;

import com.example.LlmSpring.task.TaskCheckListVO;
import com.example.LlmSpring.task.request.TaskRequestDTO;
import com.example.LlmSpring.task.response.TaskResponseDTO;
import com.example.LlmSpring.task.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskService taskService;

    //1. 업무 생성
    @PostMapping
    public String createTask(@PathVariable Long projectId, @RequestBody TaskRequestDTO requestDTO) {
        taskService.createTask(projectId, requestDTO);
        return "Created";
    }

    //2. 업무 목록 조회
    @GetMapping
    public List<TaskResponseDTO> getTaskList(@PathVariable Long projectId) {
        return taskService.getTaskList(projectId);
    }

    //3. 상세 조회
    @GetMapping("/{taskId}")
    public TaskResponseDTO getTaskDetail(@PathVariable Long projectId, @PathVariable Long taskId) {
        return taskService.getTaskDetail(taskId);
    }

    //4. 상태 변경
    @PatchMapping("/{taskId}/status")
    public String updateStatus(@PathVariable Long projectId, @PathVariable Long taskId, @RequestBody Map<String, String> body) {
        taskService.updateStatus(taskId, body.get("status"));
        return "Status updated";
    }

    //5. 수정
    @PutMapping("/{taskId}")
    public String updateTask(@PathVariable Long projectId, @PathVariable Long taskId, @RequestBody TaskRequestDTO requestDTO) {
        taskService.updateTask(taskId, requestDTO);
        return "Updated";
    }

    //6. 삭제
    @DeleteMapping("/{taskId}")
    public String deleteTask(@PathVariable Long projectId, @PathVariable Long taskId) {
        taskService.deleteTask(taskId);
        return "Deleted";
    }

    //체크리스트
    @GetMapping("/{taskId}/checklists")
    public List<TaskCheckListVO> getCheckLists(@PathVariable Long projectId, @PathVariable Long taskId) {
        return taskService.getCheckLists(taskId);
    }

    @PostMapping("/{taskId}/checklists")
    public String addCheckList(@PathVariable Long projectId, @PathVariable Long taskId, @RequestBody Map<String, String> body) {
        taskService.addCheckList(taskId, body.get("content"));
        return "Checklist Added";
    }

    @DeleteMapping("/{taskId}/checklists/{checklistId}")
    public Map<String, String> deleteCheckList(@PathVariable Long projectId, @PathVariable Long taskId, @PathVariable Long checklistId) {
        taskService.deleteCheckList(checklistId);
        return Map.of("message", "삭제되었습니다.");
    }

    @PatchMapping("/{taskId}/checklists/{checklistId}")
    public String toggleCheckList(@PathVariable Long projectId, @PathVariable Long taskId, @PathVariable Long checklistId, @RequestBody Map<String, Boolean> body) {
        taskService.toggleCheckList(checklistId, body.get("is_done"));
        return "Toggled";
    }

    //채팅
    @GetMapping("/{taskId}/chats")
    public List<Map<String, Object>> getChats(@PathVariable Long projectId, @PathVariable Long taskId) {
        return taskService.getChats(taskId);
    }

    @PostMapping("/{taskId}/chats")
    public String addChat(@PathVariable Long projectId, @PathVariable Long taskId, @RequestBody Map<String, String> body) {
        taskService.addChat(taskId, body.get("content"));
        return "Chat added";
    }

    //로그
    @GetMapping("/{taskId}/logs")
    public List<Map<String, Object>> getLogs(@PathVariable Long projectId, @PathVariable Long taskId) {
        return taskService.getLogs(taskId);
    }

}
