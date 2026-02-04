package com.example.LlmSpring.report.dailyreport.response;

import com.example.LlmSpring.report.dailyreport.DailyReportChatLogVO;
import com.example.LlmSpring.report.dailyreport.DailyReportVO;
import lombok.Data;

import java.util.List;

@Data
public class DailyReportResponseDTO {
    private Long reportId;
    private String title;
    private String content;
    private String summary;
    private Boolean originalContent; //true: 초안, false: 수정됨
    private String status;
    private String reportDate;
    private Integer commitCount;
    private boolean isPublished;
    private String writerName;

    //AI 채팅 기록
    private List<DailyReportChatLogVO> chatLogs;

    //VO -> DTO 변환 생성자
    public DailyReportResponseDTO(DailyReportVO vo, String writerName) {
        this.reportId = vo.getReportId();
        this.title = vo.getTitle();
        this.content = vo.getContent();
        this.summary = vo.getSummary();
        this.originalContent = vo.getOriginalContent();
        this.status = vo.getStatus();
        this.reportDate = vo.getReportDate().toString();
        this.commitCount = vo.getCommitCount();
        this.isPublished = Boolean.TRUE.equals(vo.getIsPublished());
        this.writerName = writerName;
    }
}
