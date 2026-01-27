package com.example.LlmSpring.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JWTService {
    @Value("${jwt.secret}")
    private String secretKey;
    @Value("${jwt.expiration}")
    private long expirationTime;

    public String createToken(String userId, String username){
        // 암호화 알고리즘
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        // JWT
        JWTCreator.Builder builder = JWT.create()
                .withSubject(userId)
                .withClaim("userName", username) // 추가 정보(이름)
                .withIssuedAt(new Date()) // 발행 시간
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationTime)) // 만료 시간
                .withIssuer("sgn08062");

        return builder.sign(algorithm); // 비밀 키로 서명하여 토큰 생성
    }

    public String verifyTokenAndUserId(String token){
        try{
            Algorithm algorithm = Algorithm.HMAC256(secretKey);

            // 검증기 생성
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer("sgn08062")
                    .build();
            // 토큰 검증 (위조되었거나 만료되면 여기서 예외 발생)
            DecodedJWT decodeJWT = verifier.verify(token);

            // userId 추출
            return decodeJWT.getSubject();
        }
        catch (Exception e){
            return null;
        }
    }
}
