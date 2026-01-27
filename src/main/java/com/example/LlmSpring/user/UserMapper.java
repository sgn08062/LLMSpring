package com.example.LlmSpring.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMapper {
    int save(UserVO userVO);
    boolean existsByEmail(String email);
    boolean existsByUserId(String userId);

    String getHashPw(String userId);
    String getUserName(String userId);

    /**
     * 키워드(ID 또는 이름)를 통한 유저 검색
     * @param keyword 검색어 (ID 또는 이름의 일부)
     * @param myUserId 현재 세션 유저 ID (검색 결과에서 본인 제외용)
     * @return 검색된 유저 VO 리스트
     */
    // 1. 키워드로 유저 검색 (본인 제외, 삭제 유저 제외)
    List<UserVO> searchUsers(@Param("keyword") String keyword, @Param("myUserId") String myUserId);
}