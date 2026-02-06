package com.example.LlmSpring.controller;

import com.example.LlmSpring.github.GithubBranchResponseDTO;
import com.example.LlmSpring.github.GithubService;
import com.example.LlmSpring.project.ProjectAccessService;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 추가됨
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/github")
@CrossOrigin(origins = "*")
public class GithubController {

    private final GithubService githubService;
    private final ProjectAccessService projectAccessService;

    @GetMapping("/{projectId}/getBranch")
    public ResponseEntity<?> getProjectBranch(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") int page)
    {

        // [읽기 권한] DELETE 상태면 OWNER만 접근 가능
        projectAccessService.validateReadAccess(projectId, userId);

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
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @RequestParam("sha") String sha,
            @RequestParam(defaultValue = "1") int page){

        // [읽기 권한]
        projectAccessService.validateReadAccess(projectId, userId);

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
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "1") int page
    ) {
        try {
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
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @RequestParam(value = "branch", required = false, defaultValue = "main") String branchName
    ) {
        // [읽기 권한]
        projectAccessService.validateReadAccess(projectId, userId);

        try {
            int commitCount = githubService.getTodayCommitCount(projectId, userId, branchName);
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
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @RequestParam(value = "branch", required = false, defaultValue = "main") String branchName
    ) {

        // [읽기 권한]
        projectAccessService.validateReadAccess(projectId, userId);

        List<Map<String, Object>> commits = githubService.getRecentCommits(projectId, userId, branchName);
        return ResponseEntity.ok(commits);
    }

    // 기여도 조회 API
    @GetMapping("/{projectId}/contribution")
    public ResponseEntity<?> getContribution(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId,
            @RequestParam(value = "branch", required = false, defaultValue = "main") String branchName
    ) {

        // [읽기 권한]
        projectAccessService.validateReadAccess(projectId, userId);

        List<Map<String, Object>> contribution = githubService.getMemberContribution(projectId, userId, branchName);
        return ResponseEntity.ok(contribution);
    }

    // 가장 최근 추가된 커밋 확인
    @GetMapping("/{projectId}/latest-commit")
    public ResponseEntity<?> getProjectLatestCommit(
            @AuthenticationPrincipal String userId,
            @PathVariable Long projectId
    ) {

        Map<String, Object> commitInfo = githubService.getProjectLatestCommit(projectId, userId);
        return ResponseEntity.ok(commitInfo != null ? commitInfo : Collections.emptyMap());
    }
}