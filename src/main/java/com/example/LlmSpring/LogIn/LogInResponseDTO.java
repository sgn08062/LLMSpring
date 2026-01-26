package com.example.LlmSpring.LogIn;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LogInResponseDTO {
    private boolean success;
    private String code;
    private String message;
    private String userName;
    private String token;

    public static LogInResponseDTO ok(String userName, String token){
        return new LogInResponseDTO(true,"SUCCESS_LOGIN",userName+"님 환영합니다!", userName, token);
    }

    public static LogInResponseDTO fail(){
        return new LogInResponseDTO(false, "FAIL_LOGIN", "아이디 또는 비밀번호가 틀렸습니다", null, null);
    }
}
