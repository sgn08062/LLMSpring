package com.example.LlmSpring.task;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;

    //1. 업무 생성
    @Transactional
    public void createTask(Long projectId, TaskRequestDTO requestDTO) {
        TaskVO vo = new TaskVO();
        vo.setProjectId(projectId);
        vo.setTitle(requestDTO.getTitle());
        vo.setUserId("user1"); // security에서 추출
        vo.setBranch(requestDTO.getBranch());
        vo.setPriority(requestDTO.getPriority());
        vo.setDueDate(requestDTO.getDueDate());
        vo.setStatus("TODO");
        vo.setContent(requestDTO.getContent());

        taskMapper.insertTask(vo);

        //담당자 저장
        if (requestDTO.getAssigneeIds() != null) {
            for (String userId : requestDTO.getAssigneeIds()) {
                taskMapper.insertTaskUser(vo.getTaskId(), userId);
            }
        }
    }

    //2. 업무 목록 조회
    public List<TaskResponseDTO> getTaskList(Long projectId) {
        List<TaskVO> tasks = taskMapper.selectTasksByProjectId(projectId);
        return tasks.stream().map(task -> {
            TaskResponseDTO dto = new TaskResponseDTO(task);
            dto.setAssigneeIds(taskMapper.selectTaskUsers(task.getTaskId()));
            return dto;
        }).collect(Collectors.toList());
    }

    //3. 업무 상세 조회
    public TaskResponseDTO getTaskDetail(Long taskId) {
        TaskVO task = taskMapper.selectTaskById(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found");

        TaskResponseDTO dto = new TaskResponseDTO(task);
        dto.setAssigneeIds(taskMapper.selectTaskUsers(taskId));
        dto.setCheckLists(taskMapper.selectCheckLists(taskId));
        return dto;
    }

    //4. 상태 변경
    @Transactional
    public void updateStatus(Long taskId, String status) {
        taskMapper.updateTaskStatus(taskId, status);
    }

    //5. 업무 수정
    @Transactional
    public void updateTask(Long taskId, TaskRequestDTO requestDTO) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(taskId);
        vo.setTitle(requestDTO.getTitle());
        vo.setContent(requestDTO.getContent());
        vo.setPriority(requestDTO.getPriority());
        vo.setBranch(requestDTO.getBranch());
        vo.setDueDate(requestDTO.getDueDate());
        taskMapper.updateTask(vo);
    }

    //6. 삭제
    @Transactional
    public void deleteTask(Long taskId) {
        taskMapper.softDeleteTask(taskId);
    }

    //체크리스트
    public List<TaskCheckListVO> getCheckLists(Long taskId) {
        return taskMapper.selectCheckLists(taskId);
    }

    public void addCheckList(Long taskId, String content) {
        TaskCheckListVO vo = new TaskCheckListVO();
        vo.setTaskId(taskId);
        vo.setContent(content);
        taskMapper.insertCheckList(vo);
    }

    public void deleteCheckList(Long checklistId) {
        taskMapper.deleteCheckList(checklistId);
    }

    public void toggleCheckList(Long checklistId, boolean isDone) {
        taskMapper.updateCheckListStatus(checklistId, isDone);
    }

    //채팅 & 로그
    public List<Map<String, Object>> getChats(Long taskId) {
        return taskMapper.selectChats(taskId);
    }

    public void addChat(Long taskId, String content) {
        String userId = "user1";
        taskMapper.insertChat(taskId, userId, content);
    }

    public List<Map<String, Object>> getLogs(Long taskId) {
        return taskMapper.selectsLogs(taskId);
    }
}
