package com.example.LlmSpring.config;

import com.example.LlmSpring.project.ProjectMapper;
import com.example.LlmSpring.project.ProjectVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ProjectMapper projectMapper;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 프론트에서 연결할 주소: ws://localhost:8080/ws-stomp
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("*") // CORS 허용
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub"); // 구독 경로
        registry.setApplicationDestinationPrefixes("/pub"); // 발행 경로
    }

    /**
     * 클라이언트로부터 들어오는 메시지를 가로채서 검사
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

                // 1. 연결 시 (CONNECT)
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // 프론트엔드에서 연결 시 헤더에 projectId를 담아서 보내야 함
                    String projectIdStr = accessor.getFirstNativeHeader("projectId");

                    if (projectIdStr != null) {
                        try {
                            Long projectId = Long.parseLong(projectIdStr);
                            ProjectVO project = projectMapper.selectProjectById(projectId);

                            // DELETE 상태면 연결 거부 (예외 발생 시키면 연결 끊어짐)
                            if (project != null && project.getDeletedAt() != null) {
                                log.warn("삭제된 프로젝트({}) 연결 시도 차단", projectId);
                                throw new IllegalArgumentException("삭제된 프로젝트입니다.");
                            }
                        } catch (NumberFormatException e) {
                            log.error("Invalid projectId format in CONNECT header: {}", projectIdStr);
                        }
                    }
                }

                // 2. 메시지 전송 시 (SEND)
                if (StompCommand.SEND.equals(accessor.getCommand())) {
                    String projectIdStr = accessor.getFirstNativeHeader("projectId");

                    if (projectIdStr != null) {
                        try {
                            Long projectId = Long.parseLong(projectIdStr);
                            ProjectVO project = projectMapper.selectProjectById(projectId);

                            // DONE 상태면 메시지 전송 거부 (읽기 전용)
                            if (project != null && "DONE".equals(project.getStatus())) {
                                log.warn("완료된 프로젝트({}) 메시지 전송 차단", projectId);
                                throw new IllegalArgumentException("완료된 프로젝트에서는 메시지를 보낼 수 없습니다.");
                            }

                            // DELETE 상태도 당연히 막아야 함 (이중 방어)
                            if (project != null && project.getDeletedAt() != null) {
                                throw new IllegalArgumentException("삭제된 프로젝트입니다.");
                            }

                        } catch (NumberFormatException e) {
                            log.error("Invalid projectId format in SEND header: {}", projectIdStr);
                        }
                    }
                }
                return message;
            }
        });
    }
}