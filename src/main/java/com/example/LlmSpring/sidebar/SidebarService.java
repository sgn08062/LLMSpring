package com.example.LlmSpring.sidebar;

import com.example.LlmSpring.sidebar.response.ProjectSidebarResponseDTO;
import com.example.LlmSpring.sidebar.response.SidebarResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SidebarService {

    private final SidebarMapper sidebarMapper;

    //메인 사이드바
    public SidebarResponseDTO getMainSidebar(String userId) {
        List<SidebarResponseDTO.SidebarProjectDTO> allProjects = sidebarMapper.selectMySidebarProject(userId);

        SidebarResponseDTO response = new SidebarResponseDTO();
        for (SidebarResponseDTO.SidebarProjectDTO project: allProjects){
            //전체 목록에 추가
            response.getProjects().add(project);

            //즐겨찾기
            if (project.isFavorite()) {
                response.getFavorites().add(project);
            }
        }
        return response;
    }

    //프로젝트 사이드바
    public ProjectSidebarResponseDTO getProjectSidebar(Long projectId, String userId) {
        ProjectSidebarResponseDTO response = new ProjectSidebarResponseDTO();
        response.setProjectId(projectId);

        //프로젝트 정보 조회
        ProjectSidebarResponseDTO projectInfo = sidebarMapper.selectProjectInfo(projectId);

        if (projectInfo != null) {
            response.setProjectName(projectInfo.getProjectName());
            response.setProjectStatus(projectInfo.getProjectStatus());
            response.setDailyReportTime(projectInfo.getDailyReportTime());
            response.setGithubUrl(projectInfo.getGithubUrl());
        }

        //오늘 리포트 작성 여부
        String today = LocalDate.now().toString();
        response.setReportWritten(sidebarMapper.countTodayMyReport(projectId, userId, today) > 0);

        //내 잔여 업무 목록
        response.setMyTasks(sidebarMapper.selectMyActiveTasks(projectId, userId));

        // 내 잔여 이슈 목록 조회 및 설정
        response.setMyIssues(sidebarMapper.selectMyActiveIssues(projectId, userId));

        return response;
    }

    //즐겨찾기
    @Transactional
    public boolean toggleFavorite(Long projectId, String userId) {
        // 1. 이미 즐겨찾기인지 확인
        int count = sidebarMapper.existsFavorite(projectId, userId);

        if (count > 0) {
            // 이미 있으면 -> 삭제 (해제)
            sidebarMapper.deleteFavorite(projectId, userId);
            return false; // 결과: 즐겨찾기 아님(false)
        } else {
            // 없으면 -> 추가 (등록)
            sidebarMapper.insertFavorite(projectId, userId);
            return true; // 결과: 즐겨찾기 됨(true)
        }
    }
}
