package com.example.LlmSpring.report.dailyreport;

import com.example.LlmSpring.alarm.AlarmService;
import com.example.LlmSpring.report.dailyreport.response.DailyReportResponseDTO;
import com.example.LlmSpring.project.ProjectMapper;
import com.example.LlmSpring.project.ProjectVO;
import com.example.LlmSpring.task.TaskVO;
import com.example.LlmSpring.user.UserMapper;
import com.example.LlmSpring.user.UserVO;
import com.example.LlmSpring.util.EncryptionUtil;
import com.example.LlmSpring.util.S3Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportService {

    private final DailyReportMapper dailyReportMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final EncryptionUtil encryptionUtil;
    private final S3Service s3Service;
    private final AlarmService alarmService;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    //1. 리포트 진입 (있으면 조회, 없으면 생성)
    @Transactional
    public DailyReportResponseDTO getOrCreateTodayReport(Long projectId, String userId){
        String today = LocalDate.now().toString();
        DailyReportVO existingReport = dailyReportMapper.selectReportByDate(projectId, userId, today);

        if (existingReport != null){
            return getReportDetail(existingReport.getReportId());
        }

        //1-2. 없으면 새로 생성
        DailyReportVO newReport = new DailyReportVO();
        newReport.setProjectId(projectId);
        newReport.setUserId(userId);
        newReport.setReportDate(LocalDate.now()); // [중요] null 방지
        newReport.setTitle(today + " 리포트");
        newReport.setContent(""); // 초기값
        newReport.setDrFilePath("");
        newReport.setCommitCount(0);
        newReport.setOriginalContent(true);
        newReport.setStatus("DRAFT");

        dailyReportMapper.insertReport(newReport);

        return convertToDTO(newReport);
    }

    public Map<String, Object> analyzeGitCommits(Long projectId, String userId, String date) {
        try {
            log.info(">>> [Analysis] Start for User: {}, Date: {}", userId, date);

            GeneratedContent result = getGeneratedContent(projectId, userId, date);

            log.info(">>> [Analysis] Finished. Commits: {}", result.commitCount);

            Map<String, Object> response = new HashMap<>();
            response.put("content", result.content);
            response.put("commitCount", result.commitCount);
            response.put("summary", "");

            return response;
        } catch (Exception e) {
            log.error("분석 실패", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("content", "# 분석 실패\n\n오류가 발생했습니다: " + e.getMessage());
            errorResponse.put("commitCount", 0);
            return errorResponse;
        }
    }

    public DailyReportResponseDTO getReportDetail(Long reportId) {
        DailyReportVO vo = dailyReportMapper.selectReportById(reportId);
        if (vo == null) throw new IllegalArgumentException("Report not found");

        DailyReportResponseDTO dto = convertToDTO(vo);
        String textContent = fetchContentFromS3(vo.getContent());
        dto.setContent(textContent);
        return dto;
    }

    @Transactional
    public void updateReport(Long reportId, String content, String title, String summary, Integer commitCount) {
        DailyReportVO existingVO = dailyReportMapper.selectReportById(reportId);
        if(existingVO == null) throw new IllegalArgumentException("Report not found");

        String dateStr = existingVO.getReportDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String s3Key = String.format("dailyReport/%d/%s_%s.md",
                existingVO.getProjectId(), dateStr, existingVO.getUserId());

        String s3Url = s3Service.uploadTextContent(s3Key, content);

        existingVO.setTitle(title);
        existingVO.setContent(s3Url);
        existingVO.setDrFilePath(s3Url);
        existingVO.setOriginalContent(false);

        if (summary != null) {
            existingVO.setSummary(summary);
        }
        if (commitCount != null) {
            existingVO.setCommitCount(commitCount);
        }

        dailyReportMapper.updateReport(existingVO);
    }

    @Transactional
    public void createReportManual(Long projectId, String userId, String dateStr, String content) {
        LocalDate reportDate = LocalDate.parse(dateStr);
        String formattedDate = reportDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String s3Key = String.format("dailyReport/%d/%s_%s.md", projectId, formattedDate, userId);

        String s3Url = s3Service.uploadTextContent(s3Key, content);

        DailyReportVO newReport = new DailyReportVO();
        newReport.setProjectId(projectId);
        newReport.setUserId(userId);
        newReport.setReportDate(reportDate);
        newReport.setTitle(reportDate + " 리포트");
        newReport.setContent(s3Url);
        newReport.setDrFilePath(s3Url);
        newReport.setStatus("DRAFT");
        newReport.setCommitCount(0);
        newReport.setOriginalContent(false);

        dailyReportMapper.insertReport(newReport);
    }

    public void publishReport(Long reportId) {
        dailyReportMapper.updateReportPublishStatus(reportId, "PUBLISHED");
    }

    public List<DailyReportResponseDTO> getDailyReportsByDate(Long projectId, String date) {
        List<DailyReportVO> reports = dailyReportMapper.selectReportsByDate(projectId, date);
        return reports.stream().map(vo -> {
            DailyReportResponseDTO dto = convertToDTO(vo);
            return dto;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> getProjectStats(Long projectId, String period) {
        return dailyReportMapper.selectProjectStats(projectId, period);
    }

    @Transactional
    public DailyReportResponseDTO regenerateReport(Long reportId) {
        DailyReportVO existingVO = dailyReportMapper.selectReportById(reportId);
        if (existingVO == null) throw new IllegalArgumentException("Report not found");

        // 리포트의 날짜를 문자열로 변환하여 전달
        String dateStr = existingVO.getReportDate().toString();
        GeneratedContent generated = getGeneratedContent(existingVO.getProjectId(), existingVO.getUserId(), dateStr);

        String s3Url = s3Service.uploadTextContent(existingVO.getDrFilePath(), generated.content);

        existingVO.setCommitCount(generated.commitCount);
        existingVO.setContent(s3Url);
        existingVO.setOriginalContent(true);

        dailyReportMapper.updateReport(existingVO);

        return getReportDetail(reportId);
    }

    @Transactional
    public Map<String, Object> sendChatToAI(Long reportId, String message, String currentContent) {
        String prompt = String.format("""
            당신은 개발자의 일일 리포트 작성을 돕는 AI 조수입니다.
            [현재 리포트 내용]
            %s
            [사용자 요청]
            %s
            요청에 맞춰 답변해주세요.
            """, currentContent, message);

        String aiReplyText = callGeminiApi(prompt);

        Map<String, Object> response = new HashMap<>();
        response.put("reply", aiReplyText);
        return response;
    }

    public Map<String, Object> getReportSettings(Long projectId) {
        return dailyReportMapper.selectReportSettings(projectId);
    }

    public void updateReportSettings(Long projectId, Map<String, Object> settings) {
        dailyReportMapper.updateReportSettings(projectId, settings);
    }

    private DailyReportResponseDTO convertToDTO(DailyReportVO vo){
        String userName = dailyReportMapper.selectUserName(vo.getUserId());
        return new DailyReportResponseDTO(vo, userName);
    }

    private String fetchContentFromS3(String url) {
        if (url == null || !url.startsWith("http")) return url;
        try {
            RestTemplate restTemplate = new RestTemplate();
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
            if (bytes != null) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            return "";
        } catch (Exception e) {
            log.error("S3 리포트 다운로드 실패: {}", url);
            return "# 로드 실패\n내용을 불러올 수 없습니다.";
        }
    }

    // --- [ GitHub & GEMINI Methods ] ---

    private static class GeneratedContent {
        String content;
        int commitCount;
        public GeneratedContent(String content, int commitCount) {
            this.content = content;
            this.commitCount = commitCount;
        }
    }

    // Git 커밋 + DB Task를 모두 가져와서 분석
    private GeneratedContent getGeneratedContent(Long projectId, String userId, String targetDate) {
        String aiContent = "금일 진행한 업무 내용을 작성해주세요.";
        int commitCount = 0;

        try {
            List<TaskVO> todayTasks = dailyReportMapper.selectTodayTasks(projectId.intValue(), userId);

            UserVO user = userMapper.getUserInfo(userId);
            ProjectVO project = projectMapper.selectProjectById(projectId);
            List<Map<String, Object>> commits = new ArrayList<>();

            if (user != null && project != null && user.getGithubToken() != null && project.getGithubRepoUrl() != null) {
                String decryptedToken = encryptionUtil.decrypt(user.getGithubToken());
                String realGithubUsername = fetchGithubUsername(decryptedToken);

                if (realGithubUsername != null) {
                    // targetDate 전달
                    commits = fetchAllMyRecentCommits(
                            project.getGithubRepoUrl(), realGithubUsername, decryptedToken, targetDate
                    );
                    commitCount = commits.size();
                }
            }

            if (!commits.isEmpty() || !todayTasks.isEmpty()) {
                aiContent = generateAiSummary(commits, todayTasks);
            } else {
                aiContent = "### 🚫 금일 활동 내역 없음\n- 완료된 업무(Task)나 GitHub 커밋 내역이 없습니다.";
            }

        } catch (Exception e) {
            log.error("AI 리포트 생성 실패", e);
            aiContent = "AI 자동 생성에 실패했습니다. (오류: " + e.getMessage() + ")";
        }
        return new GeneratedContent(aiContent, commitCount);
    }

    // 스케줄러 전용 비동기 메서드
    @Async
    public void createSystemReportAsync(Long projectId, String userId) {
        try {
            log.info(">>> [Async Start] Generating report for User: {} in Project: {}", userId, projectId);

            DailyReportResponseDTO reportDTO = getOrCreateTodayReport(projectId, userId);

            String todayStr = LocalDate.now().toString();
            GeneratedContent generated = getGeneratedContent(projectId, userId, todayStr);

            String summary = "";
            if (generated.content != null) {
                summary = generated.content.lines()
                        .limit(3)
                        .collect(Collectors.joining("\n"));
            }

            updateReport(
                    reportDTO.getReportId(),
                    generated.content,
                    reportDTO.getTitle(),
                    summary,
                    generated.commitCount
            );

            // 알림 발송
            // 프로젝트 이름 조회
            ProjectVO project = projectMapper.selectProjectById(projectId);
            String projectName = (project != null) ? project.getName() : "프로젝트";

            String alarmContent = "[" + projectName + "] 의 일일 리포트가 생성되었습니다.";
            String targetUrl = "/project/" + projectId + "/dashboard";

            // 알림 전송
            alarmService.createAlarm(userId, alarmContent, "DAILY_REPORT", targetUrl);

            log.info(">>> [Async End] Report generated and Alarm sent for User: {}", userId);

        } catch (Exception e) {
            log.error("Failed to generate async report for user: " + userId, e);
        }
    }

    // --- [ GitHub & GEMINI Methods ] ---
    // 1.1 Github 토큰을 이용해서 현재 사용자의 프로필 정보 가져옴
    private String fetchGithubUsername(String token) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.github.com/user",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("login")) {
                return (String) body.get("login");
            }
        } catch (Exception e) {
            log.error("GitHub 사용자 조회 실패", e);
        }
        return null;
    }

    private List<Map<String, Object>> fetchAllMyRecentCommits(String repoUrl, String githubId, String token, String targetDate) {
        String[] parts = repoUrl.replace(".git", "").split("/");
        if (parts.length < 2) return Collections.emptyList();

        String owner = parts[parts.length - 2];
        String repo = parts[parts.length - 1];

        // 24시간 조회
        String since;
        try {
            LocalDate date = LocalDate.parse(targetDate);
            ZonedDateTime startOfDayKST = date.atStartOfDay(ZoneId.of("Asia/Seoul"));
            since = startOfDayKST.withZoneSameInstant(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT);
        } catch (Exception e) {
            // 날짜 파싱 에러 시 그냥 24시간 전으로 fallback
            since = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(24)
                    .withZoneSameInstant(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT);
        }

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 1. 모든 브랜치 가져오기 (기존 동일)
        List<String> branches = new ArrayList<>();
        try {
            String branchesUrl = String.format("https://api.github.com/repos/%s/%s/branches", owner, repo);
            ResponseEntity<List> response = restTemplate.exchange(branchesUrl, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> branchList = response.getBody();
            if (branchList != null) {
                for (Map<String, Object> b : branchList) {
                    branches.add((String) b.get("name"));
                }
            }
        } catch (Exception e) {
            branches.add("main");
        }

        Map<String, Map<String, Object>> uniqueCommitsMap = new HashMap<>();
        Map<String, Set<String>> shaToBranches = new HashMap<>();

        for (String branch : branches) {
            try {
                String commitsUrl = String.format(
                        "https://api.github.com/repos/%s/%s/commits?per_page=100&sha=%s&since=%s",
                        owner, repo, branch, since
                );

                ResponseEntity<List> response = restTemplate.exchange(commitsUrl, HttpMethod.GET, entity, List.class);
                List<Map<String, Object>> branchCommits = response.getBody();

                if (branchCommits != null) {
                    for (Map<String, Object> commit : branchCommits) {
                        String sha = (String) commit.get("sha");
                        uniqueCommitsMap.putIfAbsent(sha, commit);
                        shaToBranches.computeIfAbsent(sha, k -> new HashSet<>()).add(branch);
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }

        if (uniqueCommitsMap.isEmpty()) return Collections.emptyList();

        List<CompletableFuture<Map<String, Object>>> futures = uniqueCommitsMap.values().stream()
                .map(commitItem -> CompletableFuture.supplyAsync(() -> {
                    String sha = (String) commitItem.get("sha");
                    String detailUrl = String.format("https://api.github.com/repos/%s/%s/commits/%s", owner, repo, sha);
                    try {
                        Map<String, Object> detail = (Map<String, Object>) restTemplate.exchange(detailUrl, HttpMethod.GET, entity, Map.class).getBody();
                        if (detail != null) {
                            detail.put("related_branches", new ArrayList<>(shaToBranches.getOrDefault(sha, Collections.emptySet())));
                        }
                        return detail;
                    } catch (Exception e) {
                        return null;
                    }
                }, executorService).thenApply(this::filterForAI))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .sorted((c1, c2) -> {
                    String d1 = (String) c1.get("date");
                    String d2 = (String) c2.get("date");
                    return d1.compareTo(d2);
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> filterForAI(Map<String, Object> original) {
        if (original == null) return null;
        Map<String, Object> filtered = new HashMap<>();
        Map<String, Object> commitInfo = (Map<String, Object>) original.get("commit");
        Map<String, Object> authorInfo = (Map<String, Object>) commitInfo.get("author");

        filtered.put("date", authorInfo.get("date"));
        filtered.put("message", commitInfo.get("message"));
        filtered.put("branches", original.get("related_branches"));

        List<Map<String, Object>> files = (List<Map<String, Object>>) original.get("files");
        List<Map<String, String>> fileChanges = new ArrayList<>();

        if (files != null) {
            for (Map<String, Object> file : files) {
                Map<String, String> fileData = new HashMap<>();
                fileData.put("filename", (String) file.get("filename"));
                fileData.put("status", (String) file.get("status"));
                String patch = (String) file.get("patch");
                fileData.put("patch", patch != null ? patch : "(Binary or Large file)");
                fileChanges.add(fileData);
            }
        }
        filtered.put("changes", fileChanges);
        return filtered;
    }

    // 1.4 GEMINI API 호출
    private String generateAiSummary(List<Map<String, Object>> commitData, List<TaskVO> taskData) {
        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-latest:generateContent?key=" + geminiApiKey;
        ObjectMapper objectMapper = new ObjectMapper();

        String jsonInput = "";
        try{
            Map<String, Object> combinedData = new HashMap<>();
            combinedData.put("commits", commitData); // Git 커밋
            combinedData.put("tasks", taskData);     // DB Task
            jsonInput = objectMapper.writeValueAsString(combinedData);
        }catch (Exception e){
            jsonInput = "Error parsing data";
        }

        String prompt = """
            ## Role
            당신은 소프트웨어 개발 프로젝트의 변경 사항을 문서화하고 팀의 진척도를 관리하는 전문 테크니컬 라이터 겸 스크럼 마스터입니다.
            제공된 'Git 커밋 데이터(commits)'와 '완료된 업무 데이터(tasks)'를 분석하여 팀 공유용 일일 업무 리포트를 작성하십시오.
        
            ## Constraints
            1. **Tone**: 건조하고 전문적인 문체를 사용하십시오. (해요체 금지, '하십시오체' 또는 명사형 종결 사용)
            2. **Format**: Markdown 형식을 엄수하십시오.
            3. **No File Paths**: 'src/main/...'와 같은 구체적인 파일 경로는 절대 나열하지 마십시오.
            4. **Feature-oriented**: 단순 코드 수정을 넘어 '어떤 비즈니스 기능(Feature)을 구현/개선했는지'를 중심으로 자연스러운 문장으로 서술하십시오.
            5. **Grouping**: 반드시 '브랜치(Branch)'를 기준으로 커밋 내용을 그룹화하여 작성하십시오.
            6. **Fact-based**: 제공된 데이터에 없는 내용을 추론하거나 꾸며내지 마십시오.
        
            ## Output Structure (Markdown)
        
            # 📅 금일 업무 요약 (Executive Summary)
            - **반드시 "금일 작업 내용은..."으로 시작하십시오.**
            - 전체 작업을 비즈니스 관점에서 3줄 이내로 핵심 요약하십시오.
        
            ---
        
            ### 1. 🕒 커밋 타임라인
            - 전체 커밋을 시간순으로 나열한 요약 그래프입니다.
            - 포맷: `YYYY-MM-DD HH:mm` | `[BranchName]` | `커밋 메시지`
        
            ---
        
            ### 2. 🚀 브랜치별 상세 구현 사항
            작업된 브랜치 별로 섹션을 나누어 상세 내용을 기술하십시오.
        
            #### 📂 [브랜치 이름 또는 주요 기능명]
            - **[Commit Hash 7자리] 커밋 메시지**
              - **기능 구현**: 커밋 및 업무 내용을 기반으로 구현된 핵심 비즈니스 로직 한글 요약
              - **상세 내용**: 수정/추가된 로직의 목적과 변경점 설명 (파일 경로 제외)
        
            ---
        
            ### 3. ✅ 금일 업무 현황 (Task Status)
            - 제공된 `tasks` 데이터를 바탕으로 아래와 같이 리스트를 출력하십시오.
            - 데이터가 없으면 '해당 없음'으로 표시하십시오.
            - 포맷: - **[업무 상태]** 업무 제목 (예: - **[DONE]** 로그인 API 예외 처리 로직 강화)
        
            ## Input Data (JSON)
        """ + jsonInput;

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);
        content.put("parts", Collections.singletonList(parts));
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.2);
        requestBody.put("contents", Collections.singletonList(content));
        requestBody.put("generationConfig", generationConfig);

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(geminiUrl, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("candidates")) return "AI 응답 오류";
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
            if (candidates.isEmpty()) return "AI 분석 결과가 없습니다.";
            Map<String, Object> resContent = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> resParts = (List<Map<String, Object>>) resContent.get("parts");
            return (String) resParts.get(0).get("text");
        } catch (Exception e) {
            log.error("Gemini API Error", e);
            throw new RuntimeException("AI 분석 중 오류 발생");
        }
    }

    private String callGeminiApi(String prompt) {
        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);
        content.put("parts", Collections.singletonList(parts));
        requestBody.put("contents", Collections.singletonList(content));

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(geminiUrl, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> resContent = (Map<String, Object>) candidates.get(0).get("content");
                    List<Map<String, Object>> resParts = (List<Map<String, Object>>) resContent.get("parts");
                    return (String) resParts.get(0).get("text");
                }
            }
        } catch (Exception e) {
            log.error("Gemini API Error", e);
        }
        return "AI 응답 오류가 발생했습니다.";
    }
}