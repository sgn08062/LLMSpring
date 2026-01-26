package com.example.LlmSpring.sidebar;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectFavoriteVO {
    private Integer favoriteId;
    private LocalDateTime createdAt;
    private Integer projectId;
    private String userId;
}
