package com.example.LlmSpring.report.dailyreport;

import com.example.LlmSpring.alarm.AlarmService;
import com.example.LlmSpring.report.dailyreport.response.DailyReportResponseDTO;
import com.example.LlmSpring.project.ProjectMapper;
import com.example.LlmSpring.project.ProjectVO;
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
    private final UserMapper userMapper;         // 유저 정보(GitHub Token) 조회용
    private final ProjectMapper projectMapper;   // 프로젝트 정보(Repo URL) 조회용
    private final EncryptionUtil encryptionUtil; // 토큰 복호화용
    private final S3Service s3Service;
    private final AlarmService alarmService;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10); // GitHub 상세 조회용

    //1. 리포트 진입 (있으면 조회, 없으면 생성)
    @Transactional
    public DailyReportResponseDTO getOrCreateTodayReport(Long projectId, String userId){
        // [디버깅용] 유저 존재 여부 확인
        UserVO userCheck = userMapper.getUserInfo(userId);
        if (userCheck == null) {
            throw new RuntimeException("DB에서 유저를 찾을 수 없습니다. ID: " + userId);
        }

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

        // === [AI 및 GitHub 연동 로직 시작] ===
        String aiContent = "금일 진행한 업무 내용을 작성해주세요."; // 기본값
        int commitCount = 0;

        try {
            // 1. 사용자 및 프로젝트 정보 조회
            UserVO user = userMapper.getUserInfo(userId);
            ProjectVO project = projectMapper.selectProjectById(projectId);

            if (user != null && project != null &&
                    user.getGithubToken() != null && project.getGithubRepoUrl() != null) {

                // 2. 토큰 복호화
                String decryptedToken = encryptionUtil.decrypt(user.getGithubToken());

                // 3. 토큰을 이용해 실제 Github Username 가져오기
                String realGithubUsername = fetchGithubUsername(decryptedToken);
                log.info("GitHub API로 확인된 유저명: {}", realGithubUsername);

                // 4. GitHub 커밋 가져오기 (최근 24시간)
                if (realGithubUsername != null) {
                    System.out.println("접속할 깃허브 링크: " + project.getGithubRepoUrl());
                    // 5. 실제 Username으로 커밋 필터링
                    List<Map<String, Object>> commits = fetchAllMyRecentCommits(
                            project.getGithubRepoUrl(),
                            realGithubUsername, // DB값이 아닌 실제 GitHub Username 전달
                            decryptedToken
                    );

                    commitCount = commits.size();
                    log.info("필터링된 커밋 개수: {}", commitCount);

                    if (!commits.isEmpty()) {
                        aiContent = generateAiSummary(commits);
                    } else {
                        aiContent = "### 🚫 금일 커밋 내역 없음\n- '" + realGithubUsername + "' 계정으로 조회된 최근 24시간 커밋이 없습니다.";
                    }
                } else {
                    aiContent = "GitHub 사용자 정보를 가져오는데 실패했습니다. (토큰 만료 가능성)";
                }
            }
        } catch (Exception e) {
            log.error("AI 리포트 생성 실패", e);
            aiContent = "AI 자동 생성에 실패했습니다. (사유: " + e.getMessage() + ")\n\n기본 템플릿: 금일 진행한 업무 내용을 작성해주세요.";
        }
        // === [AI 및 GitHub 연동 로직 끝] ===

        // === [S3 업로드 로직 추가] ===
        // 1. 파일 경로 생성: dailyReport/{projectId}/yyyyMMdd_{userId}.md
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String s3Key = String.format("dailyReport/%d/%s_%s.md", projectId, dateStr, userId);

        // 2. 텍스트를 S3에 업로드하고 URL 반환
        String s3Url = s3Service.uploadTextContent(s3Key, aiContent);

        // 3. DB에는 URL 저장 (기획 의도 반영)
        newReport.setContent(s3Url); // content 컬럼에 URL 저장
        newReport.setDrFilePath(s3Url); // drFilePath 컬럼에도 동일하게 저장 (권장)
        newReport.setCommitCount(commitCount);
        newReport.setOriginalContent(true);

        dailyReportMapper.insertReport(newReport);

        return convertToDTO(newReport);
    }

    // 스케줄러 전용 비동기 메서드
    @Async
    public void createSystemReportAsync(Long projectId, String userId) {
        try {
            log.info(">>> [Async Start] Generating report for User: {} in Project: {}", userId, projectId);

            // 1. 리포트 생성
            getOrCreateTodayReport(projectId, userId);

            // 2. 알림 발송
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
                return (String) body.get("login"); // "sgn08062" 반환
            }
        } catch (Exception e) {
            log.error("GitHub 사용자 조회 실패", e);
        }
        return null;
    }

    // 1.2 Github API 호출 밑 최근 24시간 내의 커밋을 가져옴
    private List<Map<String, Object>> fetchAllMyRecentCommits(String repoUrl, String githubId, String token) {
        String[] parts = repoUrl.replace(".git", "").split("/");
        if (parts.length < 2) return Collections.emptyList();

        String owner = parts[parts.length - 2];
        String repo = parts[parts.length - 1];

        // 날짜 기준 설정 (기존 동일)
        ZonedDateTime nowKST = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        ZonedDateTime sinceKST = nowKST.minusHours(24);
        String since = sinceKST.withZoneSameInstant(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT);

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
            log.error("브랜치 목록 조회 실패", e);
            branches.add("main");
        }

        // 2. 브랜치별 커밋 조회 및 "브랜치 정보 매핑"
        Map<String, Map<String, Object>> uniqueCommitsMap = new HashMap<>();
        // SHA를 Key로 하고, 해당 SHA가 속한 브랜치 이름들을 Set으로 저장
        Map<String, Set<String>> shaToBranches = new HashMap<>();

        for (String branch : branches) {
            try {
                String commitsUrl = String.format(
                        "https://api.github.com/repos/%s/%s/commits?per_page=10&sha=%s&author=%s&since=%s",
                        owner, repo, branch, githubId, since
                );

                ResponseEntity<List> response = restTemplate.exchange(commitsUrl, HttpMethod.GET, entity, List.class);
                List<Map<String, Object>> branchCommits = response.getBody();

                if (branchCommits != null) {
                    for (Map<String, Object> commit : branchCommits) {
                        String sha = (String) commit.get("sha");
                        uniqueCommitsMap.putIfAbsent(sha, commit); // API 호출용 유니크 맵

                        // 해당 SHA가 발견된 브랜치 이름을 Set에 추가
                        shaToBranches.computeIfAbsent(sha, k -> new HashSet<>()).add(branch);
                    }
                }
            } catch (Exception e) {
                log.warn("브랜치 커밋 조회 실패 (" + branch + "): " + e.getMessage());
            }
        }

        if (uniqueCommitsMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 상세 정보 조회 및 브랜치 정보 주입
        List<CompletableFuture<Map<String, Object>>> futures = uniqueCommitsMap.values().stream()
                .map(commitItem -> CompletableFuture.supplyAsync(() -> {
                    String sha = (String) commitItem.get("sha");
                    String detailUrl = String.format("https://api.github.com/repos/%s/%s/commits/%s", owner, repo, sha);
                    try {
                        Map<String, Object> detail = (Map<String, Object>) restTemplate.exchange(detailUrl, HttpMethod.GET, entity, Map.class).getBody();

                        // 상세 정보 Map에 'branches' 리스트 추가
                        if (detail != null) {
                            detail.put("related_branches", new ArrayList<>(shaToBranches.getOrDefault(sha, Collections.emptySet())));
                        }
                        return detail;
                    } catch (Exception e) {
                        return null;
                    }
                }, executorService).thenApply(this::filterForAI))
                .collect(Collectors.toList());

        // 시간순 정렬
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

    // 1.3 AI 분석을 위해 필요한 정보만 필터
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
    private String generateAiSummary(List<Map<String, Object>> commitData) {
        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-latest:generateContent?key=" + geminiApiKey;

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonCommitData;
        try{
            jsonCommitData = objectMapper.writeValueAsString(commitData);
        }catch (Exception e){
            jsonCommitData = commitData.toString();
        }

        // [프롬프트 수정됨]
        String prompt = """
            ## Role
            당신은 소프트웨어 개발 프로젝트의 변경 사항을 문서화하는 전문 테크니컬 라이터입니다.
            제공된 커밋 데이터(JSON)를 분석하여 팀 공유용 기술 리포트를 작성하십시오.
            JSON 데이터에는 각 커밋이 속한 브랜치 정보("branches")가 포함되어 있습니다.

            ## Constraints
            1. **Tone**: 본문은 건조하고 전문적인 문체를 사용하십시오. (해요체 금지, 하십시오체 또는 명사형 종결 사용)
            2. **Format**: Notion과 호환되는 Markdown 형식을 엄수하십시오.
            3. **Grouping**: **반드시 '브랜치(Branch)'를 기준으로 커밋 내용을 그룹화하여 작성하십시오.**
            4. **Fact-based**: 제공된 데이터에 없는 내용을 추론하거나 꾸며내지 마십시오.

            ## Output Structure
            리포트는 반드시 아래의 구조를 따라야 합니다.

            ### 1. 📅 커밋 타임라인
            - 전체 커밋을 시간순으로 나열한 요약 그래프입니다.
            - 포맷: `YYYY-MM-DD HH:mm` | `[BranchName]` | `커밋 메시지`

            ### 2. 🌿 브랜치별 상세 작업 내역
            작업된 브랜치 별로 섹션을 나누어 상세 내용을 기술하십시오.
            
            #### 📂 [브랜치 이름] (예: feature/login)
            **[Commit Hash 7자리] 커밋 메시지**
             - **변경 사항**: (코드의 핵심 변경 내용 요약)
             - **상세**: (추가/수정/삭제된 파일 및 로직 설명)

            ### 3. 📝 금일 작업 요약 (Executive Summary)
            - 전체 브랜치의 작업을 통합하여 비즈니스 관점에서 3~5문장으로 요약하십시오.
            - **반드시 "금일 작업 내용은..."으로 시작하십시오.**

            ## Input Data (JSON)
            """ + jsonCommitData;

        // Gemini 요청 바디 구성
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

    //2. 리포트 상세 조회
    public DailyReportResponseDTO getReportDetail(Long reportId) {
        DailyReportVO vo = dailyReportMapper.selectReportById(reportId);
        if (vo == null) throw new IllegalArgumentException("Report not found");

        DailyReportResponseDTO dto = convertToDTO(vo);
        List<DailyReportChatLogVO> chatLogs = dailyReportMapper.selectChatLogs(reportId);
        dto.setChatLogs(chatLogs);
        return dto;
    }

    //3. 리포트 임시 저장
    public void updateReport(Long reportId, String content, String title) {
        // 기존 리포트 정보를 가져와서 경로 재구성 필요
        DailyReportVO existingVO = dailyReportMapper.selectReportById(reportId);
        if(existingVO == null) throw new IllegalArgumentException("Report not found");

        // 1. 파일 경로 재구성 (기존 파일 덮어쓰기)
        String dateStr = existingVO.getReportDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String s3Key = String.format("dailyReport/%d/%s_%s.md",
                existingVO.getProjectId(), dateStr, existingVO.getUserId());

        // 2. 수정된 텍스트(content)를 S3에 다시 업로드 (덮어쓰기)
        String s3Url = s3Service.uploadTextContent(s3Key, content);

        // 3. DB 업데이트 (Content에는 URL 저장)
        DailyReportVO vo = new DailyReportVO();
        vo.setReportId(reportId);
        vo.setTitle(title);
        vo.setContent(s3Url); // URL 저장
        vo.setDrFilePath(s3Url);
        vo.setOriginalContent(false); // 수정됨

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
        List<DailyReportChatLogVO> logs = dailyReportMapper.selectChatLogsPaging(reportId, page * size, size);
        List<Map<String, Object>> result = new ArrayList<>();
        for (DailyReportChatLogVO log : logs) {
            Map<String, Object> map = new HashMap<>();
            map.put("role", log.getRole()); // true:User, false:AI
            map.put("message", log.getMessage());
            result.add(map);
        }
        return result;
    }

    //9. AI 채팅 전송
    @Transactional
    public Map<String, Object> sendChatToAI(Long reportId, String message, String currentContent) {
        //User 메시지 저장
        DailyReportChatLogVO userLog = new DailyReportChatLogVO();
        userLog.setReportId(reportId);
        userLog.setRole(true); // User
        userLog.setMessage(message);
        dailyReportMapper.insertChatLog(userLog);

        // TODO: 실제 AI API 호출
        String aiReplyText = "AI 응답입니다: " + message + "에 대한 피드백...";

        //AI 메시지 저장 (이 부분이 빠져 있었습니다!)
        DailyReportChatLogVO aiLog = new DailyReportChatLogVO();
        aiLog.setReportId(reportId);
        aiLog.setRole(false); // AI
        aiLog.setMessage(aiReplyText);
        aiLog.setIsApplied(false);
        dailyReportMapper.insertChatLog(aiLog);

        //응답 반환
        Map<String, Object> response = new HashMap<>();
        response.put("reply", aiReplyText);
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
