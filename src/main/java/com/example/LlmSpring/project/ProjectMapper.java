package com.example.LlmSpring.project;

import com.example.LlmSpring.project.response.ProjectDashboardResponseDTO;
import com.example.LlmSpring.project.response.ProjectListResponseDTO;
import com.example.LlmSpring.projectMember.ProjectMemberVO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 데이터 접근을 위한 인터페이스

@Mapper
public interface ProjectMapper {

    // 1. 프로젝트 기본 정보 삽입 (성공 아니면 에러라서 void 선언)
    void insertProject(ProjectVO project);

    // (Batch Insert <- 일반 Insert보다 통신 비용 감소)
    // 1-2. 멤버들 대량 삽입 (VO 리스트를 전달하여 Role/Status 구분 처리)
    void insertProjectMembers(@Param("members") List<ProjectMemberVO> members);

    // 2. 프로젝트 정보 수정 (영향을 받은 행의 수 반환)
    int updateProject(ProjectVO project);

    // 3. 프로젝트 상태 변경 (ACTIVE <-> DONE)
    int updateProjectStatus(@Param("projectId") int projectId, @Param("status") String status);

    // 4. 프로젝트 삭제 (Soft Delete: deleted_at 날짜 기록)
    int deleteProject(@Param("projectId") int projectId, @Param("deletedAt") LocalDateTime deletedAt);

    // 5. 사용자가 참여 중인 활성 프로젝트 목록 조회 (status = 'ACTIVE')
    List<ProjectVO> getActiveProjectList(@Param("userId") String userId);

    // 6. 사용자가 참여 중인 완료 프로젝트 목록 조회 (status = 'DONE')
    List<ProjectVO> getDoneProjectList(@Param("userId") String userId);

    // 7. 참여 중인 삭제 예정 프로젝트 목록 조회 (deleted_at IS NOT NULL)
    List<ProjectVO> getTrashProjectList(@Param("userId") String userId);

    // 8. 삭제 취소를 위해 프로젝트 상세 정보와 소유자 정보를 함께 조회
    // (Project와 ProjectMember를 조인하여 한 번에 확인하거나, 각각 조회 가능)
    ProjectVO selectProjectForRestore(@Param("projectId") int projectId);

    // 9. 특정 유저가 해당 프로젝트의 OWNER인지 확인
    String getProjectRole(@Param("projectId") int projectId, @Param("userId") String userId);

    // 10. 삭제 취소 실행 (deleted_at을 null로 변경)
    int restoreProject(@Param("projectId") int projectId);

    // 11. 프로젝트 정보 조회
    ProjectVO selectProjectById(@Param("projectId") Long projectId);

    // 12. 특정 프로젝트 내에서 ACTIVE 상태이며 삭제되지 않은 멤버의 수를 반환(이슈 담당자 생성 관련)
    int countActiveProjectMembers(@Param("projectId") int projectId, @Param("userIds") List<String> userIds);

    // 13. 사용자가 참여 중인 프로젝트의 상세 통계 정보를 포함한 목록 조회
    List<ProjectListResponseDTO> getDetailedActiveProjectList(@Param("userId") String userId);

    // 14. 완료된 프로젝트 상세 조회 (추가)
    List<ProjectListResponseDTO> getDetailedDoneProjectList(@Param("userId") String userId);

    // 15. 휴지통 프로젝트 상세 조회 (추가)
    List<ProjectListResponseDTO> getDetailedTrashProjectList(@Param("userId") String userId);

    // 16. 프로젝트 이름 조회
    String getProjectName(int projectId);

    // 17. 통계 조회
    ProjectDashboardResponseDTO selectProjectStats(Long projectId);


    // == 스케줄링 관련 메서드 == //
    // [Scheduler] 1. 마감일이 '내일'인 ACTIVE 프로젝트 조회
    List<ProjectVO> getProjectsDueTomorrow();

    // [Scheduler] 2. 마감일이 지났는데 아직 ACTIVE 상태인 프로젝트 조회
    List<ProjectVO> getOverdueActiveProjects();

    // [Scheduler] 3. 특정 프로젝트의 알림 대상 멤버 ID 조회 (ACTIVE 상태 & 탈퇴 안 한 사람)
    List<String> getActiveMemberIds(@Param("projectId") int projectId);

    // [Scheduler] 4. 프로젝트 상태 일괄 변경 (ACTIVE -> DONE)
    // 여러 프로젝트를 한 번에 업데이트하기 위해 List<Integer>를 받습니다.
    void updateProjectsStatusToDone(@Param("projectIds") List<Integer> projectIds);

    // [Scheduler] 5. 영구 삭제 D-1 프로젝트 조회
    List<ProjectVO> getProjectsDueForHardDeleteTomorrow();

    List<ProjectVO> getProjectsDueForHardDeleteToday();

}
