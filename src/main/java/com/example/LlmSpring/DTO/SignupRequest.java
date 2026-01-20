package com.example.LlmSpring.DTO;

import lombok.Data;

@Data
public class SignupRequest {
    private String userId;

    private String email;

    private String password;
}
