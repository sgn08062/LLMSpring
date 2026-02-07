package com.example.LlmSpring.controller;

import com.example.LlmSpring.issue.chat.IssueChatService;
import com.example.LlmSpring.issue.chat.IssueChatVO;
import com.example.LlmSpring.project.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class IssueChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final IssueChatService chatService;
    private final ProjectAccessService projectAccessService;

    // -------------------------------------------------------------
    // 1. WebSocket 메시지 처리
    // -------------------------------------------------------------

    // 클라이언트가 보낼 주소: /pub/issue/chat/{issueId}
    @MessageMapping("/issue/chat/{issueId}")
    public void sendMessage(@DestinationVariable Integer issueId, @Payload IssueChatVO chatVO) {
        log.info("채팅 수신 issueId={}, sender={}", issueId, chatVO.getUserId());

        // 1. 데이터 보정 (보안상 백엔드에서 세팅하는게 좋음)
        chatVO.setIssueId(issueId);
        if (chatVO.getCreatedAt() == null) {
            chatVO.setCreatedAt(LocalDateTime.now());
        }

        // 2. DB 저장
        IssueChatVO savedChat = chatService.saveChat(chatVO);

        // 3. 구독자들에게 브로드캐스팅
        // 구독 주소: /sub/issue/{issueId}
        messagingTemplate.convertAndSend("/sub/issue/" + issueId, savedChat);
    }

    // -------------------------------------------------------------
    // 2. HTTP API (채팅 내역 조회)
    // -------------------------------------------------------------

    // 프로젝트 URL 구조에 맞춤: GET /api/projects/{projectId}/issues/{issueId}/chats
    @GetMapping("/api/projects/{projectId}/issues/{issueId}/chats")
    public List<IssueChatVO> getChatHistory(
            @AuthenticationPrincipal String userId,
            @PathVariable Integer projectId,
            @PathVariable Integer issueId) {

        // [읽기 권한] DELETE 상태면 OWNER만 접근 가능
        projectAccessService.validateReadAccess((long) projectId, userId);

        return chatService.getChatHistory(issueId);
    }
}