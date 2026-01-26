package com.example.LlmSpring.sidebar.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SidebarResponseDTO {
    //즐겨찾기한 프로젝트 목록
    private List<SidebarResponseDTO> favorites = new ArrayList<>();

    //전체 프로젝트 목록
    private List<SidebarResponseDTO> projects = new ArrayList<>();

    @Data
    public static class SidebarProjectDTO {
        private Long projectId;
        private String name;
        private boolean isFavorite;
    }
}
