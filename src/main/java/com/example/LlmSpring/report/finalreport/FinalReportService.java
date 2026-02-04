package com.example.LlmSpring.report.finalreport;

import java.util.List;
import java.util.Map;

public interface FinalReportService {
    String getOrCreateFinalReport(Long projectId, String reportType, List<String> selectedSections, String userId);
    List<FinalReportVO> getMyFinalReports(Long projectId, String userId);
    void updateFinalReport(Long finalReportId, String userId, String title, String content);
    Map<String, Object> createFinalReportManual(Long projectId, String userId, String title, String content);
}
