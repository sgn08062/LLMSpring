package com.example.LlmSpring.controller;

import com.example.LlmSpring.github.GithubBranchResponseDTO;
import com.example.LlmSpring.github.GithubService;
import com.example.LlmSpring.util.JWTService;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/github")
@CrossOrigin(origins = "*")
public class GithubController {
    private final JWTService jwtService;
    private final GithubService githubService;

    @GetMapping("/{projectId}/getBranch")
    public ResponseEntity<?> getProjectBranch (@RequestHeader("Authorization") String authHeader,
                                               @PathVariable Long projectId,
                                               @RequestParam(defaultValue = "1") int page)
    {
        // 1. 토큰 검증 및 사용자 ID 추출
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않거나 만료된 토큰입니다.");
        }
        try{
            List<GithubBranchResponseDTO> branches = githubService.getProjectBranches(projectId, userId, page);
            return ResponseEntity.ok(branches);
        }catch (IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/{projectId}/commits")
    public ResponseEntity<?> getCommitsBySha(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long projectId,
            @RequestParam("sha") String sha,
            @RequestParam(defaultValue = "1") int page){
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        if(userId == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않거나 만료된 토큰입니다.");
        }

        try {
            List<Map<String, Object>> commits = githubService.getCommitsBySha(projectId, userId, sha, page);
            return ResponseEntity.ok(commits);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("커밋 로그 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }


    // 내 저장소 목록 조회 API
    @GetMapping("/repos")
    public ResponseEntity<?> getMyRepositories(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "1") int page // [추가] 페이지 번호 받기 (기본값 1)
    ) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
        }

        try {
            // 이제 GithubService에 getUserRepositories가 있으므로 호출 가능합니다!
            List<Map<String, Object>> repos = githubService.getUserRepositories(userId, page);
            return ResponseEntity.ok(repos);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // 오늘 커밋 수 조회 API (브랜치 선택 가능)
    @GetMapping("/{projectId}/today-commit-count")
    public ResponseEntity<?> getTodayCommitCount(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long projectId,
            @RequestParam(value = "branch", required = false, defaultValue = "main") String branchName
    ) {
        // 1. 토큰 검증
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않거나 만료된 토큰입니다.");
        }

        try {
            // Service 호출
            int commitCount = githubService.getTodayCommitCount(projectId, userId, branchName);
            // 결과를 Map이나 DTO로 감싸서 반환 (JSON 형태 유지를 위해)
            return ResponseEntity.ok(Map.of("commitCount", commitCount));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("커밋 수 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    // 최근 커밋 로그 조회 API
    @GetMapping("/{projectId}/recent-commits")
    public ResponseEntity<?> getRecentCommits(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long projectId,
            @RequestParam(value = "branch", required = false, defaultValue = "main") String branchName
    ) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<Map<String, Object>> commits = githubService.getRecentCommits(projectId, userId, branchName);
        return ResponseEntity.ok(commits);
    }

    // 기여도 조회 API
    @GetMapping("/{projectId}/contribution")
    public ResponseEntity<?> getContribution(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long projectId,
            @RequestParam(value = "branch", required = false, defaultValue = "main") String branchName
    ) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<Map<String, Object>> contribution = githubService.getMemberContribution(projectId, userId, branchName);
        return ResponseEntity.ok(contribution);
    }

    // 가장 최근 추가된 커밋 확인
    @GetMapping("/{projectId}/latest-commit")
    public ResponseEntity<?> getProjectLatestCommit(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long projectId
    ) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtService.verifyTokenAndUserId(token);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Map<String, Object> commitInfo = githubService.getProjectLatestCommit(projectId, userId);
        return ResponseEntity.ok(commitInfo != null ? commitInfo : Collections.emptyMap());
    }
}
