package com.example.LlmSpring.task;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.scheduling.config.Task;

import java.util.List;
import java.util.Map;

@Mapper
public interface TaskMapper {
    // 업무
    void insertTask(TaskVO taskVO);
    List<TaskVO>  selectTasksByProjectId(Long projectId);
    TaskVO selectTaskById(Long taskId);
    void updateTaskStatus(@Param("taskId") Long taskId, @Param("status") String status);
    void updateTask(TaskVO taskVO);
    void softDeleteTask(Long taskId);

    // 담당자 (TaskUser)
    void insertTaskUser(@Param("taskId") Long taskId, @Param("userId") String userId);
    List<String> selectTaskUsers(Long taskId);

    // 체크리스트
    List<TaskCheckListVO> selectCheckLists(Long taskId);
    void insertCheckList(TaskCheckListVO CheckListVO);
    void deleteCheckList(Long checkListId);
    void updateCheckListStatus(@Param("checkListId") Long checkListId, @Param("status") Boolean status);

    // 채팅 & 로그
    List<Map<String, Object>> selectChats(Long taskId);
    void insertChat(@Param("taskId") Long taskId, @Param("userId") String userId, @Param("content") String content);
    List<Map<String, Object>> selectsLogs(Long taskId);
}
