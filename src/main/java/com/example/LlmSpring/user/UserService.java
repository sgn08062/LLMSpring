package com.example.LlmSpring.user;

import com.example.LlmSpring.user.response.UserSearchResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {

    /**
     * 초대할 팀원을 찾기 위한 유저 검색 서비스
     */
    // 1. 초대를 위한 유저 검색
    List<UserSearchResponseDTO> searchUsersForInvitation(String keyword, String myUserId);

    UserVO getUserInfo(String userId);

    void updateProfile(String userId, String nickname, MultipartFile file) throws Exception;
}