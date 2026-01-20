package com.example.LlmSpring.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        http
                // CSRF 끄기(REST API면 보통 disable)
                .csrf(csrf -> csrf.disable())

                // 세션 안씀(JWT 기반이면 권장)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 인증/인가 규칙
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // ✅ 회원가입/로그인 열기
                        .anyRequest().authenticated()
                )

                // 기본 로그인 폼 끄기(로그인 페이지 뜨는 원인)
                .formLogin(form -> form.disable())

                // basic auth도 끄기(원하면)
                .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
