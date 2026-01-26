package com.example.LlmSpring.dailyreport;

import com.example.LlmSpring.dailyreport.response.DailyReportResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DailyReportService {

    private final DailyReportMapper dailyReportMapper;

    //1. 리포트 진입 (있으면 조회, 없으면 생성)
    @Transactional
    public DailyReportResponseDTO getOrCreateTodayReport(Long projectId, String userId){
        String today = LocalDate.now().toString();

        //1-1. 오늘 날짜로 이미 만든 리포트가 있는지 확인
        DailyReportVO existingReport = dailyReportMapper.selectReportByDate(projectId, userId, today);

        if (existingReport != null){
           return convertToDTO(existingReport);
        }

        //1-2. 없으면 새로 생성
        DailyReportVO newReport = new DailyReportVO();
        newReport.setProjectId(projectId);
        newReport.setUserId(userId);
        newReport.setReportDate(LocalDate.now());
        newReport.setTitle(LocalDate.now() + " 리포트");
        newReport.setContent("금일 진행한 업무 내용을 작성해주세요.");
        newReport.setCommitCount(0); //GitHub API 연동 시 실제 값 넣어야함

        dailyReportMapper.insertReport(newReport);

        return convertToDTO(newReport);
    }

    //2. 리포트 상세 조회
    public DailyReportResponseDTO getReportDetail(Long reportId) {
        DailyReportVO vo= dailyReportMapper.selectReportById(reportId);
        if(vo == null) throw new IllegalArgumentException("Report not found");

        DailyReportResponseDTO dto = convertToDTO(vo);

        //채팅 로그
        List<DailyReportChatLogVO> chatLogs = dailyReportMapper.selectChatLog(reportId);
        dto.setChatLogs(chatLogs);

        return dto;
    }

    //3. 리포트 임시 저장
    public void updateReport(Long reportId, String content, String title) {
        DailyReportVO vo = new DailyReportVO();
        vo.setReportId(reportId);
        vo.setTitle(title);
        vo.setContent(content);
        dailyReportMapper.updateReport(vo);
    }

    //4. 리포트 발행
    public void publishReport(Long reportId) {
        dailyReportMapper.updateReportPublishStatus(reportId, "PUBLISHED");
    }

    //5. 일일 리포트 요약 목록 조회
    public List<DailyReportResponseDTO> getDailyReportsByDate(Long projectId, String date) {
        List<DailyReportVO> reports = dailyReportMapper.selectReportsByDate(projectId, date);
        return reports.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    //6. 프로젝트 기여도 통계 조회
    public Map<String, Object> getProjectStats(Long projectId, String period) {
        return dailyReportMapper.selectProjectStats(projectId, period);
    }

    //7. 리포트 수동 재생성
    public DailyReportResponseDTO regenerateReport(Long reportId) {
        // TODO: Git 분석 로직 호출 및 Content 갱신
        return getReportDetail(reportId);
    }

    //8. AI 채팅 기록 조회
    public List<Map<String, Object>> getChatLogs(Long reportId, int page, int size) {
        // VO를 Map으로 변환하거나 별도 DTO 사용
        List<DailyReportChatLogVO> logs = dailyReportMapper.selectChatLogsPaging(reportId, page * size, size);
        List<Map<String, Object>> result = new ArrayList<>();
        for (DailyReportChatLogVO log : logs) {
            Map<String, Object> map = new HashMap<>();
            map.put("role", log.getRole());
            map.put("message", log.getMessage());
            result.add(map);
        }
        return result;
    }

    //9. AI 채팅 전송
    public Map<String, Object> sendChatToAI(Long reportId, String message, String currentContent) {
        // TODO: AI API 호출 로직
        DailyReportChatLogVO log = new DailyReportChatLogVO();
        log.setReportId(reportId);
        log.setRole(true); // User
        log.setMessage(message);
        dailyReportMapper.insertChatLog(log);

        // Mock Response
        Map<String, Object> response = new HashMap<>();
        response.put("reply", "AI 응답입니다.");
        return response;
    }

    //10. AI 제안 적용 로그 저장
    public void saveSuggestionLog(Long reportId, String suggestion, boolean isApplied) {
        DailyReportChatLogVO log = new DailyReportChatLogVO();
        log.setReportId(reportId);
        log.setRole(false); // AI
        log.setSuggestionContent(suggestion);
        log.setIsApplied(isApplied);
        dailyReportMapper.insertChatLog(log);
    }

    //11. 리포트 설정 조회
    public Map<String, Object> getReportSettings(Long projectId) {
        return dailyReportMapper.selectReportSettings(projectId);
    }

    //12. 리포트 설정 변경
    public void updateReportSettings(Long projectId, Map<String, Object> settings) {
        dailyReportMapper.updateReportSettings(projectId, settings);
    }

    //VO -> DTO 변환
    private DailyReportResponseDTO convertToDTO(DailyReportVO vo){
        String userName = dailyReportMapper.selectUserName(vo.getUserId());
        return new DailyReportResponseDTO(vo, userName);
    }

}
