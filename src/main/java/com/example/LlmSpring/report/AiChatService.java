package com.example.LlmSpring.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public Map<String, Object> generateChatResponse(String reportType, String userMessage, String context, boolean isSelection) {

        // 1. 페르소나 및 시스템 지침 설정
        String systemInstruction = getSystemPersona(reportType);

        // 2. 최종 프롬프트 조립
        StringBuilder finalPrompt = new StringBuilder();
        finalPrompt.append(systemInstruction).append("\n\n");

        if (isSelection && context != null && !context.isEmpty()) {
            finalPrompt.append("--- [Target Text Start] ---\n")
                    .append(context).append("\n")
                    .append("--- [Target Text End] ---\n\n");
            finalPrompt.append("Request: ").append(userMessage).append("\n");

            finalPrompt.append("Constraint: Rewrite the target text based on the request. ")
                    .append("Output ONLY the rewritten text in raw Markdown format. ")
                    .append("Strictly maintain the Markdown syntax (headers, lists, bold, etc.). ")
                    .append("Do NOT wrap the output in code blocks (```). ") // 백틱 감싸기 금지 (Ctrl+C/V 최적화)
                    .append("Do NOT include any intro, outro, explanations, or labels.");
        } else {
            finalPrompt.append("--- [Context Start] ---\n")
                    .append(context != null ? context : "(No content)").append("\n")
                    .append("--- [Context End] ---\n\n");
            finalPrompt.append("Request: ").append(userMessage).append("\n");
            finalPrompt.append("Constraint: Answer the request based on the context. If the request is generation or rewriting, output ONLY the result text without explanations.");
        }

        // 3. 내부 메서드로 Gemini API 호출
        String aiReply = callGemini(finalPrompt.toString());

        // 4. 결과 반환
        Map<String, Object> response = new HashMap<>();
        response.put("reply", aiReply.trim());
        return response;
    }


    private String getSystemPersona(String reportType) {
        if ("FINAL".equalsIgnoreCase(reportType)) {
            // 최종 리포트: 전문적, 격식 있음, 설명 금지
            return "You are a professional technical writer. " +
                    "Your task is to rewrite or refine the user's text to be formal and professional. " +
                    "IMPORTANT: You must output ONLY the rewritten text. " +
                    "Do NOT add greetings, 'Here is the revised version', markdown headers, or explanations. " +
                    "Just provide the final result directly.";
        } else if ("DAILY".equalsIgnoreCase(reportType)) {
            // 일일 리포트: 간결함, 명확함, 설명 금지
            return "You are an AI assistant for daily scrum reports. " +
                    "Refine the text to be clear and concise. " +
                    "IMPORTANT: Output ONLY the refined text. No conversational fillers or explanations.";
        } else {
            return "You are a helpful AI assistant. Answer concisely and output ONLY the result.";
        }
    }

    /**
     * Gemini API 호출 (RestTemplate 직접 사용)
     */
    private String callGemini(String prompt) {
        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-latest:generateContent?key=" + geminiApiKey;

        // Request Payload 구성
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);
        content.put("parts", Collections.singletonList(parts));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.3); // 창의성 낮춤 (정확한 수정 유도)
        requestBody.put("contents", Collections.singletonList(content));
        requestBody.put("generationConfig", generationConfig);

        // HTTP 요청 전송
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(geminiUrl, HttpMethod.POST, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null) {
                List<Map> candidates = (List<Map>) body.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map contentMap = (Map) candidates.get(0).get("content");
                    List<Map> partsList = (List<Map>) contentMap.get("parts");
                    return (String) partsList.get(0).get("text");
                }
            }
            return "AI 응답 오류: 응답 본문이 비어있습니다.";
        } catch (Exception e) {
            log.error("Gemini 호출 실패: {}", e.getMessage());
            return "오류 발생: " + e.getMessage();
        }
    }
}