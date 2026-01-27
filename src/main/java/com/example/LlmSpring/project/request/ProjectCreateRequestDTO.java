package com.example.LlmSpring.project.request;

import java.time.LocalDateTime;
import lombok.Data;
import java.time.LocalTime;
import java.util.List;

/**
 * 신규 프로젝트 생성을 위한 요청 데이터 객체
 */

@Data
public class ProjectCreateRequestDTO {
    private String name;            // 프로젝트 이름
    private String description;     // 프로젝트 설명
    private String gitUrl;          // GitHub 저장소 주소
    private LocalTime reportTime;   // 보고 시간 설정
    private LocalDateTime endDate;  // 마감일
    private List<String> members;   // 초대할 user_id 리스트 (프로젝트 멤버는 여러 명일 수도 있어서러 List 처리)
}