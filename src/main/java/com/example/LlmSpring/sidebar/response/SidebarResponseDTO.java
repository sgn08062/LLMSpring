package com.example.LlmSpring.sidebar.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
public class SidebarResponseDTO {
    //즐겨찾기한 프로젝트 목록
    private List<SidebarProjectDTO> favorites = new ArrayList<>();

    //전체 프로젝트 목록
    private List<SidebarProjectDTO> projects = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SidebarProjectDTO {
        private Integer projectId;
        private String name;
        private boolean isFavorite;
    }
}
