package com.example.LlmSpring.project.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ProjectListResponseDTO {
    private Integer projectId;
    private String name;
    private String status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime deletedAt; // 휴지통 목록 조회 시 사용
}