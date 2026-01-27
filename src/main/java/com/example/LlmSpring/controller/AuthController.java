package com.example.LlmSpring.controller;

import com.example.LlmSpring.logIn.LogInResponseDTO;
import com.example.LlmSpring.logIn.LoginRequestDTO;
import com.example.LlmSpring.signUp.SignUpResponseDTO;
import com.example.LlmSpring.signUp.SignupRequestDTO;
import com.example.LlmSpring.auth.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    AuthService authService;

    @PostMapping("/signUp")
    public ResponseEntity<SignUpResponseDTO> signUp(@RequestBody SignupRequestDTO req){
        SignUpResponseDTO res = authService.signUp(req);

        if(res.isSuccess()){
            return ResponseEntity.status(201).body(res);
        }

        return switch(res.getCode()){
            case "DUP_EMAIL", "DUP_USERID" -> ResponseEntity.status(401).body(res);
            default -> ResponseEntity.status(400).body(res);
        };
    }

    @PostMapping("/logIn")
    public ResponseEntity<LogInResponseDTO> logIn(@RequestBody LoginRequestDTO req){
        LogInResponseDTO res = authService.login(req);

        if(res.isSuccess()){
            return ResponseEntity.status(200).body(res);
        }

        return switch(res.getCode()){
            case "FAIL_LOGIN" -> ResponseEntity.status(401).body(res);
            default -> ResponseEntity.status(400).body(res);
        };
    }
}
