package com.example.LlmSpring.User;

import org.springframework.web.bind.annotation.RequestParam;

public interface UserService {
    UserVO getUserInfo(String userId);
}
