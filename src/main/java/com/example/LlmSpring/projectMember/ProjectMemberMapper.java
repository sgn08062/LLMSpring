package com.example.LlmSpring.projectMember;

import com.example.LlmSpring.projectMember.response.ProjectMemberResponseDTO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectMemberMapper {

    // 1-1. 요청자가 해당 프로젝트의 활성 멤버(deleted_at IS NULL)인지 확인
    boolean existsActiveMember(@Param("projectId") int projectId, @Param("userId") String userId);

    // 1-2. 특정 프로젝트의 활성 멤버 목록 조회
    List<ProjectMemberResponseDTO> selectMemberListByProjectId(@Param("projectId") int projectId);

    // 2-1, 3-1, 4-1. 사용자 프로젝트 권한 체크
    String getProjectRole(@Param("projectId") int projectId, @Param("userId") String userId);

    // 2-2. 활성 사용자 존재 여부 확인
    boolean isUserExists(@Param("userId") String userId);

    // 2-3, 3-2, 4-2. 프로젝트 멤버 데이터 상세 조회 (삭제된 데이터 포함)
    ProjectMemberVO selectMemberRaw(@Param("projectId") int projectId, @Param("userId") String userId);

    // 2-4. 신규 멤버 삽입
    void insertMember(ProjectMemberVO member);

    // 2-5. 탈퇴 멤버 재초대 (상태 복구 및 역할 초기화)
    void updateMemberToInvited(@Param("projectId") int projectId, @Param("userId") String userId);

    // 3-3. 역할 업데이트 실행
    void updateMemberRole(@Param("projectId") int projectId, @Param("userId") String userId, @Param("role") String role);

    // 4-3. 프로젝트 멤버에 대한 소프트 델리트 실행 (deleted_at 업데이트)
    int deleteMember(@Param("projectId") int projectId, @Param("userId") String userId, @Param("deletedAt") LocalDateTime deletedAt);

    // 5. 프로젝트의 사용자 status 확인
    String selectMemberStatus(@Param("projectId") int projectId, @Param("userId") String userId);

    // 6-1. 초대 수락
    void updateMemberStatus(@Param("projectId") int projectId, @Param("userId") String userId, @Param("status") String status);

    // 6-2. 초대 거절
    void declineInvitation(@Param("projectId") int projectId, @Param("userId") String userId);

    // 이슈 담당자용: ACTIVE 멤버만 조회
    List<ProjectMemberResponseDTO> selectActiveMembersByProjectId(@Param("projectId") int projectId);
}
