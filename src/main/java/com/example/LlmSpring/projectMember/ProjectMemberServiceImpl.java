package com.example.LlmSpring.projectMember;

import com.example.LlmSpring.projectMember.request.ProjectMemberInviteRequestDTO;
import com.example.LlmSpring.projectMember.request.ProjectMemberRemoveRequestDTO;
import com.example.LlmSpring.projectMember.request.ProjectMemberRoleRequestDTO;
import com.example.LlmSpring.projectMember.response.ProjectMemberResponseDTO;
import java.time.LocalDateTime;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private final ProjectMemberMapper projectMemberMapper;

    // 프로젝트 멤버 목록 조회 구현체
    @Override
    public List<ProjectMemberResponseDTO> getMemberList(int projectId, String userId) {
        // 1. 존재 여부 확인: 요청 유저가 해당 프로젝트의 '활성 멤버'인가?
        if (!projectMemberMapper.existsActiveMember(projectId, userId)) {
            // 존재하지 않거나 이미 삭제된(Soft Delete) 멤버인 경우 진입 차단
            throw new RuntimeException("해당 프로젝트의 참여 멤버만 목록을 조회할 수 있습니다.");
        }

        // 2. 멤버 목록 반환
        return projectMemberMapper.selectMemberListByProjectId(projectId);
    }

    // 프로젝트 멤버 초대 구현체
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inviteMember(int projectId, String inviterId, ProjectMemberInviteRequestDTO dto) {

        // 0. 초대 권한 확인: 요청자가 OWNER인지 검증
        String inviterRole = projectMemberMapper.getProjectRole(projectId, inviterId);
        if (!"OWNER".equals(inviterRole)) {
            throw new RuntimeException("프로젝트 소유자(OWNER)만 멤버를 초대할 수 있습니다.");
        }

        String inviteeId = dto.getUserId();

        // 1. 자기 자신 초대 방지
        if (inviteeId.equals(inviterId)) {
            throw new RuntimeException("자기 자신을 초대할 수 없습니다.");
        }

        // 2. 피초대자 존재 여부 확인
        if (!projectMemberMapper.isUserExists(inviteeId)) {
            throw new RuntimeException("존재하지 않거나 탈퇴한 사용자입니다.");
        }

        // 3. 기존 프로젝트 멤버 기록 확인
        ProjectMemberVO existingMember = projectMemberMapper.selectMemberRaw(projectId, inviteeId);

        if (existingMember == null) {
            // Case A: 처음 초대하는 경우 -> INSERT
            ProjectMemberVO newMember = ProjectMemberVO.builder()
                    .projectId(projectId)
                    .userId(inviteeId)
                    .build();
            projectMemberMapper.insertMember(newMember);
        } else if (existingMember.getDeletedAt() == null) {
            // Case B: 이미 참여 중이거나 초대된 상태 -> Exception
            throw new RuntimeException("이미 참여 중이거나 초대된 사용자입니다.");
        } else {
            // Case C: 이전에 참여했다가 나간(Soft Delete) 경우 -> UPDATE
            projectMemberMapper.updateMemberToInvited(projectId, inviteeId);
        }
    }

    // 프로젝트 멤버 역할 변경 구현체
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMemberRole(int projectId, String requesterId, ProjectMemberRoleRequestDTO dto) {
        String targetId = dto.getTargetUserId();
        String newRole = dto.getRole();

        // 1. 잘못된 역할 값 차단
        List<String> validRoles = Arrays.asList("ADMIN", "MEMBER");
        if (!validRoles.contains(newRole)) {
            throw new RuntimeException("잘못된 역할 값입니다. (ADMIN 또는 MEMBER만 가능)");
        }

        // 2. 자기 자신 역할 변경 금지
        if (requesterId.equals(targetId)) {
            throw new RuntimeException("자기 자신의 역할은 변경할 수 없습니다.");
        }

        // 3. 요청자 권한 확인 (OWNER 또는 ADMIN인지)
        String requesterRole = projectMemberMapper.getProjectRole(projectId, requesterId);
        if ((!"OWNER".equals(requesterRole) && !"ADMIN".equals(requesterRole))) {
            throw new RuntimeException("멤버 역할을 변경할 권한이 없습니다.");
        }

        // 4. 대상자 상태 및 현재 역할 조회
        ProjectMemberVO targetMember = projectMemberMapper.selectMemberRaw(projectId, targetId);
        if (targetMember == null || targetMember.getDeletedAt() != null) {
            throw new RuntimeException("해당 프로젝트의 활성 멤버가 아닙니다.");
        }

        // 5. 대상자 상태 체크 (INVITED 상태는 변경 불가)
        if ("INVITED".equals(targetMember.getStatus())) {
            throw new RuntimeException("초대 대기 중인 사용자의 역할은 변경할 수 없습니다.");
        }

        // 6. 상위 권한 침범 방지
        // - 대상이 OWNER인 경우 절대 변경 불가
        if ("OWNER".equals(targetMember.getRole())) {
            throw new RuntimeException("프로젝트 소유자의 역할은 변경할 수 없습니다.");
        }
        // - ADMIN이 다른 ADMIN의 역할을 변경하려고 하는 경우 차단 (하위 권한만 변경 가능 원칙)
        if ("ADMIN".equals(requesterRole) && "ADMIN".equals(targetMember.getRole())) {
            throw new RuntimeException("ADMIN은 다른 ADMIN의 역할을 변경할 수 없습니다.");
        }

        // 7. 동일 역할 변경 차단 (불필요한 DB 연산 방지)
        if (targetMember.getRole().equals(newRole)) {
            throw new RuntimeException("이미 해당 역할을 보유하고 있습니다.");
        }

        // 8. 업데이트 실행
        projectMemberMapper.updateMemberRole(projectId, targetId, newRole);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(int projectId, String requesterId, ProjectMemberRemoveRequestDTO dto) {
        String targetId = dto.getTargetUserId();
        String action = dto.getAction();

        // 1. 기본 정보 조회
        String requesterRole = projectMemberMapper.getProjectRole(projectId, requesterId);
        ProjectMemberVO targetMember = projectMemberMapper.selectMemberRaw(projectId, targetId);

        // 대상 존재 여부 및 활성 상태 확인
        if (targetMember == null || targetMember.getDeletedAt() != null) {
            throw new RuntimeException("해당 프로젝트의 활성 멤버가 아닙니다.");
        }

        // 2. 액션별 비즈니스 로직 분기
        switch (action) {
            case "추방":
                validateExpulsion(requesterRole, targetMember);
                break;
            case "나가기":
                validateLeave(requesterId, targetId, requesterRole);
                break;
            case "초대취소":
                validateCancelInvite(requesterRole, targetMember);
                break;
            default:
                throw new RuntimeException("잘못된 작업 이름입니다.");
        }

        // 3. 소프트 델리트 실행
        projectMemberMapper.deleteMember(projectId, targetId, LocalDateTime.now());
    }

    // [추방 로직]
    private void validateExpulsion(String requesterRole, ProjectMemberVO target) {
        // 관리자 권한 확인
        if (!"OWNER".equals(requesterRole) && !"ADMIN".equals(requesterRole)) {
            throw new RuntimeException("추방 권한이 없습니다.");
        }
        // 상위 권한 침범 방지: ADMIN은 MEMBER만 추방 가능
        if ("ADMIN".equals(requesterRole) && !"MEMBER".equals(target.getRole())) {
            throw new RuntimeException("ADMIN은 다른 ADMIN이나 OWNER를 추방할 수 없습니다.");
        }
        // OWNER 추방 불가
        if ("OWNER".equals(target.getRole())) {
            throw new RuntimeException("프로젝트 소유자는 추방할 수 없습니다.");
        }
        // 상태 불일치 확인
        if (!"ACTIVE".equals(target.getStatus())) {
            throw new RuntimeException("참여 중인 멤버만 추방할 수 있습니다.");
        }
    }

    // [나가기 로직]
    private void validateLeave(String requesterId, String targetId, String requesterRole) {
        // 타인 강제 퇴출 방지
        if (!requesterId.equals(targetId)) {
            throw new RuntimeException("본인만 '나가기'를 수행할 수 있습니다.");
        }
        // OWNER 나가기 불가
        if ("OWNER".equals(requesterRole)) {
            throw new RuntimeException("소유자는 프로젝트를 나갈 수 없습니다. 소유권 이전이 필요합니다.");
        }
    }

    // [초대취소 로직]
    private void validateCancelInvite(String requesterRole, ProjectMemberVO target) {
        // ADMIN 이상 권한 확인
        if (!"OWNER".equals(requesterRole) && !"ADMIN".equals(requesterRole)) {
            throw new RuntimeException("초대 취소 권한이 없습니다.");
        }
        // 상태 확인: 오직 INVITED 상태만 취소 가능
        if (!"INVITED".equals(target.getStatus())) {
            throw new RuntimeException("초대 대기 중인 사용자만 취소를 수행할 수 있습니다.");
        }
    }


}
