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

    // 업무 생성
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

        if (requestDTO.getAssigneeIds() != null) {
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
    
    // 업무 현황
    public List<TaskResponseDTO> getTaskList(Long projectId) {
        List<TaskVO> tasks = taskMapper.selectTasksByProjectId(projectId);
        return tasks.stream().map(task -> {
            TaskResponseDTO dto = new TaskResponseDTO(task);
            dto.setAssigneeIds(taskMapper.selectTaskUsers(task.getTaskId()));
            return dto;
        }).collect(Collectors.toList());
    }
    
    // 업무 상세
    public TaskResponseDTO getTaskDetail(Long taskId) {
        TaskVO task = taskMapper.selectTaskById(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found");

        TaskResponseDTO dto = new TaskResponseDTO(task);
        dto.setAssigneeIds(taskMapper.selectTaskUsers(taskId));
        dto.setCheckLists(taskMapper.selectCheckLists(taskId));
        return dto;
    }

    // 업무 상태 변경
    @Transactional
    public void updateStatus(Long taskId, String userId, String status) {
        taskMapper.updateTaskStatus(taskId, status);
        insertLog(taskId, "STATUS", "상태를 [" + status + "]로 변경했습니다.", userId);

        TaskVO task = taskMapper.selectTaskById(taskId);
        List<String> assignees = taskMapper.selectTaskUsers(taskId); // 담당자 목록 조회

        alarmService.sendTaskAlarm(
                userId,
                assignees,
                task.getProjectId().intValue(),
                taskId,
                task.getTitle(),
                "STATUS"
        );
    }

    // 업무 수정
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

        alarmService.sendTaskAlarm(
                userId,
                targetUsers, // 이제 비어있지 않음!
                updatedTask.getProjectId().intValue(),
                taskId,
                updatedTask.getTitle(),
                "UPDATE"
        );
    }
    
    //업무 삭제
    @Transactional
    public void deleteTask(Long taskId, String requesterId) {
        TaskVO task = taskMapper.selectTaskById(taskId);
        if (task == null) return;

        List<String> assignees = taskMapper.selectTaskUsers(taskId);

        taskMapper.softDeleteTask(taskId);

        alarmService.sendTaskAlarm(
                requesterId,
                assignees,
                task.getProjectId().intValue(),
                taskId,
                task.getTitle(),
                "DELETE"
        );
    }
    
    // 업무 체크리스트 조회
    public List<TaskCheckListVO> getCheckLists(Long taskId) {
        return taskMapper.selectCheckLists(taskId);
    }
    
    // 업무 체크리스트 추가
    @Transactional
    public void addCheckList(Long taskId, String userId, String content) {
        TaskCheckListVO vo = new TaskCheckListVO();
        vo.setTaskId(taskId);
        vo.setContent(content);
        taskMapper.insertCheckList(vo);
        insertLog(taskId, "CHECKLIST", "새 할 일 [" + content + "] 추가", userId);

        sendChecklistAlarm(taskId, userId);
    }
    
    // 업무 체크리스트 삭제
    @Transactional
    public void deleteCheckList(Long taskId, Long checklistId, String userId) {
        taskMapper.deleteCheckList(checklistId);
        insertLog(taskId, "CHECKLIST", "체크리스트 항목 삭제", userId);

        sendChecklistAlarm(taskId, userId);
    }

    // 업무 체크리스트 체크 및 해제
    @Transactional
    public void toggleCheckList(Long checklistId, boolean isDone, Long taskId, String userId) {
        taskMapper.updateCheckListStatus(checklistId, isDone);
        String action = isDone ? "완료" : "미완료";
        insertLog(taskId, "CHECKLIST", "항목을 [" + action + "] 상태로 변경", userId);

        sendChecklistAlarm(taskId, userId);

    }

    // 체크리스트 알림
    private void sendChecklistAlarm(Long taskId, String senderId) {
        // 1. 업무 정보 조회 (프로젝트 ID, 제목 필요)
        TaskVO task = taskMapper.selectTaskById(taskId);
        if (task == null) return;

        // 2. 담당자 목록 조회 (알림 받을 사람들)
        List<String> assignees = taskMapper.selectTaskUsers(taskId);

        // 3. 알림 서비스 호출
        alarmService.sendTaskAlarm(
                senderId,
                assignees,
                task.getProjectId().intValue(),
                taskId,
                task.getTitle(),
                "CHECKLIST" // 타입 지정
        );
    }

    // 업무 채팅 조회
    public List<Map<String, Object>> getChats(Long taskId) {
        return taskMapper.selectChats(taskId);
    }
    
    // 업무 채팅 추가
    public void addChat(Long taskId, String userId, String content) {
        taskMapper.insertChat(taskId, userId, content);

        TaskVO task = taskMapper.selectTaskById(taskId);
        List<String> assignees = taskMapper.selectTaskUsers(taskId);

        alarmService.sendTaskAlarm(
                userId,
                assignees,
                task.getProjectId().intValue(),
                taskId,
                task.getTitle(),
                "CHAT"
        );
    }
    
    // 업무 로그 조회
    public List<Map<String, Object>> getLogs(Long taskId) {
        return taskMapper.selectLogs(taskId);
    }
    
    // 업무 로그 삽입
    private void insertLog(Long taskId, String type, String content, String userId) {
        taskMapper.insertTaskLog(taskId, type, content, userId);
    }
}