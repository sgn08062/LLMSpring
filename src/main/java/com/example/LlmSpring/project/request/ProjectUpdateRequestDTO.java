package com.example.LlmSpring.project.request;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class ProjectUpdateRequestDTO {
    private String name;
    private String description;
    private String gitUrl;
    private LocalDateTime endDate;   // 마감일만 유지
    private LocalTime reportTime;
}