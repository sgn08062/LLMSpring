package com.example.LlmSpring.report.finalreport;

import com.example.LlmSpring.report.dailyreport.DailyReportVO;
import com.example.LlmSpring.util.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinalReportServiceImpl implements FinalReportService {

    private final FinalReportMapper finalReportMapper;
    private final S3Service s3Service;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    // 최대 리포트 개수 제한
    private static final int MAX_REPORT_COUNT = 7;

    @Override
    @Transactional
    public String getOrCreateFinalReport(Long projectId, String reportType, List<String> selectedSections, String userId) {
        checkReportLimit(projectId, userId);

        // 1. 데이터 수집 (일일 리포트 모음)
        String aggregatedContent = collectAllDailyReports(projectId);

        // 2. 동적 프롬프트 생성
        String prompt = buildDynamicPrompt(reportType, selectedSections, aggregatedContent);

        // 3. AI 호출
        String generatedContent = callGemini(prompt);

        //  S3 파일명 생성 전략 적용
        // 포맷: finalReport/FinalReport_P{projectId}_U{userId}_{yyyyMMddHHmmss}.md
        // 예: finalReport/FinalReport_P10_Uuser123_20260203153000.md
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String s3FileName = String.format("finalReport/FinalReport_P%d_U%s_%s.md", projectId, userId, timestamp);

        // 4. S3 업로드
        String s3Url = s3Service.uploadTextContent(s3FileName, generatedContent);

        // 5. DB 저장
        FinalReportVO vo = new FinalReportVO();
        vo.setProjectId(projectId);

        // [수정 3] 제목 자동 생성: "리포트타입 (날짜 시간)" 형태
        String displayDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String baseTitle = generateTitle(reportType);
        vo.setTitle(baseTitle + " (" + displayDate + ")");

        vo.setContent(s3Url);
        vo.setStatus("DRAFT");
        vo.setCreatedBy(userId);

        finalReportMapper.insertFinalReport(vo);

        return generatedContent;
    }

    @Override
    public List<FinalReportVO> getMyFinalReports(Long projectId, String userId) {
        // 1. DB에서 리스트 조회 (이때 content 필드에는 https://s3... URL이 들어있음)
        List<FinalReportVO> reports = finalReportMapper.selectAllFinalReportsByProjectAndUser(projectId, userId);

        // 2. [수정] S3 URL을 실제 텍스트 내용으로 변환 (병렬 처리로 속도 최적화)
        reports.parallelStream().forEach(report -> {
            String textContent = fetchContentFromS3(report.getContent());
            report.setContent(textContent); // URL을 실제 마크다운 내용으로 덮어씌움
        });

        return reports;
    }

    @Override
    @Transactional
    public void updateFinalReport(Long finalReportId, String userId, String title, String content) {
        // 1. 기존 리포트 조회
        FinalReportVO report = finalReportMapper.selectFinalReportByProjectId(finalReportId);
        if (report == null) {
            throw new IllegalArgumentException("존재하지 않는 리포트입니다.");
        }

        // 2. 권한 확인
        if (!report.getCreatedBy().equals(userId)) {
            throw new IllegalArgumentException("수정 권한이 없습니다.");
        }

        // [중요] 삭제를 위해 기존 URL 미리 저장
        String oldS3Url = report.getContent();

        // 3. S3에 내용 재업로드 (새로운 타임스탬프로 생성)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String s3FileName = String.format("finalReport/FinalReport_P%d_U%s_%s_upd.md",
                report.getProjectId(), userId, timestamp);

        String newS3Url = s3Service.uploadTextContent(s3FileName, content);

        // 4. DB 정보 업데이트
        report.setTitle(title);
        report.setContent(newS3Url);

        finalReportMapper.updateFinalReport(report);

        // [추가] 5. 기존 S3 파일 삭제 (DB 업데이트 성공 후 실행)
        // 만약 '버전 관리' 기능이 필요 없다면 지우는 것이 맞습니다.
        s3Service.deleteFile(oldS3Url);
    }

    @Override
    @Transactional
    public Map<String, Object> createFinalReportManual(Long projectId, String userId, String title, String content) {
        checkReportLimit(projectId, userId);

        // 1. S3 파일명 생성 (중복 방지 타임스탬프)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // 파일명에 '_manual'을 붙여서 AI 생성본과 구분
        String s3FileName = String.format("finalReport/FinalReport_P%d_U%s_%s_manual.md", projectId, userId, timestamp);

        // 2. S3 업로드
        String s3Url = s3Service.uploadTextContent(s3FileName, content);

        // 3. DB 저장 (새로운 객체 생성)
        FinalReportVO vo = new FinalReportVO();
        vo.setProjectId(projectId);
        vo.setTitle(title); // 사용자가 입력한 제목 그대로 저장
        vo.setContent(s3Url);
        vo.setStatus("DRAFT");
        vo.setCreatedBy(userId);

        // insert 시 useGeneratedKeys="true" 덕분에 vo.getFinalReportId()에 ID가 채워짐
        finalReportMapper.insertFinalReport(vo);

        // 4. 결과 반환 (프론트엔드 상태 업데이트용)
        Map<String, Object> result = new HashMap<>();
        result.put("finalReportId", vo.getFinalReportId());
        result.put("title", vo.getTitle());
        result.put("content", content);

        return result;
    }

    @Override
    @Transactional
    public void deleteFinalReport(Long finalReportId, String userId) {
        // 1. 리포트 조회
        FinalReportVO report = finalReportMapper.selectFinalReportByProjectId(finalReportId); // 혹은 selectFinalReportByProjectId 등 기존 조회 메서드 활용
        if (report == null) {
            throw new IllegalArgumentException("존재하지 않는 리포트입니다.");
        }

        // 2. 권한 확인
        if (!report.getCreatedBy().equals(userId)) {
            throw new IllegalArgumentException("삭제 권한이 없습니다.");
        }

        // 3. S3 파일 삭제
        s3Service.deleteFile(report.getContent());

        // 4. DB 데이터 삭제
        finalReportMapper.deleteFinalReport(finalReportId);
    }

    private void checkReportLimit(Long projectId, String userId) {
        int myReportsCount = finalReportMapper.countFinalReportByProjectIdAndUserId(projectId, userId);
        if (myReportsCount >= MAX_REPORT_COUNT) {
            throw new IllegalArgumentException("최종 리포트는 1인당 최대 " + MAX_REPORT_COUNT + "개까지만 생성할 수 있습니다.");
        }
    }

    private String fetchContentFromS3(String url) {
        if (url == null || !url.startsWith("http")) {
            return url;
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            // String.class 대신 byte[].class로 수신
            byte[] bytes = restTemplate.getForObject(url, byte[].class);

            if (bytes != null) {
                // 바이트 배열을 UTF-8 문자열로 변환
                return new String(bytes, StandardCharsets.UTF_8);
            }
            return "";
        } catch (Exception e) {
            log.error("S3 최종 리포트 다운로드 실패 (URL: {}): {}", url, e.getMessage());
            return "# 로드 실패\n\n리포트 내용을 불러오는 데 실패했습니다. 관리자에게 문의하세요.";
        }
    }

    private String collectAllDailyReports(Long projectId) {
        List<DailyReportVO> reports = finalReportMapper.selectAllReportsByProjectId(projectId);

        if (reports.isEmpty()) {
            return "작성된 일일 리포트가 없습니다.";
        }

        StringBuilder aggregatedContent = new StringBuilder();
        RestTemplate restTemplate = new RestTemplate();

        aggregatedContent.append(String.format("=== Project ID: %d Daily Reports ===\n\n", projectId));

        for (DailyReportVO report : reports) {
            String date = report.getReportDate().toString();
            String s3Url = report.getContent();

            aggregatedContent.append(String.format("## Date: %s\n", date));

            try {
                if (s3Url != null && s3Url.startsWith("http")) {
                    // String.class 대신 byte[].class 사용
                    byte[] bytes = restTemplate.getForObject(s3Url, byte[].class);
                    if (bytes != null) {
                        String textContent = new String(bytes, StandardCharsets.UTF_8);
                        aggregatedContent.append(textContent).append("\n\n");
                    }
                } else {
                    aggregatedContent.append(s3Url).append("\n\n");
                }
            } catch (Exception e) {
                log.error("일일 리포트 로드 실패 (ID: {}): {}", report.getReportId(), e.getMessage());
                aggregatedContent.append("(내용 로드 실패)\n\n");
            }
        }

        return aggregatedContent.toString();
    }

    private String buildDynamicPrompt(String reportType, List<String> sections, String data) {
        StringBuilder sb = new StringBuilder();

        // 1. 공통 역할 부여 (기존 createPromptByType의 Role 내용)
        sb.append("## Role\n")
                .append("당신은 IT 프로젝트의 결과물을 정리하는 전문 테크니컬 라이터이자 PM입니다.\n")
                .append("제공된 '일일 업무 리포트 모음'을 분석하여, 요청된 구조에 맞춰 최종 문서를 작성하십시오.\n\n");

        // 2. 리포트 유형별 톤앤매너 및 제약조건 설정 (변수 주입)
        if ("PORTFOLIO".equals(reportType)) {
            sb.append("## Output Style: [개발자 포트폴리오]\n")
                    .append("- Tone: 자신감 있고 성취를 강조하는 어조 ('나' 주어 사용, 해요체)\n")
                    .append("- Goal: 채용 담당자에게 개발 역량과 문제 해결 능력을 어필\n")
                    .append("- Constraint: 수치적인 성과(예: 성능 00% 개선)를 적극적으로 강조하십시오.\n\n");
        } else if ("TECHNICAL_DOC".equals(reportType)) {
            sb.append("## Output Style: [기술 명세서]\n")
                    .append("- Tone: 명확하고 객관적인 기술적 어조 (평서문)\n")
                    .append("- Goal: 개발자를 위한 시스템 구조 및 구현 상세 설명\n")
                    .append("- Constraint: 전문 용어를 적극 사용하고 로직을 명확히 서술하십시오.\n\n");
        } else { // PROJECT_REPORT (기본값)
            sb.append("## Output Style: [프로젝트 결과 보고서]\n")
                    .append("- Tone: 공식적이고 비즈니스적인 어조 (하십시오체)\n")
                    .append("- Goal: 관리자 및 이해관계자에게 프로젝트 진행 과정과 성과 보고\n")
                    .append("- Constraint: 문장은 간결하게 명사형이나 '하십시오'체로 끝맺으십시오.\n\n");
        }

        // 3. 선택된 섹션 구성 (유형에 따라 지시사항이 달라짐)
        sb.append("## Required Sections (작성해야 할 목차):\n");

        // 프론트엔드에서 섹션을 선택하지 않았을 경우를 대비한 기본값
        if (sections == null || sections.isEmpty()) {
            sections = List.of("개요", "기능 목록", "트러블슈팅", "결과/회고");
        }

        int sectionIndex = 1;
        for (String section : sections) {
            // 섹션 이름뿐만 아니라 'reportType'도 함께 넘겨서 맞춤형 지시사항을 가져옴
            sb.append(getSectionInstruction(section, reportType, sectionIndex++)).append("\n");
        }

        // 4. 데이터 주입
        sb.append("\n## Input Data (Daily Reports):\n").append(data);

        return sb.toString();
    }

    private String getSectionInstruction(String section, String reportType, int index) {
        String instruction = "";

        switch (section) {
            case "개요":
                if ("PORTFOLIO".equals(reportType)) {
                    instruction = "프로젝트 한 줄 소개 및 핵심 가치 요약 (매력적인 도입부 작성)";
                } else {
                    instruction = "프로젝트 진행 기간, 주요 목표, 전체적인 진행 흐름 요약";
                }
                break;
            case "역할/협업":
                if ("PORTFOLIO".equals(reportType)) {
                    instruction = "나의 기여도와 주도적인 역할 강조, 협업 시 발생한 갈등 해결 사례";
                } else {
                    instruction = "팀 내 역할 분담 및 협업 툴/방식 서술";
                }
                break;
            case "아키텍처":
                instruction = "사용된 기술 스택(Tech Stack)과 시스템 아키텍처 구조 설명";
                break;
            case "API명세":
                instruction = "주요 기능의 API 엔드포인트 목록 및 데이터 흐름 요약";
                break;
            case "트러블슈팅":
                if ("PORTFOLIO".equals(reportType)) {
                    instruction = "가장 기술적으로 도전적이었던 문제를 STAR 기법(Situation, Task, Action, Result)으로 서술";
                } else {
                    instruction = "주요 버그 및 이슈 사항과 해결 과정 (문제-원인-해결 구조)";
                }
                break;
            case "기능 목록":
                instruction = "구현된 핵심 기능 목록 나열 및 설명";
                break;
            case "결과/회고":
                if ("PORTFOLIO".equals(reportType)) {
                    instruction = "이 프로젝트를 통해 얻은 기술적 성장과 인사이트 (Growth & Insight)";
                } else {
                    instruction = "최종 성과와 아쉬웠던 점(KPT 회고), 향후 개선 방향";
                }
                break;
            case "개선점":
                instruction = "현재 버전의 한계점과 추후 고도화 계획";
                break;
            default:
                instruction = "해당 주제에 대해 리포트 내용을 바탕으로 상세히 서술";
                break;
        }

        return String.format("### %d. %s\n- %s", index, section, instruction);
    }

    private String callGemini(String prompt) {
        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);
        content.put("parts", Collections.singletonList(parts));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.3);
        requestBody.put("contents", Collections.singletonList(content));
        requestBody.put("generationConfig", generationConfig);

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(geminiUrl, HttpMethod.POST, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null) {
                List<Map> candidates = (List<Map>) body.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map contentMap = (Map) candidates.get(0).get("content");
                    List<Map> partsList = (List<Map>) contentMap.get("parts");
                    return (String) partsList.get(0).get("text");
                }
            }
            return "AI 응답 오류";
        } catch (Exception e) {
            log.error("Gemini 호출 실패: {}", e.getMessage());
            return "오류 발생: " + e.getMessage();
        }
    }

    private String generateTitle(String reportType) {
        if ("PROJECT_REPORT".equals(reportType)) return "프로젝트 결과 보고서";
        if ("PORTFOLIO".equals(reportType)) return "개발자 포트폴리오";
        if ("TECHNICAL_DOC".equals(reportType)) return "기술 명세서";
        return "최종 리포트";
    }
}