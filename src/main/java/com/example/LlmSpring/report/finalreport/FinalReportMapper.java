package com.example.LlmSpring.report.finalreport;

import com.example.LlmSpring.report.dailyreport.DailyReportVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FinalReportMapper {
    List<DailyReportVO> selectAllReportsByProjectId(Long projectId);
    List<FinalReportVO> selectAllFinalReportsByProjectAndUser(Long projectId, String userId);
    FinalReportVO selectFinalReportByProjectId(Long finalReportId);
    int countFinalReportByProjectIdAndUserId(Long projectId, String userId);
    void insertFinalReport(FinalReportVO finalReportVO);
    void updateFinalReport(FinalReportVO vo);
    void deleteFinalReport(Long finalReportId);
}
