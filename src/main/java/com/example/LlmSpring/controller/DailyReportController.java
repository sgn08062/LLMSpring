package com.example.LlmSpring.controller;

import com.example.LlmSpring.dailyreport.DailyReportService;
import com.example.LlmSpring.dailyreport.response.DailyReportResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}")
@CrossOrigin(origins = "*")
public class DailyReportController {

    private final DailyReportService dailyReportService;

    //1. 리포트 작성 페이지 진입
    @PostMapping("/today")
    public DailyReportResponseDTO createOrGetTodayReport(@PathVariable Long projectId) {
        String userId = "user1"; //실제 로그인 유저 ID 필요
        return dailyReportService.getOrCreateTodayReport(projectId, userId);
    }

    //2. 리포트 상세 조회
    @GetMapping("/{reportId}")
    public DailyReportResponseDTO getReport (@PathVariable Long projectId, @PathVariable Long reportId){
        return dailyReportService.getReportDetail(reportId);
    }

    //3. 리포트 수정 (임시 저장)
    @PutMapping("/{reportId}")
    public void updateReport(@PathVariable Long reportId, @RequestBody Map<String, String> body) {
        dailyReportService.updateReport(reportId, body.get("content"), body.get("title"));
    }

    //4. 리포트 발행 (완료 처리)
    @PatchMapping("/{reportId}/publish")
    public void publishReport(@PathVariable Long reportId) {
        dailyReportService.publishReport(reportId);
    }

    //5. 일일 리포트 요약 목록 조회
    @GetMapping("/daily-reports")
    public List<DailyReportResponseDTO> getDailyReports(@PathVariable Long projectId, @RequestParam("date") String date) {
        return dailyReportService.getDailyReportsByDate(projectId, date);
    }

    //6. 프로젝트 기여도 통계 조회
    @GetMapping("/stats")
    public Map<String, Object> getProjectStats(@PathVariable Long projectId, @RequestParam(value = "period", defaultValue = "weekly") String period) {
        return dailyReportService.getProjectStats(projectId, period);
    }

    //7. 리포트 수동 재생성
    @PostMapping("/daily-reports/{reportId}/regeneration")
    public DailyReportResponseDTO regenerateReport(@PathVariable Long reportId) {
        return dailyReportService.regenerateReport(reportId);
    }

    //8. AI 채팅 기록 조회
    @GetMapping("/daily-reports/{reportId}/chat-logs")
    public List<Map<String, Object>> getChatLogs(@PathVariable Long reportId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return dailyReportService.getChatLogs(reportId, page, size);
    }

    //9. AI 채팅 전송
    @PostMapping("/daily-reports/{reportId}/chat")
    public Map<String, Object> sendChat(@PathVariable Long reportId, @RequestBody Map<String, String> body) {
        return dailyReportService.sendChatToAI(reportId, body.get("message"), body.get("current_content"));
    }

    //10. AI 제안 적용 로그 저장
    @PostMapping("/daily-reports/{reportId}/apply")
    public void applySuggestion(@PathVariable Long reportId, @RequestBody Map<String, Object> body) {
        dailyReportService.saveSuggestionLog(reportId, (String) body.get("suggestion_content"), (Boolean) body.get("is_applied"));
    }

    //11. 리포트 설정 조회
    @GetMapping("/report-settings")
    public Map<String, Object> getReportSettings(@PathVariable Long projectId) {
        return dailyReportService.getReportSettings(projectId);
    }

    //12. 리포트 설정 변경
    @PutMapping("/report-settings")
    public void updateReportSettings(@PathVariable Long projectId, @RequestBody Map<String, Object> body) {
        dailyReportService.updateReportSettings(projectId, body);
    }

}
