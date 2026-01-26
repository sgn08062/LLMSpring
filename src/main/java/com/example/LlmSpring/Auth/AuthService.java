package com.example.LlmSpring.Auth;

import com.example.LlmSpring.LogIn.LogInResponseDTO;
import com.example.LlmSpring.LogIn.LoginRequestDTO;
import com.example.LlmSpring.SignUp.SignUpResponseDTO;
import com.example.LlmSpring.SignUp.SignupRequestDTO;
import com.example.LlmSpring.User.UserMapper;
import com.example.LlmSpring.User.UserVO;
import com.example.LlmSpring.Util.JWTService;
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

    public LogInResponseDTO login(LoginRequestDTO req){
        String userId = req.getUserId();
        // 아이디가 없을 경우
        if(!userMapper.existsByUserId(userId)){
            return LogInResponseDTO.fail();
        }

        String hashPw = userMapper.getHashPw(req.getUserId());
        if(BCrypt.checkpw(req.getPassword(), hashPw)){
            String userName = userMapper.getUserName(userId);
            String token = jwtService.createToken(userId, userName);
            return LogInResponseDTO.ok(userName, token);
        }else{
            // 비밀번호 오류
            return LogInResponseDTO.fail();
        }
    }
}
