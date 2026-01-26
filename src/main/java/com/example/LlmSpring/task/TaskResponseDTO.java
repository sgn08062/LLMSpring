package com.example.LlmSpring.task;

import lombok.Data;

import java.util.List;

@Data
public class TaskResponseDTO {
    private Long taskId;
    private String title;
    private String content;
    private String status;
    private Integer priority;
    private String branch;
    private String dueDate;
    private String userId;
    private List<String> assigneeIds; // 담당자 목록
    private List<TaskCheckListVO> checkLists; // 상세 조회용

    public TaskResponseDTO(TaskVO vo) {
        this.taskId = vo.getTaskId();
        this.title = vo.getTitle();
        this.content = vo.getContent();
        this.status = vo.getStatus();
        this.priority = vo.getPriority();
        this.branch = vo.getBranch();
        this.dueDate = vo.getDueDate() != null ? vo.getDueDate().toString() : null;
        this.userId = vo.getUserId();
    }
}
