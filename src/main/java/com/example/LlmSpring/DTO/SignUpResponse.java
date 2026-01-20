package com.example.LlmSpring.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SignUpResponse {
    private boolean success;
    private String code;
    private String message;
    private String userId;
    private String email;

    public static SignUpResponse ok(String userId, String email){
        return new SignUpResponse(true, "OK", "회원가입 성공", userId, email);
    }

    public static SignUpResponse fail(String code, String message){
        return new SignUpResponse(false, code, message, null, null);
    }
}
