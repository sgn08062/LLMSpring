package com.example.LlmSpring.controller;

import com.example.LlmSpring.sidebar.SidebarService;
import com.example.LlmSpring.sidebar.response.ProjectSidebarResponseDTO;
import com.example.LlmSpring.sidebar.response.SidebarResponseDTO;
import com.example.LlmSpring.util.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SidebarController {
    private final SidebarService sidebarService;
    private final JWTService jWTService;

    //메인 사이드바
    @GetMapping("/sidebar")
    public ResponseEntity<?> getMainSidebar(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
        String userId = jWTService.verifyTokenAndUserId(token);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        return ResponseEntity.ok(sidebarService.getMainSidebar(userId));
    }

    //프로젝트 사이드바
    @GetMapping("/projects/{projectId}/sidebar")
    public ResponseEntity<?> getProjectSidebar(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("projectId") Long projectId) {

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
        String userId = jWTService.verifyTokenAndUserId(token);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        return ResponseEntity.ok(sidebarService.getProjectSidebar(projectId, userId));
    }
}
