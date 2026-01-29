package com.example.LlmSpring.user;

import com.example.LlmSpring.user.response.UserSearchResponseDTO;
import java.util.Collections;

import com.example.LlmSpring.util.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final S3Service s3Service;

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

    @Override
    public UserVO getUserInfo(String userId) {
        return userMapper.getUserInfo(userId);
    }

    @Override
    public void updateProfile(String userId, String nickname, MultipartFile file) throws Exception {
        UserVO userVO = new UserVO();
        userVO.setUserId(userId);
        userVO.setName(nickname);

        System.out.println("UserService까지 진입");

        // 새 파일이 있는 경우에만 S3 업로드 및 경로 설정
        if (file != null && !file.isEmpty()) {
            UserVO oldUser = userMapper.getUserInfo(userId);
            if(oldUser.getFilePath() != null) s3Service.deleteFile(oldUser.getFilePath());

            String newUrl = s3Service.uploadFile(file);
            userVO.setFilePath(newUrl);
        }

        userMapper.updateProfile(userVO);
    }
}