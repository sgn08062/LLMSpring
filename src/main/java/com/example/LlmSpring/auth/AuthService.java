package com.example.LlmSpring.auth;

import com.example.LlmSpring.logIn.LogInResponseDTO;
import com.example.LlmSpring.logIn.LoginRequestDTO;
import com.example.LlmSpring.signUp.SignUpResponseDTO;
import com.example.LlmSpring.signUp.SignupRequestDTO;
import com.example.LlmSpring.user.UserMapper;
import com.example.LlmSpring.user.UserVO;
import com.example.LlmSpring.util.JWTService;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JWTService jwtService;

    @Transactional
    public SignUpResponseDTO signUp(SignupRequestDTO req){
        if(userMapper.existsByEmail(req.getEmail())){
            return SignUpResponseDTO.fail("DUP_EMAIL", "이미 사용 중인 이메일입니다");
        }

        if(userMapper.existsByUserId(req.getUserId())){
            return SignUpResponseDTO.fail("DUP_USERID", "이미 사용 중인 아이디입니다.");
        }

        String hashed = BCrypt.hashpw(req.getPassword(), BCrypt.gensalt());

        UserVO user = UserVO.builder()
                .userId(req.getUserId())
                .passwordHash(hashed)
                .name(req.getUserId())
                .email(req.getEmail())
                .build();

        userMapper.save(user);

        return SignUpResponseDTO.ok(user.getUserId(), user.getEmail());
    }

    @Transactional
    public LogInResponseDTO login(LoginRequestDTO req){
        String userId = req.getUserId();

        // 아이디 존재 여부 확인
        if(!userMapper.existsByUserId(userId)){
            return LogInResponseDTO.fail();
        }

        String hashPw = userMapper.getHashPw(userId);

        if(BCrypt.checkpw(req.getPassword(), hashPw)){
            String userName = userMapper.getUserName(userId);

            // 1. 토큰 2종 생성
            String accessToken = jwtService.createAccessToken(userId, userName);
            String refreshToken = jwtService.createRefreshToken(userId);

            // 2. Refresh Token DB에 저장 (기존에 있으면 업데이트)
            userMapper.updateRefreshToken(userId, refreshToken);

            // 3. 응답 (DTO도 수정 필요)
            return LogInResponseDTO.ok(userId, accessToken, refreshToken);
        } else {
            // 비밀번호 오류
            return LogInResponseDTO.fail();
        }
    }

    public String reissueAccessToken(String refreshToken) {
        // 1. Refresh Token 유효성 및 만료 검사
        String userId = jwtService.verifyTokenAndUserId(refreshToken);
        if (userId == null) {
            return null; // 토큰이 만료되었거나 유효하지 않음
        }

        // 2. DB에 저장된 Refresh Token 가져오기
        String storedRefreshToken = userMapper.getRefreshToken(userId);

        // 3. 클라이언트가 보낸 토큰과 DB 토큰이 일치하는지 확인
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            return null;
        }

        // 4. 새로운 Access Token 발급
        String userName = userMapper.getUserName(userId);
        return jwtService.createAccessToken(userId, userName);
    }
}
