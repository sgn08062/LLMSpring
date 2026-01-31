package com.example.LlmSpring.project;

import com.example.LlmSpring.alarm.AlarmService;
import com.example.LlmSpring.project.request.ProjectCreateRequestDTO;
import com.example.LlmSpring.project.request.ProjectUpdateRequestDTO;
import com.example.LlmSpring.project.response.ProjectDetailResponseDTO;
import com.example.LlmSpring.project.response.ProjectListResponseDTO;
import com.example.LlmSpring.projectMember.ProjectMemberMapper;
import com.example.LlmSpring.projectMember.ProjectMemberVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("projectService")
@RequiredArgsConstructor // @Autowired 대신 권장하는 final + @RequiredArgsConstructor 방식
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final AlarmService alarmService;
    private final ProjectMemberMapper projectMemberMapper;

    /**
     * 프로젝트 생성 및 멤버 초기화
     * 1. 프로젝트 정보를 생성합니다.
     * 2. 생성자를 해당 프로젝트의 'OWNER' 및 'ACTIVE' 상태로 등록합니다.
     * 3. 초대된 멤버들을 'MEMBER' 및 'INVITED' 상태로 일괄 등록합니다.
     * @Transactional을 통해 이 모든 과정이 하나라도 실패하면 전체 롤백됩니다.
     */
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

        // 2-1. 생성자(Owner) 자동 등록: 본인이 만든 것이므로 즉시 ACTIVE 상태
        membersToInsert.add(ProjectMemberVO.builder()
                .projectId(projectId)
                .userId(ownerId)
                .role("OWNER")
                .status("ACTIVE") // 요구사항 반영
                .joinedAt(LocalDateTime.now())
                .build());

        // 2-2. 초대 멤버 등록: 수락이 필요한 INVITED 상태
        if (dto.getMembers() != null && !dto.getMembers().isEmpty()) {
            for (String memberId : dto.getMembers()) {

                if (!memberId.equals(ownerId)) { // 중복 등록 방지
                    membersToInsert.add(ProjectMemberVO.builder()
                            .projectId(projectId)
                            .userId(memberId)
                            .role("MEMBER")
                            .status("INVITED")
                            .build());
                }

                alarmService.sendInviteAlarm(ownerId, memberId, projectId);
            }
        }

        // 3. 일괄 삽입 실행
        projectMapper.insertProjectMembers(membersToInsert);

        return projectId;
    }

    /**
     * 프로젝트 상세 정보 수정
     * ACTIVE 상태이며 삭제되지 않은 프로젝트만 수정 가능
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateProject(int projectId, String userId, ProjectUpdateRequestDTO dto) { // 프로젝트 내용 수정

        // 1. 권한 확인: 요청한 사용자가 해당 프로젝트의 OWNER인지 확인
        // ProjectMapper의 getProjectRole을 사용하여 역할 정보를 가져옵니다.
        String role = projectMapper.getProjectRole(projectId, userId);

        if (!"OWNER".equals(role)) {
            throw new RuntimeException("프로젝트 소유자(OWNER)만 정보를 수정할 수 있습니다.");
        }

        // 2. DTO 데이터를 VO 객체로 변환 (startDate 제외)
        ProjectVO project = ProjectVO.builder()
                .projectId(projectId)
                .name(dto.getName())
                .description(dto.getDescription())
                .githubRepoUrl(dto.getGitUrl())
                .endDate(dto.getEndDate())     // 마감일만 반영
                .dailyReportTime(dto.getReportTime())
                .build();

        // 3. 수정 실행
        return projectMapper.updateProject(project);
    }

    /**
     * 프로젝트 상태 변경 (ACTIVE <-> DONE)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateProjectStatus(int projectId, String userId, String status) { // 프로젝트 ACTIVE <-> DONE(아카이브) 상태 변경

        // 1. 권한 확인: OWNER 여부 검증
        String role = projectMapper.getProjectRole(projectId, userId);
        if (!"OWNER".equals(role)) {
            throw new RuntimeException("프로젝트 소유자(OWNER)만 상태를 변경할 수 있습니다.");
        }

        return projectMapper.updateProjectStatus(projectId, status);
    }

    /**
     * [Soft Delete] 프로젝트 삭제 유예 처리
     * 즉시 삭제하지 않고 현재 시간으로부터 7일 뒤를 삭제 예정일로 기록합니다.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteProject(int projectId, String userId) { // 프로젝트 soft_delete

        // 1. 권한 확인: OWNER 여부 검증
        String role = projectMapper.getProjectRole(projectId, userId);
        if (!"OWNER".equals(role)) {
            throw new RuntimeException("프로젝트 소유자(OWNER)만 프로젝트를 삭제할 수 있습니다.");
        }

        // 현재 시간으로부터 7일 뒤의 시간을 계산하고 DB에 업데이트
        LocalDateTime deleteDate = LocalDateTime.now().plusDays(7);

        return projectMapper.deleteProject(projectId, deleteDate);
    }


    /**
     * 사용자가 참여 중인 상태별 프로젝트 목록 조회 (ACTIVE)
     */
    @Override
    public List<ProjectListResponseDTO> getActiveProjects(String userId) {
        // 통계 정보가 포함된 확장된 매퍼 메서드 호출
        return projectMapper.getDetailedActiveProjectList(userId);
    }

    /**
     * 사용자가 참여 중인 상태별 프로젝트 목록 조회 (DONE)
     */
    @Override
    public List<ProjectListResponseDTO> getDoneProjects(String userId) { // 사용자가 참여중인 DONE 상태의 프로젝트 목록 조회
        return projectMapper.getDetailedDoneProjectList(userId);
    }

    /**
     * 사용자가 참여 중인 상태별 프로젝트 목록 조회 (TRASH)
     */
    @Override
    public List<ProjectListResponseDTO> getTrashProjects(String userId) { // 사용자가 참여중인 삭제 예정의 프로젝트 목록 조회
        return projectMapper.getDetailedTrashProjectList(userId);
    }

    /**
     * 사용자가 참여 중인 프로젝트 단일 상세 정보 조회 (TRASH)
     */
    @Override
    public ProjectDetailResponseDTO getProjectDetail(int projectId, String userId) {
        // 1. 프로젝트 조회 (ProjectMapper.xml에 selectProjectById는 이미 존재함)

        ProjectVO project = projectMapper.selectProjectById((long) projectId);

        // 2. 프로젝트 존재 및 삭제 여부 확인
        if (project == null) {
            throw new RuntimeException("존재하지 않는 프로젝트입니다.");
        }

        // 3. 권한 확인: 요청자가 해당 프로젝트의 활성 멤버인지 확인
        String memberStatus = projectMemberMapper.selectMemberStatus(projectId, userId);

        if(memberStatus == null){
            throw new RuntimeException("해당 프로젝트의 멤버만 상세 정보를 조회할 수 있습니다.");
        }

        // 4. VO -> DetailDTO 변환
        return ProjectDetailResponseDTO.builder()
                .projectId(project.getProjectId())
                .name(project.getName())
                .description(project.getDescription())
                .startDate(project.getStartDate())
                .endDate(project.getEndDate())
                .status(project.getStatus())
                .githubRepoUrl(project.getGithubRepoUrl())
                .dailyReportTime(project.getDailyReportTime())
                .githubDefaultBranch(project.getGithubDefaultBranch())
                .githubConnectedStatus(project.getGithubConnectedStatus())
                .currentUserStatus(memberStatus)
                .deletedAt(project.getDeletedAt())
                .build();
    }

    /**
     * [도메인 규칙] 프로젝트 삭제 취소 (복구)
     * 1. 요청 유저가 해당 프로젝트의 소유자(OWNER)인지 확인합니다.
     * 2. 삭제 대기 상태이며 유예 기간(7일)이 경과하지 않았는지 확인합니다.
     */
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

        // 도메인 규칙: 현재 시간이 삭제 예정 시간(deleted_at) 이전인 경우에만 복구 가능
        if (project.getDeletedAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("삭제 유예 기간(7일)이 지나 복구할 수 없습니다.");
        }

        // 3. 복구 실행: deleted_at을 null로 업데이트
        return projectMapper.restoreProject(projectId);
    }

}
