package com.example.LlmSpring.report.dailyreport;

import com.example.LlmSpring.task.TaskVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DailyReportMapper {

    // 오늘 완료/수정된 업무 조회
    List<TaskVO> selectTodayTasks(@Param("projectId") int projectId, @Param("userId") String userId);

    //1. 리포트 진입 (중복 확인)
    DailyReportVO selectReportByDate(@Param("projectId") Long projectId, @Param("userId") String userId, @Param("date") String date);

    //2. 리포트 생성
    void insertReport(DailyReportVO vo);

    //3. 리포트 상세 조회
    DailyReportVO selectReportById(@Param("reportId") Long reportId);

    //4. 리포트 수정
    void updateReport(DailyReportVO vo);

    //5. 리포트 발행
    void updateReportPublishStatus(@Param("reportId") Long reportId, @Param("status") String status);

    //6. 일일 리포트 요약 목록 조회
    List<DailyReportVO> selectReportsByDate(@Param("projectId") Long projectId, @Param("date") String date);

    //7. 프로젝트 기여도 통계 조회
    Map<String, Object> selectProjectStats(@Param("projectId") Long projectId, @Param("period") String period);

    //8. 채팅 로그 저장
    void insertChatLog(DailyReportChatLogVO vo);

    //9. 채팅 로그 전체 조회
    List<DailyReportChatLogVO> selectChatLogs(@Param("reportId") Long reportId);

    //10. 채팅 로그 페이징 조회
    List<DailyReportChatLogVO> selectChatLogsPaging(@Param("reportId") Long reportId, @Param("offset") int offset, @Param("limit") int limit);

    //11. 작성자 이름 조회
    String selectUserName(@Param("userId") String userId);

    //12. 리포트 설정 조회
    Map<String, Object> selectReportSettings(@Param("projectId") Long projectId);

    //13. 리포트 설정 변경
    void updateReportSettings(@Param("projectId") Long projectId, @Param("settings") Map<String, Object> settings);

}