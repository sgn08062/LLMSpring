package com.example.LlmSpring.project;

import com.example.LlmSpring.projectMember.ProjectMemberVO;
import com.example.LlmSpring.projectMember.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProjectAccessService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;

    // 1. 읽기 권한 (GET)
    // - 삭제된 프로젝트: OWNER만 가능
    // - 그 외: 멤버라면 누구나 가능
    public void validateReadAccess(Long projectId, String userId) {
        ProjectVO project = getProject(projectId);
        ProjectMemberVO member = getMember(projectId, userId);

        if (project.getDeletedAt() != null) {
            if (!"OWNER".equals(member.getRole())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "삭제된 프로젝트는 접근할 수 없습니다.");
            }
        }
    }

    // 2. 쓰기 권한 (POST, PUT, DELETE 등)
    // - 삭제됨 OR 완료됨(DONE): 절대 불가
    public void validateWriteAccess(Long projectId, String userId) {
        ProjectVO project = getProject(projectId);
        getMember(projectId, userId); // 멤버 여부 체크

        if (project.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "삭제된 프로젝트는 수정할 수 없습니다.");
        }
        if ("DONE".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "완료된 프로젝트는 수정할 수 없습니다.");
        }
    }

    // 3. 리포트 전용 권한
    // - 삭제됨: 불가 / 완료됨: 가능
    public void validateReportAccess(Long projectId, String userId) {
        ProjectVO project = getProject(projectId);
        getMember(projectId, userId);

        if (project.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "삭제된 프로젝트의 리포트는 이용할 수 없습니다.");
        }
    }

    // 4. 멤버 관리 권한 (초대/강퇴)
    // - OWNER만 가능
    // - 삭제됨 OR 완료됨: 불가
    public void validateMemberManageAccess(Long projectId, String userId) {
        ProjectVO project = getProject(projectId);
        ProjectMemberVO member = getMember(projectId, userId);

        if (!"OWNER".equals(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
        if (project.getDeletedAt() != null || "DONE".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "현재 상태에서는 멤버를 변경할 수 없습니다.");
        }
    }

    // 5. 파일 업로드 권한 (S3용)
    public void validateFileUpload(Long projectId) {
        ProjectVO project = getProject(projectId);
        if (project.getDeletedAt() != null || "DONE".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "파일 업로드가 제한된 상태입니다.");
        }
    }

    // 6. 파일 다운로드 권한 (S3용)
    public void validateFileDownload(Long projectId) {
        ProjectVO project = getProject(projectId);
        if (project.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "삭제된 프로젝트의 파일은 다운로드할 수 없습니다.");
        }
    }

    // --- 내부 헬퍼 ---
    private ProjectVO getProject(Long projectId) {
        ProjectVO p = projectMapper.selectProjectById(projectId);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트가 존재하지 않습니다.");
        return p;
    }

    private ProjectMemberVO getMember(Long projectId, String userId) {
        // ProjectMemberMapper가 int형 projectId를 쓴다면 변환
        ProjectMemberVO m = projectMemberMapper.selectMemberRaw(projectId.intValue(), userId);

        // 멤버 없음 / 초대 상태 / 삭제된 멤버 차단
        if (m == null || "INVITED".equals(m.getStatus()) || m.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "프로젝트 멤버가 아닙니다.");
        }
        return m;
    }
}