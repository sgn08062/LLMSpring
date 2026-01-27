package com.example.LlmSpring.Controller;

import com.example.LlmSpring.User.UserService;
import com.example.LlmSpring.User.UserVO;
import com.example.LlmSpring.Util.JWTService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins="*")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private JWTService jwtService;

    @GetMapping("/info")
    public ResponseEntity<?> getUserInfo(@RequestParam String token){
        System.out.println("사용자 정보 받기 위해 진입");
        // jwt 검증하고 사용자id 반환
        String userId = jwtService.verifyTokenAndUserId(token);
        UserVO userVO = userService.getUserInfo(userId);

        if (userVO != null) {
            // 보안상 사용자 아이디, 사용자 이름, 사용자 이메일만 전송
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userVO.getUserId());
            response.put("name", userVO.getName());
            response.put("email", userVO.getEmail());

            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(404).body("User not found");
        }
    }
}
