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

    public Map<String, Object> generateChatResponse(String reportType, String message, String context, boolean isSelection){
        // 1. 리포트 타입에 따른 시스템 프롬프트 설정
        String systemPersona = getSystemPersona(reportType);

        // 2. 최종 프롬프트 구성
        StringBuilder prompt = new StringBuilder();
        prompt.append(systemPersona).append("\n\n");

        if(isSelection && context != null && !context.isEmpty()){
            prompt.append("사용자가 다음 텍스트 블록을 선택했습니다:\n");
            prompt.append("\"\"\"\n").append(context).append("\n\"\"\"\n");
            prompt.append("선택된 부분에 대해 다음 요청을 처리하세요: ").append(message);
        } else {
            prompt.append("다음은 문서의 전체 내용입니다:\n");
            prompt.append("\"\"\"\n").append(context).append("\n\"\"\"\n");
            prompt.append("위 내용에 대해 다음 요청을 처리하세요: ").append(message);
        }

        // 3. Gemini API 호출
        String aiResponse = callGeminiAPI(prompt.toString());

        // 4. 결과 반환
        Map<String, Object> result = new HashMap<>();
        result.put("reply", aiResponse);
        return result;
    }

    private String getSystemPersona(String reportType) {
        if ("FINAL".equalsIgnoreCase(reportType)) {
            return "당신은 소프트웨어 프로젝트의 '최종 결과 보고서' 작성을 돕는 전문 테크니컬 라이터입니다. " +
                    "문체는 격식 있고 비즈니스 친화적이어야 하며, 프로젝트의 성과와 기술적 깊이를 강조하는 방향으로 조언하십시오.";
        } else {
            // DAILY 또는 기본값
            return "당신은 개발자의 '일일 업무 리포트(Daily Report)' 작성을 돕는 AI 어시스턴트입니다. " +
                    "문체는 간결하고 명확해야 하며, 코드 변경 사항이나 기술적 이슈를 요약하는 데 중점을 두십시오.";
        }
    }

    private String callGeminiAPI(String prompt) {
        String geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-latest:generateContent?key=" + geminiApiKey;

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);
        content.put("parts", Collections.singletonList(parts));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.3); // 창의성 조절
        requestBody.put("contents", Collections.singletonList(content));
        requestBody.put("generationConfig", generationConfig);

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
