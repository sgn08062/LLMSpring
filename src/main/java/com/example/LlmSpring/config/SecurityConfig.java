package com.example.LlmSpring.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Autowired
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. CSRF 비활성화 (REST API는 보통 비활성화)
                .csrf(csrf -> csrf.disable())

                // 2. 기본 로그인 폼 및 팝업 비활성화
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                // 3. 특정 URL 허용 (회원가입 등)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**", "/error", "/oauth2/**").permitAll() // 회원가입 경로는 누구나 접근 가능
                        .anyRequest().authenticated() // 그 외는 인증 필요 (나중에 JWT 등을 붙일 때 사용)
                )
                // OAuth 설정
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(new SimpleUrlAuthenticationFailureHandler("http://localhost:3000/myPage"))
                );

        return http.build();
    }
}