package com.example.LlmSpring.github;

import com.example.LlmSpring.project.ProjectMapper;
import com.example.LlmSpring.project.ProjectVO;
import com.example.LlmSpring.projectMember.ProjectMemberMapper;
import com.example.LlmSpring.user.UserMapper;
import com.example.LlmSpring.user.UserVO;
import com.example.LlmSpring.util.EncryptionUtil;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubService {
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;
    private final EncryptionUtil encryptionUtil;

    public List<GithubBranchResponseDTO> getProjectBranches(Long projectId, String userId, int page){
        // 1. 프로젝트 참여 멤버인지 확인
        if(!projectMemberMapper.existsActiveMember(projectId.intValue(), userId)){
            throw new IllegalArgumentException("해당 프로젝트의 참여 멤버가 아닙니다.");
        }

        // 2. 프로젝트 정보 조회
        ProjectVO project = projectMapper.selectProjectById(projectId);
        if(project == null || project.getGithubRepoUrl() == null){
            throw new IllegalArgumentException("프로젝트가 존재하지 않거나 GitHub 저장소가 연결되지 않았습니다.");
        }

        // 3. 사용자 정보 조회
        UserVO user = userMapper.getUserInfo(userId);
        if (user == null || user.getGithubToken() == null) {
            throw new IllegalArgumentException("GitHub 계정이 연동되지 않은 사용자입니다.");
        }

        // 4. 토큰 복호화
        String decryptedToken = encryptionUtil.decrypt(user.getGithubToken());

        // 5. Repo URL 파싱 (Owner, Repo 이름 추출)
        String repoUrl = project.getGithubRepoUrl();
        String[] repoInfo = parseRepoUrl(project.getGithubRepoUrl());

        // 6. GitHub API 호출
        return fetchBranchesFromGithub(repoInfo[0], repoInfo[1], decryptedToken, page);
    }

    private List<GithubBranchResponseDTO> fetchBranchesFromGithub(String owner, String repo, String token, int page) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String branchesUrl = String.format("https://api.github.com/repos/%s/%s/branches?per_page=10&page=%d", owner, repo, page);
        List<GithubBranchResponseDTO> branchListResult = new ArrayList<>();

        try {
            ResponseEntity<List> response = restTemplate.exchange(branchesUrl, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> responseList = response.getBody();

            if (responseList != null) {
                for (Map<String, Object> branchData : responseList) {
                    // 1. 브랜치 이름 추출
                    String name = (String) branchData.get("name");

                    // 2. 최신 커밋 SHA 추출
                    Map<String, Object> commitData = (Map<String, Object>) branchData.get("commit");
                    String sha = (commitData != null) ? (String) commitData.get("sha") : null;

                    // 3. DTO 생성 및 추가
                    branchListResult.add(GithubBranchResponseDTO.builder()
                            .name(name)
                            .sha(sha)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("GitHub 브랜치 조회 실패: {}", e.getMessage());
            throw new RuntimeException("GitHub API 호출 실패: " + e.getMessage());
        }

        return branchListResult;
    }

    public List<Map<String, Object>> getCommitsBySha(Long projectId, String userId, String sha, int page){
        // 1. 권한 및 정보 검증 (공통 로직)
        if (!projectMemberMapper.existsActiveMember(projectId.intValue(), userId)) {
            throw new IllegalArgumentException("해당 프로젝트의 참여 멤버가 아닙니다.");
        }

        ProjectVO project = projectMapper.selectProjectById(projectId);
        if (project == null || project.getGithubRepoUrl() == null) {
            throw new IllegalArgumentException("프로젝트가 존재하지 않거나 GitHub 저장소가 연결되지 않았습니다.");
        }

        UserVO user = userMapper.getUserInfo(userId);
        if (user == null || user.getGithubToken() == null) {
            throw new IllegalArgumentException("GitHub 계정이 연동되지 않은 사용자입니다.");
        }

        String decryptedToken = encryptionUtil.decrypt(user.getGithubToken());
        String[] repoInfo = parseRepoUrl(project.getGithubRepoUrl()); // [Owner, Repo]

        // 2. GitHub API 호출
        return fetchCommitsFromGithub(repoInfo[0], repoInfo[1], sha, decryptedToken, page);
    }

    private List<Map<String, Object>> fetchCommitsFromGithub(String owner, String repo, String sha, String token, int page) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // sha 파라미터로 특정 지점부터의 커밋을 가져옴 (최근 30개)
        String commitsUrl = String.format("https://api.github.com/repos/%s/%s/commits?sha=%s&per_page=10&page=%d", owner, repo, sha, page);
        List<Map<String, Object>> resultList = new ArrayList<>();

        try {
            ResponseEntity<List> response = restTemplate.exchange(commitsUrl, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> responseList = response.getBody();

            if (responseList != null) {
                for (Map<String, Object> commitItem : responseList) {
                    Map<String, Object> commitInfo = (Map<String, Object>) commitItem.get("commit");
                    Map<String, Object> authorInfo = (Map<String, Object>) commitInfo.get("author");

                    Map<String, Object> simplifiedCommit = new HashMap<>();
                    simplifiedCommit.put("sha", commitItem.get("sha"));
                    simplifiedCommit.put("message", commitInfo.get("message"));
                    simplifiedCommit.put("authorName", authorInfo.get("name"));
                    simplifiedCommit.put("date", authorInfo.get("date"));
                    simplifiedCommit.put("htmlUrl", commitItem.get("html_url"));

                    resultList.add(simplifiedCommit);
                }
            }
        } catch (Exception e) {
            log.error("GitHub 커밋 조회 실패: {}", e.getMessage());
            throw new RuntimeException("GitHub 커밋 로그 조회 실패: " + e.getMessage());
        }
        return resultList;
    }




    private String[] parseRepoUrl(String repoUrl) {
        String[] parts = repoUrl.replace(".git", "").split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("잘못된 GitHub 저장소 URL입니다.");
        }
        return new String[]{parts[parts.length - 2], parts[parts.length - 1]};
    }

    // 깃허브 내 저장소 목록 가져오기
    public List<Map<String, Object>> getUserRepositories(String userId, int page) {
        // 1. 유저 정보 및 토큰 조회
        UserVO user = userMapper.getUserInfo(userId);
        if (user == null || user.getGithubToken() == null) {
            throw new IllegalArgumentException("GitHub 계정이 연동되지 않았습니다.");
        }

        // 2. 토큰 복호화
        String decryptedToken = encryptionUtil.decrypt(user.getGithubToken());

        // 3. GitHub API 호출 (내 저장소 목록, 최신순 100개)
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + decryptedToken);
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = String.format("https://api.github.com/user/repos?sort=updated&per_page=10&type=all&page=%d", page);
        List<Map<String, Object>> resultList = new ArrayList<>();

        try {
            // API 호출
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> responseList = response.getBody();

            if (responseList != null) {
                for (Map<String, Object> repoData : responseList) {
                    Map<String, Object> simpleRepo = new HashMap<>();
                    simpleRepo.put("name", repoData.get("name"));           // 리포지토리 이름
                    simpleRepo.put("full_name", repoData.get("full_name")); // 전체 이름
                    simpleRepo.put("html_url", repoData.get("html_url"));   // URL
                    simpleRepo.put("private", repoData.get("private"));     // 비공개 여부
                    simpleRepo.put("description", repoData.get("description")); // 설명

                    resultList.add(simpleRepo);
                }
            }
        } catch (Exception e) {
            log.error("GitHub 저장소 목록 조회 실패: {}", e.getMessage());
            throw new RuntimeException("GitHub API 호출 실패: " + e.getMessage());
        }

        return resultList;
    }

    // [1] 컨트롤러에서 호출할 메인 메서드 (새로 추가)
    public int getTodayCommitCount(Long projectId, String userId, String branchName) {
        // 1. 유저 및 프로젝트 검증 (기존 코드 유지)
        if (!projectMemberMapper.existsActiveMember(projectId.intValue(), userId)) return 0;
        ProjectVO project = projectMapper.selectProjectById(projectId);
        UserVO user = userMapper.getUserInfo(userId);
        if (project == null || user == null || user.getGithubToken() == null) return 0;

        String decryptedToken = encryptionUtil.decrypt(user.getGithubToken());
        String[] repoInfo = parseRepoUrl(project.getGithubRepoUrl());

        // 2. 브랜치 설정
        String targetBranch = (branchName == null || branchName.trim().isEmpty()) ? "main" : branchName.trim();

        // 3. 날짜 설정 (핵심 수정 부분!)
        // KST 00:00:00을 UTC로 변환하여 'Z' 포맷으로 만듭니다.
        // 예: 2026-02-05 00:00:00 KST -> 2026-02-04 15:00:00Z
        String sinceQuery = LocalDate.now(ZoneId.of("Asia/Seoul"))
                .atStartOfDay(ZoneId.of("Asia/Seoul"))
                .toInstant() // Instant로 변환하면 자동으로 UTC가 됩니다.
                .toString(); // "2026-02-04T15:00:00Z" 형태가 됨

        // 4. API URL 생성 (UriComponentsBuilder 사용)
        String url = UriComponentsBuilder.fromHttpUrl("https://api.github.com/repos")
                .pathSegment(repoInfo[0], repoInfo[1], "commits")
                .queryParam("sha", targetBranch)
                .queryParam("since", sinceQuery) // 변환된 UTC 시간 사용
                .queryParam("per_page", "100")
                .build()
                .toUriString();

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + decryptedToken);
            headers.set("Accept", "application/vnd.github.v3+json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);

            int count = response.getBody() != null ? response.getBody().size() : 0;
            log.info("브랜치({})의 오늘 커밋 수: {}", targetBranch, count);

            return count;

        } catch (Exception e) {
            log.error("커밋 조회 실패: {}", e.getMessage());
            return 0;
        }
    }

    // 최근 커밋 5개 가져오기
    public List<Map<String, Object>> getRecentCommits(Long projectId, String userId, String branchName) {
        // 1. 기본 검증 (기존과 동일)
        if (!projectMemberMapper.existsActiveMember(projectId.intValue(), userId)) return Collections.emptyList();
        ProjectVO project = projectMapper.selectProjectById(projectId);
        UserVO user = userMapper.getUserInfo(userId);
        if (project == null || user == null || user.getGithubToken() == null) return Collections.emptyList();

        String decryptedToken = encryptionUtil.decrypt(user.getGithubToken());
        String[] repoInfo = parseRepoUrl(project.getGithubRepoUrl());

        // 2. 브랜치 설정
        String targetBranch = (branchName == null || branchName.trim().isEmpty()) ? "main" : branchName.trim();

        // 3. API URL 생성 (per_page=5 로 최근 5개만 조회)
        String url = UriComponentsBuilder.fromHttpUrl("https://api.github.com/repos")
                .pathSegment(repoInfo[0], repoInfo[1], "commits")
                .queryParam("sha", targetBranch)
                .queryParam("per_page", "5") // 갯수 제한
                .build()
                .toUriString();

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + decryptedToken);
            headers.set("Accept", "application/vnd.github.v3+json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> rawCommits = response.getBody();

            // 4. 데이터 가공 (프론트에서 쓰기 편하게 필요한 정보만 추출)
            List<Map<String, Object>> result = new ArrayList<>();
            if (rawCommits != null) {
                for (Map<String, Object> commitObj : rawCommits) {
                    Map<String, Object> commitInfo = (Map<String, Object>) commitObj.get("commit");
                    Map<String, Object> committer = (Map<String, Object>) commitInfo.get("committer");

                    Map<String, Object> simplified = new HashMap<>();
                    simplified.put("message", commitInfo.get("message")); // 커밋 메시지
                    simplified.put("author", committer.get("name"));      // 작성자
                    simplified.put("date", committer.get("date"));        // 날짜
                    simplified.put("url", commitObj.get("html_url"));     // 깃허브 링크

                    result.add(simplified);
                }
            }
            return result;

        } catch (Exception e) {
            log.error("최근 커밋 로그 조회 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // 멤버 별 기여도 확인
    public List<Map<String, Object>> getMemberContribution(Long projectId, String userId, String branchName) {
        // 1. 검증 로직 (기존과 동일)
        if (!projectMemberMapper.existsActiveMember(projectId.intValue(), userId)) return Collections.emptyList();
        ProjectVO project = projectMapper.selectProjectById(projectId);
        UserVO user = userMapper.getUserInfo(userId);
        if (project == null || user == null || user.getGithubToken() == null) return Collections.emptyList();

        String decryptedToken = encryptionUtil.decrypt(user.getGithubToken());
        String[] repoInfo = parseRepoUrl(project.getGithubRepoUrl());
        String targetBranch = (branchName == null || branchName.trim().isEmpty()) ? "main" : branchName.trim();

        // 2. API 호출
        String url = UriComponentsBuilder.fromHttpUrl("https://api.github.com/repos")
                .pathSegment(repoInfo[0], repoInfo[1], "commits")
                .queryParam("sha", targetBranch)
                .queryParam("per_page", "100")
                .build()
                .toUriString();

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + decryptedToken);
            headers.set("Accept", "application/vnd.github.v3+json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> commits = response.getBody();

            if (commits == null) return Collections.emptyList();

            // [핵심 수정] 3. 데이터 그룹핑 (Key를 이름이 아닌 'GitHub ID'로 변경하여 중복 제거)
            Map<String, Map<String, Object>> userMap = new HashMap<>();

            for (Map<String, Object> commitObj : commits) {
                // 1) GitHub 계정 정보 가져오기 (author 필드)
                Map<String, Object> githubUser = (Map<String, Object>) commitObj.get("author");
                Map<String, Object> commitInfo = (Map<String, Object>) commitObj.get("commit");
                Map<String, Object> commitAuthor = (Map<String, Object>) commitInfo.get("author");

                String uniqueKey;
                String displayName;
                String avatarUrl = null;

                if (githubUser != null && githubUser.get("login") != null) {
                    // GitHub 계정과 연동된 커밋인 경우 -> Login ID를 키로 사용 (가장 정확함)
                    uniqueKey = (String) githubUser.get("login");
                    displayName = uniqueKey; // 보여줄 이름도 ID로 통일 (또는 commitAuthor.get("name") 사용 가능)
                    avatarUrl = (String) githubUser.get("avatar_url");
                } else {
                    // GitHub 계정 연동 안 된 커밋인 경우 -> 커밋에 적힌 이름 사용
                    String rawName = (String) commitAuthor.get("name");
                    uniqueKey = rawName.trim(); // 공백 제거
                    displayName = uniqueKey;
                }

                // 2) 카운트 집계
                // 이미 있는 사용자면 카운트 +1, 없으면 새로 생성
                if (userMap.containsKey(uniqueKey)) {
                    Map<String, Object> stat = userMap.get(uniqueKey);
                    stat.put("count", (int) stat.get("count") + 1);
                } else {
                    Map<String, Object> stat = new HashMap<>();
                    stat.put("name", displayName);
                    stat.put("count", 1);
                    stat.put("avatar", avatarUrl);
                    userMap.put(uniqueKey, stat);
                }
            }

            // 4. 리스트 변환 및 정렬
            List<Map<String, Object>> result = new ArrayList<>(userMap.values());
            result.sort((a, b) -> ((Integer) b.get("count")).compareTo((Integer) a.get("count")));

            return result;

        } catch (Exception e) {
            log.error("기여도 조회 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> getProjectLatestCommit(Long projectId, String userId) {
        // 1. 검증 로직 (기존 유지)
        if (!projectMemberMapper.existsActiveMember(projectId.intValue(), userId)) return null;
        ProjectVO project = projectMapper.selectProjectById(projectId);
        UserVO user = userMapper.getUserInfo(userId);
        if (project == null || user == null || user.getGithubToken() == null) return null;

        String decryptedToken = encryptionUtil.decrypt(user.getGithubToken());
        String[] repoInfo = parseRepoUrl(project.getGithubRepoUrl());

        // REST 템플릿 준비
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + decryptedToken);
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            // 2. Events API 호출
            String eventsUrl = UriComponentsBuilder.fromHttpUrl("https://api.github.com/repos")
                    .pathSegment(repoInfo[0], repoInfo[1], "events")
                    .queryParam("per_page", "10")
                    .build()
                    .toUriString();

            ResponseEntity<List> response = restTemplate.exchange(eventsUrl, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> events = response.getBody();

            if (events == null || events.isEmpty()) return null;

            // 3. PushEvent 탐색
            for (Map<String, Object> event : events) {
                if ("PushEvent".equals(event.get("type"))) {
                    Map<String, Object> payload = (Map<String, Object>) event.get("payload");
                    List<Map<String, Object>> commits = (List<Map<String, Object>>) payload.get("commits");
                    String ref = (String) payload.get("ref");
                    String branch = ref.replace("refs/heads/", "");

                    // Case A: 커밋 리스트가 있는 경우 (일반적인 푸시)
                    if (commits != null && !commits.isEmpty()) {
                        Map<String, Object> latestCommit = commits.get(commits.size() - 1);
                        Map<String, Object> result = new HashMap<>();
                        result.put("message", latestCommit.get("message"));
                        result.put("author", ((Map<String, Object>) latestCommit.get("author")).get("name"));
                        result.put("date", event.get("created_at"));
                        result.put("branch", branch);
                        return result;
                    }

                    // Case B: 커밋 리스트가 비어있는 경우 (강제 푸시 등) -> Head SHA로 단건 조회
                    else if (payload.get("head") != null) {
                        String headSha = (String) payload.get("head");
                        log.info(">>> 커밋 리스트 없음. Head SHA({})로 상세 조회 시도", headSha);

                        try {
                            String commitUrl = UriComponentsBuilder.fromHttpUrl("https://api.github.com/repos")
                                    .pathSegment(repoInfo[0], repoInfo[1], "commits", headSha)
                                    .build()
                                    .toUriString();

                            ResponseEntity<Map> commitRes = restTemplate.exchange(commitUrl, HttpMethod.GET, entity, Map.class);
                            Map<String, Object> commitData = commitRes.getBody(); // 전체 응답
                            Map<String, Object> commitDetails = (Map<String, Object>) commitData.get("commit"); // commit 객체
                            Map<String, Object> authorDetails = (Map<String, Object>) commitDetails.get("author");

                            Map<String, Object> result = new HashMap<>();
                            result.put("message", commitDetails.get("message"));
                            result.put("author", authorDetails.get("name"));
                            result.put("date", authorDetails.get("date"));
                            result.put("branch", branch);
                            return result;

                        } catch (Exception e) {
                            log.warn(">>> Head 커밋 상세 조회 실패: {}", e.getMessage());
                            // 실패하면 다음 이벤트 탐색
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("최신 커밋 조회 중 에러: {}", e.getMessage());
        }
        return null;
    }
}
