package com.example.LlmSpring.report.finalreport;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FinalReportVO {
    private Integer finalReportId; // final_report_id (PK)
    private Long projectId;        // project_id (FK)

    private String title;          // title
    private String content;        // content (AI Generated or User Modified)
    private String status;         // status ('DRAFT', 'MODIFYING', 'APPROVED')

    private String createdBy;      // created_by (User ID)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
