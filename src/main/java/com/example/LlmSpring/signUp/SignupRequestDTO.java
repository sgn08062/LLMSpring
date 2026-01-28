package com.example.LlmSpring.signUp;

import lombok.Data;

@Data
public class SignupRequestDTO {
    private String userId;

    private String email;

    private String password;
}
