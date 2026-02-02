package com.example.LlmSpring.report.finalreport;

import java.util.List;

public interface FinalReportService {
    String getOrCreateFinalReport(Long projectId, String reportType, List<String> selectedSections, String userId);
    FinalReportVO getFinalReportMetadata(Long projectId);
}
