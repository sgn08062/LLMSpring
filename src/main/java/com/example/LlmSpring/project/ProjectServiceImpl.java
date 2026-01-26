package com.example.LlmSpring.project;

import com.example.LlmSpring.project.request.ProjectCreateRequestDTO;
import com.example.LlmSpring.project.request.ProjectUpdateRequestDTO;
import com.example.LlmSpring.project.response.ProjectListResponseDTO;
import com.example.LlmSpring.projectMember.ProjectMemberVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("projectService")
@RequiredArgsConstructor // @Autowired 대신 권장하는 final + @RequiredArgsConstructor 방식
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class) // 예외 발생 시 롤백
    public int createProject(ProjectCreateRequestDTO dto, String ownerId) { // 프로젝트 생성

        // 1. DTO 데이터를 ProjectVO로 변환 및 프로젝트 저장
        ProjectVO project = ProjectVO.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .startDate(LocalDateTime.now())        // 시작일을 현재 시간으로 설정
                .endDate(dto.getEndDate())
                .githubRepoUrl(dto.getGitUrl())
                .dailyReportTime(dto.getReportTime())
                .build();

        projectMapper.insertProject(project); // 만약에 DB 처리 실패 시 자동 롤백 예외처리
        Integer projectId = project.getProjectId();

        // 2. 통합 멤버 리스트 생성
        List<ProjectMemberVO> membersToInsert = new ArrayList<>();

        // 2-1. 생성자(Owner) 추가: JOINED 상태
        membersToInsert.add(ProjectMemberVO.builder()
                .projectId(projectId)
                .userId(ownerId)
                .role("OWNER")
                .status("JOINED") // 요구사항 반영
                .joinedAt(LocalDateTime.now())
                .build());

        // 2-2. 초대 멤버들 추가: INVITED 상태
        if (dto.getMembers() != null && !dto.getMembers().isEmpty()) {
            for (String memberId : dto.getMembers()) {
                // 소유자가 초대 명단에 중복 포함된 경우 제외
                if (!memberId.equals(ownerId)) {
                    membersToInsert.add(ProjectMemberVO.builder()
                            .projectId(projectId)
                            .userId(memberId)
                            .role("MEMBER")
                            .status("INVITED") // 요구사항 반영
                            .build());
                }
            }
        }

        // 3. Mapper를 통한 Bulk Insert 실행
        projectMapper.insertProjectMembers(membersToInsert);

        return projectId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateProject(int projectId, ProjectUpdateRequestDTO dto) { // 프로젝트 내용 수정
        // 1. DTO 데이터를 VO 객체로 변환 (startDate 제외)
        ProjectVO project = ProjectVO.builder()
                .projectId(projectId)
                .name(dto.getName())
                .description(dto.getDescription())
                .githubRepoUrl(dto.getGitUrl())
                .endDate(dto.getEndDate())     // 마감일만 반영
                .dailyReportTime(dto.getReportTime())
                .build();

        // 2. 수정 실행
        return projectMapper.updateProject(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateProjectStatus(int projectId, String status) { // 프로젝트 ACTIVE <-> DONE(아카이브) 상태 변경
        return projectMapper.updateProjectStatus(projectId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteProject(int projectId) { // 프로젝트 soft_delete
        // 현재 시간으로부터 7일 뒤의 시간을 계산하고 DB에 업데이트
        LocalDateTime deleteDate = LocalDateTime.now().plusDays(7);

        return projectMapper.deleteProject(projectId, deleteDate);
    }

    // VO를 목록용 DTO로 변환하는 공통 메서드
    private ProjectListResponseDTO convertToProjectListDTO(ProjectVO vo) {
        return ProjectListResponseDTO.builder()
                .projectId(vo.getProjectId())
                .name(vo.getName())
                .status(vo.getStatus())
                .startDate(vo.getStartDate())
                .endDate(vo.getEndDate())
                .deletedAt(vo.getDeletedAt())
                .build();
    }

    @Override
    public List<ProjectListResponseDTO> getActiveProjects(String userId) { // 사용자가 참여중인 ACTIVE 상태의 프로젝트 목록 조회

        List<ProjectVO> voList = projectMapper.getActiveProjectList(userId);

        // 2. DTO로 변환하여 반환
        return voList.stream()
                .map(this::convertToProjectListDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProjectListResponseDTO> getDoneProjects(String userId) { // 사용자가 참여중인 DONE 상태의 프로젝트 목록 조회
        return projectMapper.getDoneProjectList(userId).stream()
                .map(this::convertToProjectListDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProjectListResponseDTO> getTrashProjects(String userId) { // 사용자가 참여중인 삭제 예정의 프로젝트 목록 조회
        return projectMapper.getTrashProjectList(userId).stream()
                .map(this::convertToProjectListDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int restoreProject(int projectId, String userId) { // 삭제 취소 요청
        // 1. 권한 확인: 요청한 사용자가 OWNER인지 확인
        String role = projectMapper.getProjectRole(projectId, userId);
        if (!"OWNER".equals(role)) {
            throw new RuntimeException("프로젝트 소유자(OWNER)만 삭제를 취소할 수 있습니다.");
        }

        // 2. 상태 및 시간 확인: 삭제 예정 프로젝트인지, 유예 기간이 남았는지 확인
        ProjectVO project = projectMapper.selectProjectForRestore(projectId);

        if (project == null || project.getDeletedAt() == null) {
            throw new RuntimeException("삭제 대기 중인 프로젝트가 아닙니다.");
        }

        // 도메인 규칙: deleted_at이 현재 날짜보다 큰 경우(미래)에만 취소 가능
        if (project.getDeletedAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("삭제 유예 기간(7일)이 지나 복구할 수 없습니다.");
        }

        // 3. 복구 실행: deleted_at을 null로 업데이트
        return projectMapper.restoreProject(projectId);
    }

}
