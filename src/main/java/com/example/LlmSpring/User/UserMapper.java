package com.example.LlmSpring.User;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
    int save(UserVO userVO);
    boolean existsByEmail(String email);
    boolean existsByUserId(String userId);

    String getHashPw(String userId);
    String getUserName(String userId);
    UserVO getUserInfo(String userId);

    void updateGithubInfo(UserVO userVO);
    UserVO findByEmail(String email); // 비상용
}