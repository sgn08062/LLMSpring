package com.example.LlmSpring.user;

import com.example.LlmSpring.user.response.UserSearchResponseDTO;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public List<UserSearchResponseDTO> searchUsersForInvitation(String keyword, String myUserId) {

        // DB 조회 전 파라미터 재확인
        if (keyword == null || myUserId == null) {
            return Collections.emptyList();
        }

        // Mapper를 통해 조회한 VO 리스트를 스트림을 사용하여 DTO 리스트로 변환
        return userMapper.searchUsers(keyword, myUserId).stream()
                .map(vo -> UserSearchResponseDTO.builder()
                        .userId(vo.getUserId())
                        .name(vo.getName())
                        .email(vo.getEmail())
                        .build())
                .collect(Collectors.toList());
    }

}