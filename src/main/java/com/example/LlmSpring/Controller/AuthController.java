package com.example.LlmSpring.Controller;

import com.example.LlmSpring.DTO.SignUpResponse;
import com.example.LlmSpring.DTO.SignupRequest;
import com.example.LlmSpring.Service.AuthService;
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
    public ResponseEntity<SignUpResponse> signUp(@RequestBody SignupRequest req){
        SignUpResponse res = authService.signUp(req);

        if(res.isSuccess()){
            return ResponseEntity.status(201).body(res);
        }

        return switch(res.getCode()){
            case "DUP_EMAIL", "DUP_USERID" -> ResponseEntity.status(401).body(res);
            default -> ResponseEntity.status(400).body(res);
        };
    }
}
