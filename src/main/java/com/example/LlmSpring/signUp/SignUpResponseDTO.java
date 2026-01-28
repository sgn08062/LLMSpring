package com.example.LlmSpring.signUp;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SignUpResponseDTO {
    private boolean success;
    private String code;
    private String message;
    private String userId;
    private String email;

    public static SignUpResponseDTO ok(String userId, String email){
        return new SignUpResponseDTO(true, "OK", "회원가입 성공", userId, email);
    }

    public static SignUpResponseDTO fail(String code, String message){
        return new SignUpResponseDTO(false, code, message, null, null);
    }
}
