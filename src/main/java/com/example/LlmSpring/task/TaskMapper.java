package com.example.LlmSpring.task;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TaskMapper {
    // 업무
    void insertTask(TaskVO taskVO);

    List<TaskVO> selectTasksByProjectId(@Param("projectId") Long projectId);

    TaskVO selectTaskById(Long taskId);

    void updateTaskStatus(@Param("taskId") Long taskId, @Param("status") String status);

    void updateTask(TaskVO taskVO);

    void softDeleteTask(Long taskId);

    // 권한 조회
    String getProjectRole(@Param("projectId") Long projectId, @Param("userId") String userId);

    // 담당자
    void insertTaskUser(@Param("taskId") Long taskId, @Param("userId") String userId);

    List<String> selectTaskUsers(Long taskId);

    void deleteTaskUsers(Long taskId);

    // 체크리스트
    TaskCheckListVO selectCheckListById(Long checkListId);

    List<TaskCheckListVO> selectCheckLists(Long taskId);

    void insertCheckList(TaskCheckListVO CheckListVO);

    void deleteCheckList(Long checkListId);

    void updateCheckListStatus(@Param("checkListId") Long checkListId, @Param("status") Boolean status);

    // 채팅 & 로그
    List<Map<String, Object>> selectChats(Long taskId);

    void insertChat(@Param("taskId") Long taskId, @Param("userId") String userId, @Param("content") String content);

    // 로그 삽입
    void insertTaskLog(@Param("taskId") Long taskId, @Param("type") String type, @Param("content") String content, @Param("userId") String userId);

    // 로그 조회
    List<TaskLogVO> selectLogs(Long taskId);
}