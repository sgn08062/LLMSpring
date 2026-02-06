package com.example.LlmSpring.report.dailyreport.response;

import com.example.LlmSpring.report.dailyreport.DailyReportChatLogVO;
import com.example.LlmSpring.report.dailyreport.DailyReportVO;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class DailyReportResponseDTO {
    private Long reportId;
    private String title;
    private String content;
    private String summary;
    private Boolean originalContent;
    private String status;
    private String reportDate;
    private Integer commitCount;
    private boolean isPublished;
    private String writerName;
    private String userId;
    private String role;
    private List<DailyReportChatLogVO> chatLogs;

    public DailyReportResponseDTO(DailyReportVO vo, String writerName) {
        this.reportId = vo.getReportId();
        this.title = vo.getTitle();
        this.content = vo.getContent();
        this.summary = vo.getSummary();
        this.originalContent = vo.getOriginalContent();
        this.status = vo.getStatus();
        this.reportDate = (vo != null && vo.getReportDate() != null) ? vo.getReportDate().toString() : LocalDate.now().toString();
        this.commitCount = vo.getCommitCount();
        this.isPublished = Boolean.TRUE.equals(vo.getIsPublished());
        this.writerName = writerName;

        // serId와 role 매핑
        this.userId = vo.getUserId();
        this.role = (vo.getRole() != null) ? vo.getRole() : "MEMBER";
    }
}