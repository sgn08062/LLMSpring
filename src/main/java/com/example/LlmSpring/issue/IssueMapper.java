package com.example.LlmSpring.issue;

import com.example.LlmSpring.issue.response.IssueDetailResponseDTO;
import com.example.LlmSpring.issue.response.IssueListResponseDTO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IssueMapper {

    // 1-1. 이슈 메인 정보 삽입
    void insertIssue(IssueVO issue);

    // 1-2. 담당자 목록 일괄 삽입 (Batch Insert)
    void insertIssueAssignees(@Param("assignees") List<IssueAssigneeVO> assignees);

    // 2-1. 특정 이슈의 상세 정보 조회 (권한 및 프로젝트 일치 여부 확인용)
    IssueVO selectIssueById(@Param("issueId") int issueId);

    // 2-2. 이슈 아카이브 처리 (archived_at 컬럼 업데이트)
    int updateIssueArchived(@Param("issueId") int issueId);

    // 3-1. 특정 이슈에 특정 유저가 이미 담당자로 등록되어 있는지 확인
    int countAssignee(@Param("issueId") int issueId, @Param("userId") String userId);

    // 3-2. 이슈의 상태(status)를 강제로 변경 (UNASSIGNED -> IN_PROGRESS 등)
    int updateIssueStatus(@Param("issueId") int issueId, @Param("status") String status);

    // 3-3. 단일 담당자 추가
    void insertSingleAssignee(IssueAssigneeVO assignee);

    
    // 4-1. 특정 이슈의 모든 담당자 수를 조회
    int countAssigneesByIssueId(@Param("issueId") int issueId);

    // 4-2. 이슈 담당자 제거
    int deleteAssignee(@Param("issueId") int issueId, @Param("userId") String userId);

    // 5. 이슈 내용 부분 수정
    void updateIssuePartial(IssueVO updateVo);

    // 6. 목록 조회 (필터 및 정렬)
    List<IssueListResponseDTO> selectIssueList(
            @Param("projectId") int projectId,
            @Param("status") String status,
            @Param("priority") Integer priority,
            @Param("assigneeId") String assigneeId,
            @Param("sortColumn") String sortColumn,
            @Param("sortDirection") String sortDirection
    );


    // 7. 특정 이슈의 담당자 정보(ID, 이름) 목록 조회
    List<IssueDetailResponseDTO.AssigneeInfoDTO> selectAssigneeDetailsByIssueId(@Param("issueId") int issueId);
}