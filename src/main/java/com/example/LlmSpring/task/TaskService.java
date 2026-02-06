package com.example.LlmSpring.task;

import com.example.LlmSpring.alarm.AlarmService;
import com.example.LlmSpring.task.request.TaskRequestDTO;
import com.example.LlmSpring.task.response.TaskResponseDTO;
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
    private final AlarmService alarmService;

    //권한 조회
    public String getMyRole(Long projectId, String userId) {
        String role = taskMapper.getProjectRole(projectId, userId);
        return role != null ? role : "MEMBER";
    }

    // 1. 업무 생성
    @Transactional
    public void createTask(Long projectId, String userId, TaskRequestDTO requestDTO) {
        TaskVO vo = new TaskVO();
        vo.setProjectId(projectId);
        vo.setTitle(requestDTO.getTitle());
        vo.setUserId(userId);
        vo.setBranch(requestDTO.getBranch());
        vo.setPriority(requestDTO.getPriority());
        vo.setDueDate(requestDTO.getDueDate());
        vo.setStatus("TODO");
        vo.setContent(requestDTO.getContent());

        taskMapper.insertTask(vo);

        if (requestDTO.getAssigneeIds() != null && !requestDTO.getAssigneeIds().isEmpty()) {
            for (String assigneeId : requestDTO.getAssigneeIds()) {
                taskMapper.insertTaskUser(vo.getTaskId(), assigneeId);
            }

            alarmService.sendTaskAlarm(
                    userId,
                    requestDTO.getAssigneeIds(),
                    projectId.intValue(),
                    vo.getTaskId(),
                    vo.getTitle(),
                    "CREATE"
            );
        }

        insertLog(vo.getTaskId(), "CREATE", "업무를 생성했습니다.", userId);
    }

    // 업무 수정 (알람 및 로그)
    @Transactional
    public void updateTask(Long taskId, String userId, TaskRequestDTO requestDTO) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(taskId);
        vo.setTitle(requestDTO.getTitle());
        vo.setContent(requestDTO.getContent());
        vo.setPriority(requestDTO.getPriority());
        vo.setBranch(requestDTO.getBranch());
        vo.setDueDate(requestDTO.getDueDate());

        taskMapper.updateTask(vo);
        insertLog(taskId, "UPDATE", "업무 상세 정보를 수정했습니다.", userId);

        List<String> targetUsers;
        if (requestDTO.getAssigneeIds() != null) {
            taskMapper.deleteTaskUsers(taskId);
            for (String assigneeId : requestDTO.getAssigneeIds()) {
                taskMapper.insertTaskUser(taskId, assigneeId);
            }
            targetUsers = requestDTO.getAssigneeIds();
        } else {
            targetUsers = taskMapper.selectTaskUsers(taskId);
        }

        TaskVO updatedTask = taskMapper.selectTaskById(taskId);
        alarmService.sendTaskAlarm(userId, targetUsers, updatedTask.getProjectId().intValue(), taskId, updatedTask.getTitle(), "UPDATE");
    }

    // 업무 상태 변경
    @Transactional
    public void updateStatus(Long taskId, String userId, String status) {
        taskMapper.updateTaskStatus(taskId, status);
        insertLog(taskId, "STATUS", "상태를 [" + status + "]로 변경했습니다.", userId);

        TaskVO task = taskMapper.selectTaskById(taskId);
        List<String> assignees = taskMapper.selectTaskUsers(taskId);
        alarmService.sendTaskAlarm(userId, assignees, task.getProjectId().intValue(), taskId, task.getTitle(), "STATUS");
    }

    // 체크리스트 목록 조회
    public List<TaskCheckListVO> getCheckLists(Long taskId) {
        return taskMapper.selectCheckLists(taskId);
    }

    // 체크리스트 추가
    @Transactional
    public void addCheckList(Long taskId, String userId, String content) {
        TaskCheckListVO vo = new TaskCheckListVO();
        vo.setTaskId(taskId);
        vo.setContent(content);
        taskMapper.insertCheckList(vo);

        insertLog(taskId, "CHECKLIST", "체크리스트 [" + content + "] 항목을 추가했습니다.", userId);
        sendChecklistAlarm(taskId, userId);
    }

    // 체크리스트 삭제
    @Transactional
    public void deleteCheckList(Long taskId, Long checklistId, String userId) {
        TaskCheckListVO item = taskMapper.selectCheckListById(checklistId);
        String content = (item != null) ? item.getContent() : "알 수 없는 항목";

        taskMapper.deleteCheckList(checklistId);
        insertLog(taskId, "CHECKLIST", "체크리스트 [" + content + "] 항목을 삭제했습니다.", userId);
        sendChecklistAlarm(taskId, userId);
    }

    // 체크리스트 상태 변경
    @Transactional
    public void toggleCheckList(Long checklistId, boolean isDone, Long taskId, String userId) {
        TaskCheckListVO item = taskMapper.selectCheckListById(checklistId);
        String content = (item != null) ? item.getContent() : "항목";

        taskMapper.updateCheckListStatus(checklistId, isDone);
        String action = isDone ? "완료" : "미완료";
        insertLog(taskId, "CHECKLIST", "체크리스트 [" + content + "] 항목을 " + action + " 처리했습니다.", userId);
        sendChecklistAlarm(taskId, userId);
    }

    // 공통 알람 발송 메서드
    private void sendChecklistAlarm(Long taskId, String senderId) {
        TaskVO task = taskMapper.selectTaskById(taskId);
        if (task == null) return;
        List<String> assignees = taskMapper.selectTaskUsers(taskId);
        alarmService.sendTaskAlarm(senderId, assignees, task.getProjectId().intValue(), taskId, task.getTitle(), "CHECKLIST");
    }

    @Transactional
    public void deleteTask(Long taskId, String requesterId) {
        TaskVO task = taskMapper.selectTaskById(taskId);
        if (task == null) return;
        List<String> assignees = taskMapper.selectTaskUsers(taskId);
        taskMapper.softDeleteTask(taskId);
        alarmService.sendTaskAlarm(requesterId, assignees, task.getProjectId().intValue(), taskId, task.getTitle(), "DELETE");
    }

    public List<TaskResponseDTO> getTaskList(Long projectId) {
        List<TaskVO> tasks = taskMapper.selectTasksByProjectId(projectId);
        return tasks.stream().map(task -> {
            TaskResponseDTO dto = new TaskResponseDTO(task);
            dto.setAssigneeIds(taskMapper.selectTaskUsers(task.getTaskId()));
            return dto;
        }).collect(Collectors.toList());
    }

    public TaskResponseDTO getTaskDetail(Long taskId) {
        TaskVO task = taskMapper.selectTaskById(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found");
        TaskResponseDTO dto = new TaskResponseDTO(task);
        dto.setAssigneeIds(taskMapper.selectTaskUsers(taskId));
        dto.setCheckLists(taskMapper.selectCheckLists(taskId));
        return dto;
    }

    public List<Map<String, Object>> getChats(Long taskId) { return taskMapper.selectChats(taskId); }

    public void addChat(Long taskId, String userId, String content) {
        taskMapper.insertChat(taskId, userId, content);
        TaskVO task = taskMapper.selectTaskById(taskId);
        List<String> assignees = taskMapper.selectTaskUsers(taskId);
        alarmService.sendTaskAlarm(userId, assignees, task.getProjectId().intValue(), taskId, task.getTitle(), "CHAT");
    }

    // 로그 조회
    public List<TaskLogVO> getLogs(Long taskId) {
        return taskMapper.selectLogs(taskId);
    }

    private void insertLog(Long taskId, String type, String content, String userId) {
        taskMapper.insertTaskLog(taskId, type, content, userId);
    }
}