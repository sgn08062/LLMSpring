package com.example.LlmSpring.project.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 프로젝트 목록 조회 시 반환되는 요약 정보 객체
 * 보안 및 전송 효율을 위해 필요한 필드만 포함.
 */

@Data
@Builder
public class ProjectListResponseDTO {
    private Integer projectId;      // 프로젝트 ID
    private String name;           // 프로젝트 이름
    private String status;         // 현재 상태 (ACTIVE/DONE)
    private LocalDateTime startDate; // 시작일
    private LocalDateTime endDate;   // 마감일
    private LocalDateTime deletedAt; // 휴지통 목록 조회 시 삭제 예정일 확인용
}