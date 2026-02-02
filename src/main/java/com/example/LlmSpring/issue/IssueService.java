package com.example.LlmSpring.issue;

import com.example.LlmSpring.issue.request.IssueCreateRequestDTO;
import com.example.LlmSpring.issue.response.IssueDetailResponseDTO;
import com.example.LlmSpring.issue.response.IssueListResponseDTO;
import com.example.LlmSpring.issue.request.IssueUpdateRequestDTO;
import java.util.List;

public interface IssueService {

    // 이슈 생성
    int createIssue(int projectId, String userId, IssueCreateRequestDTO dto);

    // 이슈 아카이브(삭제)
    void archiveIssue(int projectId, int issueId, String userId);

    // 이슈 담당자 추가
    void addAssignee(int projectId, int issueId, String requesterId, String targetUserId);

    // 이슈 담당자 제거
    void removeAssignee(int projectId, int issueId, String requesterId, String targetUserId);

    // 이슈 상세 정보 수정
    void updateIssue(int projectId, int issueId, String userId, IssueUpdateRequestDTO dto);

    // 이슈 목록 조회
    List<IssueListResponseDTO> getIssueList(int projectId, String userId, String status, Integer priority, String assigneeId,
                                            String createdStart, String createdEnd, String dueStart, String dueEnd, String sort);

    // 이슈 상세 내용 조회
    IssueDetailResponseDTO getIssueDetail(int projectId, int issueId, String userId);



}
