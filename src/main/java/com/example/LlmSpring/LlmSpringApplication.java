package com.example.LlmSpring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling // 스케줄링 기능 활성화
@SpringBootApplication
public class LlmSpringApplication {

	public static void main(String[] args) {
		SpringApplication.run(LlmSpringApplication.class, args);
	}

}
