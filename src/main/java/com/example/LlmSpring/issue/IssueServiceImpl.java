package com.example.LlmSpring.issue;

import com.example.LlmSpring.alarm.AlarmService;
import com.example.LlmSpring.issue.request.IssueCreateRequestDTO;
import com.example.LlmSpring.issue.response.IssueDetailResponseDTO;
import com.example.LlmSpring.issue.response.IssueListResponseDTO;
import com.example.LlmSpring.issue.request.IssueUpdateRequestDTO;
import com.example.LlmSpring.project.ProjectMapper;
import com.example.LlmSpring.projectMember.ProjectMemberMapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IssueServiceImpl implements IssueService {

    private final IssueMapper issueMapper;
    private final ProjectMapper projectMapper; // 권한 확인을 위해 기존 매퍼 주입
    private final ProjectMemberMapper projectMemberMapper;
    private final AlarmService alarmService;

    /**
     * 이슈 생성 비즈니스 로직
     * @Transactional: 이슈 저장과 담당자 할당 중 하나라도 실패하면 전체 롤백됩니다.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int createIssue(int projectId, String userId, IssueCreateRequestDTO dto) {

        // 1. 권한 확인: 프로젝트 멤버인지 확인
        String role = projectMapper.getProjectRole(projectId, userId);

        if (role == null) {
            throw new RuntimeException("프로젝트 멤버만 이슈를 생성할 수 있습니다.");
        }

        // 2. 담당자 유효성 검증 (프로젝트 멤버이면서, ACTIVE 상태이면서, deleted_at이 null인 경우만 허용)
        List<String> assigneeIds = dto.getAssigneeIds();
        if (assigneeIds != null && !assigneeIds.isEmpty()) {

            // DB에서 해당 프로젝트의 ACTIVE 멤버 중 assigneeIds에 포함된 인원 수를 카운트
            int activeMemberCount = projectMapper.countActiveProjectMembers(projectId, assigneeIds);

            // 요청한 담당자 수와 DB에서 조회된 유효 멤버 수가 다르면 예외 발생
            if (activeMemberCount != assigneeIds.size()) {
                throw new RuntimeException("일부 담당자가 유효하지 않거나, 프로젝트에 참여 중(ACTIVE)인 멤버가 아닙니다.");
            }
        }

        // 3. 우선순위 검증 (0~5)
        if (dto.getPriority() < 0 || dto.getPriority() > 5) {
            throw new RuntimeException("우선순위는 0에서 5 사이여야 합니다.");
        }

        // 4. 담당자 존재 여부에 따라 초기 상태 설정
        // 담당자가 없으면 UNASSIGNED, 한 명이라도 있으면 IN_PROGRESS
        boolean hasAssignees = dto.getAssigneeIds() != null && !dto.getAssigneeIds().isEmpty();
        String status = hasAssignees ? "IN_PROGRESS" : "UNASSIGNED";

        // 5. IssueVO 빌드 및 저장
        IssueVO issue = IssueVO.builder()
                .projectId(projectId)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(status)
                .priority(dto.getPriority())
                .dueDate(dto.getDueDate())
                .createdBy(userId)
                .linkedCommitSha(dto.getLinkedCommitSha())
                .linkedCommitMessage(dto.getLinkedCommitMessage())
                .linkedCommitUrl(dto.getLinkedCommitUrl())
                .build();

        issueMapper.insertIssue(issue);
        int issueId = issue.getIssueId();

        // 6. 담당자 할당 (있는 경우에만)
        if (hasAssignees) {
            List<IssueAssigneeVO> assignees = dto.getAssigneeIds().stream()
                    .map(assigneeId -> IssueAssigneeVO.builder()
                            .issueId(issueId)
                            .projectId(projectId)
                            .userId(assigneeId)
                            .build())
                    .collect(Collectors.toList());

            // Batch Insert 호출 (담당자 자격은 DB FK 제약조건에서 최종 검증됨)
            issueMapper.insertIssueAssignees(assignees);

            // 담당자들에게 알림 전송 로직
            for (String assigneeId : dto.getAssigneeIds()) {
                // "userId(생성자)"가 "assigneeId(담당자)"에게 알림을 보냄
                alarmService.sendIssueAssignAlarm(
                        userId,             // 보낸 사람 (이슈 생성자)
                        assigneeId,         // 받는 사람 (담당자)
                        projectId,
                        issueId,
                        issue.getTitle()
                );
            }
        }

        return issueId;
    }

    /**
     * 이슈 삭제(아카이브) 비즈니스 로직
     * @Transactional: 이슈 저장과 담당자 할당 중 하나라도 실패하면 전체 롤백됩니다.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveIssue(int projectId, int issueId, String userId) {

        // 1. 이슈 존재 여부 조회
        IssueVO issue = issueMapper.selectIssueById(issueId);
        if (issue == null) {
            throw new RuntimeException("해당 이슈를 찾을 수 없습니다.");
        }

        // 2. 데이터 일치 확인: 해당 이슈가 요청된 프로젝트에 속해 있는지 검증
        if (issue.getProjectId() != projectId) {
            throw new RuntimeException("해당 프로젝트에 속한 이슈가 아닙니다.");
        }

        // 3. 중복 처리 확인: 이미 아카이브된 이슈인지 검증
        if (issue.getArchivedAt() != null) {
            throw new RuntimeException("이미 아카이브 처리된 이슈입니다.");
        }

        // 4. 권한 검증: 프로젝트 OWNER이거나 이슈 생성자(created_by)인지 확인
        String role = projectMapper.getProjectRole(projectId, userId);
        boolean isOwner = "OWNER".equals(role);
        boolean isCreator = userId.equals(issue.getCreatedBy());

        if (!isOwner && !isCreator) {
            throw new RuntimeException("프로젝트 소유자나 이슈 생성자만 아카이브할 수 있습니다.");
        }

        // 5. 아카이브 실행
        int result = issueMapper.updateIssueArchived(issueId);
        if (result == 0) {
            throw new RuntimeException("아카이브 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 이슈 담당자 추가 로직
     * @Transactional: 이슈 저장과 담당자 할당 중 하나라도 실패하면 전체 롤백됩니다.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addAssignee(int projectId, int issueId, String requesterId, String targetUserId) {

        // 1. 이슈 조회 및 유효성 확인
        IssueVO issue = issueMapper.selectIssueById(issueId);
        if (issue == null || issue.getArchivedAt() != null) {
            throw new RuntimeException("유효하지 않거나 아카이브된 이슈입니다.");
        }

        // 2. 완료(DONE)된 이슈에는 담당자를 추가할 수 없음
        if ("DONE".equals(issue.getStatus())) {
            throw new RuntimeException("완료된 이슈에는 담당자를 추가할 수 없습니다.");
        }

        // 3. 권한 검증: 요청자가 OWNER이거나 이슈 생성자인지 확인
        String role = projectMapper.getProjectRole(projectId, requesterId);
        boolean isOwner = "OWNER".equals(role);
        boolean isCreator = requesterId.equals(issue.getCreatedBy());

        if (!isOwner && !isCreator) {
            throw new RuntimeException("프로젝트 소유자나 이슈 생성자만 담당자를 추가할 수 있습니다.");
        }

        // 4. 대상 유저 유효성 확인: 프로젝트의 ACTIVE 멤버인지 확인
        int activeCount = projectMapper.countActiveProjectMembers(projectId, java.util.Collections.singletonList(targetUserId));
        if (activeCount == 0) {
            throw new RuntimeException("해당 유저는 프로젝트에 참여 중인(ACTIVE) 멤버가 아닙니다.");
        }

        // 5. 중복 할당 확인
        int alreadyAssigned = issueMapper.countAssignee(issueId, targetUserId);
        if (alreadyAssigned > 0) {
            throw new RuntimeException("이미 해당 이슈의 담당자로 등록된 유저입니다.");
        }

        // 6. 담당자 추가 실행
        issueMapper.insertSingleAssignee(IssueAssigneeVO.builder()
                .issueId(issueId)
                .projectId(projectId)
                .userId(targetUserId)
                .build());

        // 7. 이슈 상태 자동 전환: UNASSIGNED인 경우 IN_PROGRESS로 변경
        // 담당자가 추가되었으므로, 현재 상태가 UNASSIGNED라면 IN_PROGRESS로 변경
        IssueVO updatedIssue = issueMapper.selectIssueById(issueId);

        if ("UNASSIGNED".equals(updatedIssue.getStatus())) {
            IssueVO updateVo = IssueVO.builder()
                    .issueId(issueId)
                    .status("IN_PROGRESS")
                    .build();
            issueMapper.updateIssuePartial(updateVo);
        }

        // requesterId(보낸 사람) -> targetUserId(받는 사람)
        alarmService.sendIssueAssignAlarm(
                requesterId,
                targetUserId,
                projectId,
                issueId,
                updatedIssue.getTitle() // 이슈 제목
        );

    }

    /**
     * 이슈 담당자 제거 로직
     * @Transactional: 이슈 저장과 담당자 할당 중 하나라도 실패하면 전체 롤백됩니다.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeAssignee(int projectId, int issueId, String requesterId, String targetUserId) {

        // 1. 이슈 조회 및 유효성 확인
        IssueVO issue = issueMapper.selectIssueById(issueId);
        if (issue == null || issue.getArchivedAt() != null) {
            throw new RuntimeException("유효하지 않거나 아카이브된 이슈입니다.");
        }

        // 2. 프로젝트 일치 여부 확인
        if (issue.getProjectId() != projectId) {
            throw new RuntimeException("해당 프로젝트에 속한 이슈가 아닙니다.");
        }

        // 3. 도메인 규칙: 완료(DONE)된 이슈는 담당자를 변경할 수 없음
        if ("DONE".equals(issue.getStatus())) {
            throw new RuntimeException("완료된 이슈는 담당자를 수정할 수 없습니다.");
        }

        // 4. 권한 검증: 프로젝트 OWNER이거나 이슈 생성자인지 확인 (본인 해제 기능 제외)
        String role = projectMapper.getProjectRole(projectId, requesterId);
        boolean isOwner = "OWNER".equals(role);
        boolean isCreator = requesterId.equals(issue.getCreatedBy());

        if (!isOwner && !isCreator) {
            throw new RuntimeException("프로젝트 소유자나 이슈 생성자만 담당자를 제거할 수 있습니다.");
        }

        // 5. 할당 여부 확인
        int isAssigned = issueMapper.countAssignee(issueId, targetUserId);
        if (isAssigned == 0) {
            throw new RuntimeException("해당 유저는 이 이슈의 담당자가 아닙니다.");
        }

        // 6. 담당자 제거 실행
        issueMapper.deleteAssignee(issueId, targetUserId);

        // 7. 상태 회귀 로직: 남은 담당자가 0명인 경우 UNASSIGNED로 변경
        int count = issueMapper.countAssigneesByIssueId(issueId);
        IssueVO updatedIssue = issueMapper.selectIssueById(issueId);

        // 진행 중(IN_PROGRESS) 상태였는데 담당자가 다 사라지면 미배정으로 복귀
        if (count == 0 && "IN_PROGRESS".equals(updatedIssue.getStatus())) {
            IssueVO updateVo = IssueVO.builder()
                    .issueId(issueId)
                    .status("UNASSIGNED")
                    .build();
            issueMapper.updateIssuePartial(updateVo);
        }
    }

    /**
     * 이슈 내용 수정 로직
     * @Transactional: 이슈 저장과 담당자 할당 중 하나라도 실패하면 전체 롤백됩니다.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateIssue(int projectId, int issueId, String userId, IssueUpdateRequestDTO dto) {

        // 1. 이슈 존재 및 아카이브 여부 확인
        IssueVO issue = issueMapper.selectIssueById(issueId);
        if (issue == null || issue.getArchivedAt() != null) {
            throw new RuntimeException("수정할 수 없는 이슈이거나 존재하지 않습니다.");
        }

        // 2. 프로젝트 소속 검증
        if (issue.getProjectId() != projectId) {
            throw new RuntimeException("해당 프로젝트의 이슈가 아닙니다.");
        }

        // 3. 권한 검증: OWNER 또는 Creator만 가능 (담당자 제외)
        String role = projectMapper.getProjectRole(projectId, userId);
        boolean isOwner = "OWNER".equals(role);
        boolean isAdmin = "ADMIN".equals(role);
        boolean isCreator = userId.equals(issue.getCreatedBy());

        if (!isOwner && !isAdmin && !isCreator) {
            throw new RuntimeException("프로젝트 관리자(OWNER/ADMIN)나 이슈 생성자만 수정할 수 있습니다.");
        }

        // 4. 우선순위 범위 검증 (0~5)
        if (dto.getPriority() != null && (dto.getPriority() < 0 || dto.getPriority() > 5)) {
            throw new RuntimeException("우선순위는 0에서 5 사이여야 합니다.");
        }

        // 5. 상태 변경 시 담당자 유무 규칙 검증
        // 현재 담당자 수 확인
        int assigneeCount = issueMapper.countAssigneesByIssueId(issueId);

        // 최종적으로 반영될 상태 결정 (DTO에 값이 없으면 기존 상태 유지)
        String targetStatus = (dto.getStatus() != null) ? dto.getStatus() : issue.getStatus();

        // 규칙 1: 담당자가 없으면 IN_PROGRESS일 수 없다 -> UNASSIGNED로 강제 변경
        // (단, DONE 상태는 담당자가 없어도 유지될 수 있다고 가정. 만약 DONE도 막으려면 조건 추가 필요)
        if (assigneeCount == 0 && "IN_PROGRESS".equals(targetStatus)) {
            targetStatus = "UNASSIGNED";
        }

        // 규칙 2: 담당자가 있으면 UNASSIGNED일 수 없다 -> IN_PROGRESS로 강제 변경
        // (완료된 이슈(DONE)가 아니라면, 담당자가 생기는 순간 진행 중으로 봅니다)
        if (assigneeCount > 0 && "UNASSIGNED".equals(targetStatus)) {
            targetStatus = "IN_PROGRESS";
        }

        // 6. DB 반영 (VO에 데이터 매핑 후 호출)
        IssueVO updateVo = IssueVO.builder()
                .issueId(issueId)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .priority(dto.getPriority())
                .dueDate(dto.getDueDate())
                .status(targetStatus) // 보정된 상태값 사용
                .linkedCommitSha(dto.getLinkedCommitSha())
                .linkedCommitMessage(dto.getLinkedCommitMessage())
                .linkedCommitUrl(dto.getLinkedCommitUrl())
                .build();

        issueMapper.updateIssuePartial(updateVo);
    }

    /**
     * 이슈 목록 조회 (필터링 및 정렬 파싱)
     */
    @Override
    public List<IssueListResponseDTO> getIssueList(int projectId, String userId, String status, Integer priority, String assigneeId,
                                                   String createdStart, String createdEnd, String dueStart, String dueEnd, String sort) {

        if (projectMapper.getProjectRole(projectId, userId) == null) {
            throw new RuntimeException("프로젝트 멤버 권한이 없습니다.");
        }

        String[] sortParts = sort.split("_");
        return issueMapper.selectIssueList(projectId, status, priority, assigneeId,
                createdStart, createdEnd, dueStart, dueEnd,
                sortParts[0], sortParts[1].toUpperCase());
    }


    /**
     * 이슈 내용 상세 조회
     */
    @Override
    public IssueDetailResponseDTO getIssueDetail(int projectId, int issueId, String userId) {
        // 1. 프로젝트 멤버 권한 확인
        if (projectMapper.getProjectRole(projectId, userId) == null) {
            throw new RuntimeException("프로젝트 멤버만 이슈를 조회할 수 있습니다.");
        }

        // 2. 이슈 기본 정보 조회
        IssueVO issue = issueMapper.selectIssueById(issueId);

        // 3. 존재 여부 및 프로젝트 일치 확인
        if (issue == null || issue.getProjectId() != projectId) {
            throw new RuntimeException("해당 이슈를 찾을 수 없습니다.");
        }

        // 4. 아카이브 정책 반영: 삭제된 이슈 명시
        if (issue.getArchivedAt() != null) {
            throw new RuntimeException("삭제된 이슈입니다.");
        }

        // 5. 담당자 상세 정보(ID, 이름) 조회
        List<IssueDetailResponseDTO.AssigneeInfoDTO> assignees =
                issueMapper.selectAssigneeDetailsByIssueId(issueId);

        // 6. 결과 조립
        return IssueDetailResponseDTO.builder()
                .issueId(issue.getIssueId())
                .title(issue.getTitle())
                .description(issue.getDescription())
                .status(issue.getStatus())
                .priority(issue.getPriority())
                .dueDate(issue.getDueDate())
                .createdBy(issue.getCreatedBy())
                .creatorName(issue.getCreatorName())
                .createdAt(issue.getCreatedAt())
                .finishedAt(issue.getFinishedAt())
                .assignees(assignees)
                .linkedCommitSha(issue.getLinkedCommitSha())
                .linkedCommitMessage(issue.getLinkedCommitMessage())
                .linkedCommitUrl(issue.getLinkedCommitUrl())
                .build();
    }

}
