package com.example.LlmSpring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity // 스프링 시큐리티 설정을 활성화
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. CSRF 보안 비활성화
                // REST API는 세션을 사용하지 않는 경우가 많고, POSTMAN 테스트 시 CSRF 토큰이 없으면 403 에러가 발생하므로 끕니다.
                .csrf(AbstractHttpConfigurer::disable)

                // 2. 특정 URL 허용 (회원가입 등)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/error").permitAll() // 회원가입 경로는 누구나 접근 가능
                        .anyRequest().authenticated() // 그 외는 인증 필요 (나중에 JWT 등을 붙일 때 사용)
                )
                // 3. REST API 개발 시 방해되는 UI 요소 비활성화
                .formLogin(AbstractHttpConfigurer::disable) // 기본 로그인 페이지 사용 안 함
                .httpBasic(AbstractHttpConfigurer::disable); // HTTP Basic 인증 팝업 사용 안 함;

        return http.build();
    }
}