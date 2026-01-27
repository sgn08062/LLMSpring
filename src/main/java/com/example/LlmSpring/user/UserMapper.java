package com.example.LlmSpring.user;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
    int save(UserVO userVO);
    boolean existsByEmail(String email);
    boolean existsByUserId(String userId);

    String getHashPw(String userId);
    String getUserName(String userId);
}