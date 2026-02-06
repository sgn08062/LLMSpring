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

    @Value("${jwt.access.expiration}")
    private long accessTokenValidity;

    @Value("${jwt.refresh.expiration}")
    private long refreshTokenValidity;

    // 1. Access Token 생성 (짧은 만료, 정보 포함)
    public String createAccessToken(String userId, String username){
        return createToken(userId, username, accessTokenValidity);
    }

    // 2. Refresh Token 생성 (긴 만료, 최소 정보)
    public String createRefreshToken(String userId){
        // Refresh Token에는 민감한 정보를 넣지 않는 것이 좋습니다.
        return createToken(userId, null, refreshTokenValidity);
    }

    // 내부적으로 사용하는 토큰 생성 헬퍼 메서드
    private String createToken(String userId, String username, long validity){
        Algorithm algorithm = Algorithm.HMAC256(secretKey);

        JWTCreator.Builder builder = JWT.create()
                .withSubject(userId)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + validity))
                .withIssuer("sgn08062");

        if(username != null){
            builder.withClaim("userName", username);
        }

        return builder.sign(algorithm);
    }

    public String verifyTokenAndUserId(String token){
        try{
            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer("sgn08062")
                    .build();
            DecodedJWT decodeJWT = verifier.verify(token);
            return decodeJWT.getSubject();
        }
        catch (Exception e){
            return null; // 검증 실패 시 null 반환
        }
    }
}