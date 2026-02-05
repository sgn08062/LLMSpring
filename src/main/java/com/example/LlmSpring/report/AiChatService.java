package com.example.LlmSpring.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
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
        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=" + geminiApiKey;

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
            // [수정] ParameterizedTypeReference를 사용하여 처음부터 제네릭 타입을 명확히 함 (권장)
            // 또는 제안하신 (Class<Map<String, Object>>) (Class<?>) Map.class 방식도 OK
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    geminiUrl,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> body = response.getBody();
            if (body == null) {
                return "AI 응답 오류: 응답 본문이 비어있습니다.";
            }

            // 헬퍼 메서드로 안전하게 캐스팅
            List<Map<String, Object>> candidates = asListOfMap(body.get("candidates"));
            if (candidates == null || candidates.isEmpty()) {
                return "AI 응답 오류: candidates가 비어있습니다.";
            }

            Map<String, Object> firstCandidate = candidates.get(0);
            Object contentObj = firstCandidate.get("content");

            // content가 Map 형태인지 확인
            if (contentObj instanceof Map<?, ?>) {
                Map<String, Object> contentMap = asMap((Map<?, ?>) contentObj);

                List<Map<String, Object>> partsList = asListOfMap(contentMap.get("parts"));
                if (partsList != null && !partsList.isEmpty()) {
                    Object text = partsList.get(0).get("text");
                    if (text instanceof String s) {
                        return s;
                    }
                }
                return "AI 응답 오류: parts/text 정보를 찾을 수 없습니다.";
            } else {
                // content가 없으면 차단 사유 확인
                Object finishReasonObj = firstCandidate.get("finishReason");
                String finishReason = (finishReasonObj instanceof String s) ? s : "UNKNOWN";
                log.warn("Gemini 응답 거부. 사유: {}", finishReason);
                return "AI가 응답을 생성하지 못했습니다. (사유: " + finishReason + ")";
            }

        } catch (HttpClientErrorException e) {
            log.error("Gemini API 호출 오류 (4xx): {}", e.getResponseBodyAsString());
            return "요청 오류가 발생했습니다. (관리자 문의)";
        } catch (Exception e) {
            log.error("Gemini 호출 실패: {}", e.getMessage());
            return "오류 발생: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMap(Object obj) {
        if (!(obj instanceof List<?> list)) return null;

        // 리스트가 비어있지 않다면 첫 번째 요소로 타입 체크 (더 엄격하게 하려면 전체 루프)
        if (!list.isEmpty() && !(list.get(0) instanceof Map)) {
            return null;
        }

        return (List<Map<String, Object>>) obj;
    }
}