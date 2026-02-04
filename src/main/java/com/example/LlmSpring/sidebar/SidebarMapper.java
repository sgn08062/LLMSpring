package com.example.LlmSpring.sidebar;

import com.example.LlmSpring.sidebar.response.ProjectSidebarResponseDTO;
import com.example.LlmSpring.sidebar.response.SidebarResponseDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SidebarMapper {
    //1. 메인 사이드바: 내 프로젝트 목록 (즐겨찾기 포함)
    List<SidebarResponseDTO.SidebarProjectDTO> selectMySidebarProject(@Param("userId") String userId);

    //2. 프로젝트 사이드바: 기본 정보 (이름, 상태, 리포트 마감시간)
    Map<String, Object> selectProjectInfo(@Param("projectId") Long projectId);

    //3. 프로젝트 사이드바: 오늘 리포트 작성 여부 (1 = 작성, 0 = 미작성)
    int countTodayMyReport(@Param("projectId") Long projectId, @Param("userId") String userId, @Param("date") String date);

    //4. 프로젝트 사이드바: 내 잔여 업무 목록 (상위 5개)
    List<ProjectSidebarResponseDTO.SidebarTaskDTO> selectMyActiveTasks(@Param("projectId") Long projectId, @Param("userId") String userId);

    //5. 프로젝트 사이드바: 내 이슈 업무 목록 (상위 5개)
    List<ProjectSidebarResponseDTO.SidebarIssueDTO> selectMyActiveIssues(
            @Param("projectId") Long projectId,
            @Param("userId") String userId
    );

    // 즐겨찾기 관련 메서드
    // 1. 이미 즐겨찾기 되어있는지 확인
    int existsFavorite(@Param("projectId") Long projectId, @Param("userId") String userId);

    // 2. 즐겨찾기 추가
    void insertFavorite(@Param("projectId") Long projectId, @Param("userId") String userId);

    // 3. 즐겨찾기 해제
    void deleteFavorite(@Param("projectId") Long projectId, @Param("userId") String userId);
}
