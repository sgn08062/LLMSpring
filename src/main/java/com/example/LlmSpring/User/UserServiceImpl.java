package com.example.LlmSpring.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserVO getUserInfo(String userId) {
        return userMapper.getUserInfo(userId);
    }
}
