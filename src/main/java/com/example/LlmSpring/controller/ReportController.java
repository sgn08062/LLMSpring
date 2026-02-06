package com.example.LlmSpring.controller;

import com.example.LlmSpring.report.AiChatService;
import com.example.LlmSpring.report.dailyreport.DailyReportService;
import com.example.LlmSpring.report.dailyreport.response.DailyReportResponseDTO;
import com.example.LlmSpring.report.finalreport.FinalReportService;
import com.example.LlmSpring.report.finalreport.FinalReportVO;
import com.example.LlmSpring.util.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}")
@CrossOrigin(origins = "*")
public class ReportController {

    private final DailyReportService dailyReportService;
    private final FinalReportService finalReportService;
    private final JWTService jwtService;
    private final AiChatService aiChatService;

    private String getUserId(String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return jwtService.verifyTokenAndUserId(token);
    }

    //1. 리포트 작성 페이지 진입
    @PostMapping("/today")
    public ResponseEntity<DailyReportResponseDTO> createOrGetTodayReport(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long projectId) {

        String userId = getUserId(authHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        return ResponseEntity.ok(dailyReportService.getOrCreateTodayReport(projectId, userId));
    }

    // 1-1. Git 분석 요청
    @PostMapping("/daily-reports/analyze")
    public ResponseEntity<Map<String, Object>> analyzeGitCommits(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long projectId,
            @RequestBody Map<String, String> requestBody) {

        String userId = getUserId(authHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String date = requestBody.get("date");

        Map<String, Object> response = dailyReportService.analyzeGitCommits(projectId, userId, date);

        return ResponseEntity.ok(response);
    }

    //2. 리포트 상세 조회
    @GetMapping("/{reportId}")
    public ResponseEntity<DailyReportResponseDTO> getReport(@PathVariable Long projectId, @PathVariable Long reportId) {
        return ResponseEntity.ok(dailyReportService.getReportDetail(reportId));
    }

    //3. 리포트 수정 (임시 저장)
    @PutMapping("/daily-reports/{reportId}")
    public ResponseEntity<String> updateReport(
            @PathVariable Long projectId,
            @PathVariable Long reportId,
            @RequestBody Map<String, Object> body) {

        String content = (String) body.get("content");
        String title = (String) body.get("title");
        String summary = (String) body.get("summary");
        Integer commitCount = body.get("commitCount") != null ? Integer.parseInt(body.get("commitCount").toString()) : 0;

        dailyReportService.updateReport(reportId, content, title, summary, commitCount);
        return ResponseEntity.ok("Updated successfully");
    }

    // 리포트 직접 생성
    @PostMapping("/daily-reports")
    public ResponseEntity<?> createDailyReport(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> body) {

        String userId = getUserId(authHeader);

        Object dateObj = body.get("reportDate") != null ? body.get("reportDate") : body.get("date");
        if (dateObj == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "날짜 정보가 없습니다."));
        }

        String dateStr = dateObj.toString(); // "2026-02-06"
        String content = body.get("content") != null ? body.get("content").toString() : "";
        String summary = (String) body.get("summary");
        Integer commitCount = body.get("commitCount") != null ? Integer.parseInt(body.get("commitCount").toString()) : 0;

        DailyReportResponseDTO res = dailyReportService.getOrCreateTodayReport(projectId, userId);

        dailyReportService.updateReport(res.getReportId(), content, dateStr + " 리포트", summary, commitCount);

        return ResponseEntity.ok(Map.of("reportId", res.getReportId()));
    }

