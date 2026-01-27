package com.example.LlmSpring.Config;

import com.example.LlmSpring.User.UserMapper;
import com.example.LlmSpring.User.UserVO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        // 1. Github 에서 넘어온 정보 추출
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();

        // 2. Access Token 추출 (수정된 부분)
        // clientRegistrationId는 "github" 같은 설정값이어야 함
        String clientRegistrationId = oauthToken.getAuthorizedClientRegistrationId();
        String principalName = oauthToken.getName();

        OAuth2AuthorizedClient authorizedClient = oAuth2AuthorizedClientService.loadAuthorizedClient(
                clientRegistrationId, // 첫 번째 인자: "github"
                principalName         // 두 번째 인자: 사용자 식별자
        );

        // 방어 코드: 만약 authorizedClient가 여전히 null이면 중단
        if (authorizedClient == null) {
            throw new ServletException("OAuth2AuthorizedClient not found");
        }

        String accessToken = authorizedClient.getAccessToken().getTokenValue();

        // Github ID는 Integer일 수도 있으므로 안전하게 String 변환
        String githubId = String.valueOf(oAuth2User.getAttributes().get("id"));

        // 3. 쿠키에서 link_user_id 찾기
        String targetUserId = null;
        Cookie[] cookies = request.getCookies();

        // 쿠키가 없어도 로직이 돌아가도록 null 체크 위치 주의 (아래 로직 수정)
        if(cookies != null){
            for(Cookie cookie : cookies){
                if("link_user_id".equals(cookie.getName())){
                    targetUserId = cookie.getValue();
                    // 사용한 쿠키 삭제 (청소)
                    cookie.setValue("");
                    cookie.setPath("/");
                    cookie.setMaxAge(0);
                    response.addCookie(cookie);
                    break;
                }
            }
        } // cookies for문 종료

        UserVO user = null;

        // 4. 연동 대상 찾기
        if(targetUserId != null){
            // A: 쿠키가 있으면 해당 ID 사용 (강제 연동)
            System.out.println("쿠키 감지: " + targetUserId + " 계정에 연동합니다");
            user = userMapper.getUserInfo(targetUserId);
        } else {
            // B: 쿠키가 없으면 이메일로 찾기 (백업 로직)
            String email = (String) oAuth2User.getAttributes().get("email");
            if(email != null){
                // 주의: 기존 UserMapper에 findByEmail이 없으면 getUserInfo 대신 적절한 메소드 사용 필요
                // 여기서는 기존 코드 맥락상 이메일로 찾는 쿼리가 있다고 가정하거나,
                // 기존 getUserInfo가 email도 처리하는지 확인 필요. 보통은 findByEmail 별도 구현 권장.
                user = userMapper.findByEmail(email);
            }
        }

        // 5. DB 업데이트
        if(user != null){
            user.setGithubId(githubId);
            user.setGithubToken(accessToken);

            userMapper.updateGithubInfo(user);
            System.out.println("깃허브 연동 성공: " + user.getUserId());
        } else {
            System.out.println("연동 실패: 사용자 정보를 찾을 수 없습니다 (쿠키 없음 & 이메일 매칭 실패)");
        }

        // 6. 프론트엔드 마이페이지로 복귀 (if문 밖으로 빼야 함)
        // 쿠키가 null이어도 리다이렉트는 되어야 하므로 밖으로 이동했습니다.
        response.sendRedirect("http://localhost:3000/myPage");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authentication) throws IOException, ServletException {
        AuthenticationSuccessHandler.super.onAuthenticationSuccess(request, response, chain, authentication);
    }
}
