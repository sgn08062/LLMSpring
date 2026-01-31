package com.example.LlmSpring.projectMember;

import com.example.LlmSpring.projectMember.request.ProjectMemberInviteRequestDTO;
import com.example.LlmSpring.projectMember.request.ProjectMemberRemoveRequestDTO;
import com.example.LlmSpring.projectMember.request.ProjectMemberRoleRequestDTO;
import com.example.LlmSpring.projectMember.response.ProjectMemberResponseDTO;
import java.util.List;

public interface ProjectMemberService {

    /**
     * 1. 프로젝트 참여 멤버 목록 조회
     * @param projectId 대상 프로젝트 ID
     * @param userId 조회를 요청하는 사용자 ID
     * @return 멤버 상세 정보 리스트
     */
    List<ProjectMemberResponseDTO> getMemberList(int projectId, String userId);

    /**
     * 2. 프로젝트 멤버 초대
     * @param projectId 대상 프로젝트 ID
     * @param inviterId 초대를 시도하는 사용자(OWNER) ID
     * @param dto 초대 대상 사용자 ID 정보를 담은 DTO
     */
    void inviteMember(int projectId, String inviterId, ProjectMemberInviteRequestDTO dto);

    /**
     * 3. 멤버 역할(Role) 변경
     * @param projectId 대상 프로젝트 ID
     * @param requesterId 변경을 시도하는 사용자(OWNER/ADMIN) ID
     * @param dto 변경 대상자 및 새로운 역할 정보를 담은 DTO
     */
    void updateMemberRole(int projectId, String requesterId, ProjectMemberRoleRequestDTO dto);

    /**
     * 4. 멤버 제거 (추방, 나가기, 초대취소)
     * @param projectId 대상 프로젝트 ID
     * @param requesterId 작업을 수행하는 사용자 ID
     * @param dto 작업 대상자 및 행위(액션) 정보를 담은 DTO
     */
    void removeMember(int projectId, String requesterId, ProjectMemberRemoveRequestDTO dto);

    // 5. 초대 수락
    void acceptInvitation(int projectId, String userId);

    // 6. 초대 거절
    void declineInvitation(int projectId, String userId);
}
