package com.example.LlmSpring.dailyreport;

import com.example.LlmSpring.dailyreport.response.DailyReportResponseDTO;
import com.example.LlmSpring.project.ProjectMapper;
import com.example.LlmSpring.project.ProjectVO;
import com.example.LlmSpring.user.UserMapper;
import com.example.LlmSpring.user.UserVO;
import com.example.LlmSpring.util.EncryptionUtil;
import com.example.LlmSpring.util.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
        // 1. 현재 한국 시간 가져오기
        ZonedDateTime nowKST = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        // 2. 24시간 전으로 설정
        ZonedDateTime sinceKST = nowKST.minusHours(24);
        // 3. GitHub API 표준인 UTC로 변환 (포맷 예: 2026-01-27T03:44:00Z)
        String since = sinceKST.withZoneSameInstant(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT);

        log.info("GitHub 검색 기준 시간(since): {}", since);

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 1. 모든 브랜치 가져오기
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
            log.error("브랜치 목록 조회 실패: " + e.getMessage());
            // 브랜치 조회 실패 시 기본 main이라도 시도하도록 리스트에 추가
            branches.add("main");
        }

        // 2. 브랜치별 커밋 조회 (병렬 처리 권장되나, 간단히 순차 처리 후 상세 조회만 병렬로 함)
        // 중복 제거를 위해 Map<SHA, CommitData> 사용
        Map<String, Map<String, Object>> uniqueCommitsMap = new HashMap<>();

        for (String branch : branches) {
            try {
                // 해당 브랜치에서, 내가 작성한, 24시간 이내 커밋
                String commitsUrl = String.format(
                        "https://api.github.com/repos/%s/%s/commits?per_page=10&sha=%s&author=%s&since=%s",
                        owner, repo, branch, githubId, since
                );

                ResponseEntity<List> response = restTemplate.exchange(commitsUrl, HttpMethod.GET, entity, List.class);
                List<Map<String, Object>> branchCommits = response.getBody();

                if (branchCommits != null) {
                    for (Map<String, Object> commit : branchCommits) {
                        String sha = (String) commit.get("sha");
                        uniqueCommitsMap.putIfAbsent(sha, commit); // 이미 있으면 스킵 (중복 방지)
                    }
                }
            } catch (Exception e) {
                log.warn("브랜치 커밋 조회 실패 (" + branch + "): " + e.getMessage());
                // 특정 브랜치 조회 실패해도 다른 브랜치는 계속 진행
            }
        }

        if (uniqueCommitsMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 유니크한 커밋들의 상세 정보(Patch) 병렬 조회
        List<CompletableFuture<Map<String, Object>>> futures = uniqueCommitsMap.values().stream()
                .map(commitItem -> CompletableFuture.supplyAsync(() -> {
                    String sha = (String) commitItem.get("sha");
                    String detailUrl = String.format("https://api.github.com/repos/%s/%s/commits/%s", owner, repo, sha);
                    try {
                        return (Map<String, Object>) restTemplate.exchange(detailUrl, HttpMethod.GET, entity, Map.class).getBody();
                    } catch (Exception e) {
                        return null;
                    }
                }, executorService).thenApply(this::filterForAI))
                .collect(Collectors.toList());

        // 시간순 정렬 (GitHub API는 최신순으로 주지만, 병렬 처리 후 뒤섞일 수 있으므로 재정렬)
        List<Map<String, Object>> result = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .sorted((c1, c2) -> {
                    // date 문자열 비교 (ISO 포맷이라 문자열 비교 가능)
                    String d1 = (String) c1.get("date");
                    String d2 = (String) c2.get("date");
                    return d1.compareTo(d2); // 과거 -> 최신 (리포트 작성 순서)
                })
                .collect(Collectors.toList());

        System.out.println(result);

        return result;
    }

    // 1.3 AI 분석을 위해 필요한 정보만 필터
    private Map<String, Object> filterForAI(Map<String, Object> original) {
        if (original == null) return null;

        Map<String, Object> filtered = new HashMap<>();
        Map<String, Object> commitInfo = (Map<String, Object>) original.get("commit");
        Map<String, Object> authorInfo = (Map<String, Object>) commitInfo.get("author");

        filtered.put("date", authorInfo.get("date"));
        filtered.put("message", commitInfo.get("message"));

        List<Map<String, Object>> files = (List<Map<String, Object>>) original.get("files");
        List<Map<String, String>> fileChanges = new ArrayList<>();

        if (files != null) {
            for (Map<String, Object> file : files) {
                Map<String, String> fileData = new HashMap<>();
                fileData.put("filename", (String) file.get("filename"));
                fileData.put("status", (String) file.get("status"));
                // Patch는 너무 길면 자르는 로직 추가 고려 가능
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
        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

        String prompt = """
            당신은 팩트에 기반해 문서를 정리하는 테크니컬 라이터입니다.
            제공된 커밋 로그(JSON)를 시간순으로 분석하여 개발 내역을 정리해주세요.

            [작성 규칙]
            1. **포맷**: Notion에 바로 붙여넣을 수 있는 깔끔한 Markdown 형식을 사용하세요.
            2. **어조**: 이모티콘을 절대 사용하지 말고, 간결하고 전문적인 문체로 작성하세요.
            3. **조건부 출력**: '추가된 내용', '수정된 내용', '삭제된 내용'으로 분류하되, **변경 사항이 없는 항목은 제목 자체를 아예 적지 말고 생략하세요.**
            4. **기반 데이터**: 오직 제공된 로그와 패치 내역에 있는 사실만 적으세요.
            
            [출력 양식 예시]
            ### 추가된 내용
            - (새로운 기능, 파일 추가 등)
            
            ### 수정된 내용
            - (기존 로직 변경 등)
            
            ---
            ### 📝 작업 요약
            - (전체 작업의 핵심 내용 3문장 이내)

            [커밋 데이터]
            """ + commitData.toString();

        // Gemini 요청 바디 구성
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