    //4. 리포트 발행 (완료 처리)
    @PatchMapping("/{reportId}/publish")
    public ResponseEntity<String> publishReport(@PathVariable Long reportId) {
        dailyReportService.publishReport(reportId);
        return ResponseEntity.ok("Published successfully");
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
    public ResponseEntity<DailyReportResponseDTO> regenerateReport(@PathVariable Long reportId) {
        return ResponseEntity.ok(dailyReportService.regenerateReport(reportId));
    }

    //8. AI 채팅 기록 조회
    @GetMapping("/daily-reports/{reportId}/chat-logs")
    public ResponseEntity<List<Map<String, Object>>> getChatLogs(
            @PathVariable Long reportId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(dailyReportService.getChatLogs(reportId, page, size));
    }

    //9. AI 채팅 전송
    @PostMapping("/daily-reports/{reportId}/chat")
    public ResponseEntity<Map<String, Object>> sendChat(@PathVariable Long reportId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(dailyReportService.sendChatToAI(reportId, body.get("message"), body.get("current_content")));
    }

    //10. AI 제안 적용 로그 저장
    @PostMapping("/daily-reports/{reportId}/apply")
    public ResponseEntity<String> applySuggestion(@PathVariable Long reportId, @RequestBody Map<String, Object> body) {
        dailyReportService.saveSuggestionLog(reportId, (String) body.get("suggestion_content"), (Boolean) body.get("is_applied"));
        return ResponseEntity.ok("Applied log saved");
    }

    //11. 리포트 설정 조회
    @GetMapping("/report-settings")
    public ResponseEntity<Map<String, Object>> getReportSettings(@PathVariable Long projectId) {
        return ResponseEntity.ok(dailyReportService.getReportSettings(projectId));
    }

    //12. 리포트 설정 변경
    @PutMapping("/report-settings")
    public ResponseEntity<String> updateReportSettings(@PathVariable Long projectId, @RequestBody Map<String, Object> body) {
        dailyReportService.updateReportSettings(projectId, body);
        return ResponseEntity.ok("Settings updated");
    }

    // 13. 최종 리포트 생성
    @PostMapping("/final-reports")
    public ResponseEntity<Map<String, String>> createFinalReport(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> body) {

        // 1. 토큰에서 User ID 추출
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // 2. 리포트 타입 추출
        String reportType = (String) body.get("reportType");

        // 3. 섹션 리스트 안전하게 추출
        List<String> selectedSections = new ArrayList<>();

        if (body.get("selectedSections") instanceof List<?>) {
            for (Object obj : (List<?>) body.get("selectedSections")) {
                selectedSections.add(obj.toString());
            }
        }

        // 4. Service 호출
        String content = finalReportService.getOrCreateFinalReport(projectId, reportType, selectedSections, userId);

        Map<String, String> response = new HashMap<>();
        response.put("content", content);
        return ResponseEntity.ok(response);
    }

    // 14. 최종 리포트 메타데이터 조회
    @GetMapping("/final-reports")
    public ResponseEntity<List<FinalReportVO>> getMyFinalReports(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long projectId) {

        // 1. 토큰에서 User ID 추출
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // 2. 서비스 호출 (List 반환)
        List<FinalReportVO> reports = finalReportService.getMyFinalReports(projectId, userId);

        return ResponseEntity.ok(reports);
    }

    // 15. 최종 리포트 수정
    @PutMapping("/final-reports/{finalReportId}")
    public ResponseEntity<String> updateFinalReport(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long finalReportId,
            @RequestBody Map<String, Object> body
    ){
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // 데이터 추출
        Object titleObj = body.get("title");
        String title = titleObj != null ? titleObj.toString() : "";
        Object contentObj = body.get("content");
        String content = contentObj != null ? contentObj.toString() : "";

        finalReportService.updateFinalReport(finalReportId, userId, title, content);

        return ResponseEntity.ok("리포트가 성공적으로 저장되었습니다.");
    }

    // 16. 다른 이름으로 저장
    @PostMapping("/final-reports/save-as")
    public ResponseEntity<Map<String, Object>> saveFinalReportAs(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> body
    ){
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Object titleObj = body.get("title");
        String title = titleObj != null ? titleObj.toString() : "제목 없음";

        Object contentObj = body.get("content");
        String content = contentObj != null ? contentObj.toString() : "";

        try {
            // 서비스 호출
            Map<String, Object> response = finalReportService.createFinalReportManual(projectId, userId, title, content);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            // [중요] 7개 제한에 걸렸을 때 400 Bad Request와 메시지 반환
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "저장 중 오류가 발생했습니다."));
        }
    }

    // 17. 최종 리포트 삭제
    @DeleteMapping("/final-reports/{finalReportId}")
    public ResponseEntity<String> deleteFinalReport(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long finalReportId) {

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        finalReportService.deleteFinalReport(finalReportId, userId);

        return ResponseEntity.ok("리포트가 삭제되었습니다.");
    }

    // 리포트 AI 채팅
    @PostMapping("/reports/chat")
    public ResponseEntity<Map<String, Object>> sendUnifiedChat(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> body
    ){
        String message = (String) body.get("message");
        String context =  (String) body.get("context");
        Boolean isSelection = (Boolean) body.get("isSelection");
        String reportType = (String) body.get("reportType");

        Map<String, Object> response = aiChatService.generateChatResponse(
                reportType,
                message,
                context,
                isSelection != null ? isSelection : false
        );

        System.out.println(response.toString());

        return ResponseEntity.ok(response);
    }
}