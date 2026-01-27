package com.example.LlmSpring.sidebar.controller;

import com.example.LlmSpring.sidebar.SidebarService;
import com.example.LlmSpring.sidebar.response.ProjectSidebarResponseDTO;
import com.example.LlmSpring.sidebar.response.SidebarResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SidebarController {
    private final SidebarService sidebarService;

    //메인 사이드바
    @GetMapping("/sidebar")
    public SidebarResponseDTO getMainSidebar() {
        String userId = "user1";
        return sidebarService.getMainSidebar(userId);
    }

    //프로젝트 사이드바
    @GetMapping("/projects/{projectId}/sidebar")
    public ProjectSidebarResponseDTO getProjectSidebar(@PathVariable Long projectId) {
        String userId = "user1";
        return sidebarService.getProjectSidebar(projectId, userId);
    }
}
