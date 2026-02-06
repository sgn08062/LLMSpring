package com.example.LlmSpring.project;

import com.example.LlmSpring.project.request.ProjectCreateRequestDTO;
import com.example.LlmSpring.project.request.ProjectUpdateRequestDTO;
import com.example.LlmSpring.project.response.ProjectDashboardResponseDTO;
import com.example.LlmSpring.project.response.ProjectDetailResponseDTO;
import com.example.LlmSpring.project.response.ProjectListResponseDTO;
import java.util.List;

public interface ProjectService {

    // 프로젝트 생성 기능 (생성된 프로젝트 ID 반환)
    int createProject(ProjectCreateRequestDTO dto, String ownerId);

    // 프로젝트 수정 기능
    int updateProject(int projectId, String userId, ProjectUpdateRequestDTO dto);

    // 프로젝트 상태 수정 기능
    int updateProjectStatus(int projectId, String userId, String status);

    // 프로젝트 삭제
    int deleteProject(int projectId, String userId);

    // 사용자가 참여중인 프로젝트 목록 조회 (ACTIVE만)
    List<ProjectListResponseDTO> getActiveProjects(String userId);

    // 사용자가 참여중인 프로젝트 목록 조회 (DONE만)
    List<ProjectListResponseDTO> getDoneProjects(String userId);

    // 사용자가 참여중인 삭제 예정인 프로젝트 목록 조회
    List<ProjectListResponseDTO> getTrashProjects(String userId);

    // 삭제 취소 기능 추가
    int restoreProject(int projectId, String userId);

    // 단일 프로젝트 상세 조회
    ProjectDetailResponseDTO getProjectDetail(int projectId, String userId);

    // 대시보드 통계 조회
    ProjectDashboardResponseDTO getProjectDashboardStats(Long projectId, String userId);

}
