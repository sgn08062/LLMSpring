package com.example.LlmSpring.project.request;

import lombok.Data;

@Data
public class ProjectStatusRequestDTO {
    private String status; // "DONE" 또는 "ACTIVE"
}