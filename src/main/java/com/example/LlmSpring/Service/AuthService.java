package com.example.LlmSpring.Service;

import com.example.LlmSpring.DTO.SignUpResponse;
import com.example.LlmSpring.DTO.SignupRequest;
import com.example.LlmSpring.Entity.User;
import com.example.LlmSpring.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public SignUpResponse signUp(SignupRequest req){
        if(userRepository.existsByEmail(req.getEmail())){
            return SignUpResponse.fail("DUP_EMAIL", "이미 사용 중인 이메일입니다");
        }

        if(userRepository.existsByUserId(req.getUserId())){
            return SignUpResponse.fail("DUP_USERID", "이미 사용 중인 아이디입니다.");
        }

        String hashed = passwordEncoder.encode(req.getPassword());

        User user = User.builder()
                .userId(req.getUserId())
                .passwordHash(hashed)
                .name(req.getUserId())
                .email(req.getEmail())
                .build();

        userRepository.save(user);

        return SignUpResponse.ok(user.getUserId(), user.getEmail());
    }
}
