package com.church.website.config;

import com.church.website.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Spring Security 보안 설정 클래스.
 *
 * <p>이 클래스에서 세 가지 보안 영역을 설정한다.
 * <ul>
 *   <li>접근 제어: 공개 URL과 관리자 전용 URL을 구분하여 권한을 부여한다.
 *       /admin/** 는 ROLE_ADMIN 만 접근 가능, 그 외 공개 경로는 모두 허용.</li>
 *   <li>보안 헤더: X-Frame-Options, CSP(Content Security Policy) 등을 설정하여
 *       클릭재킹·XSS 같은 웹 공격을 방어한다.</li>
 *   <li>로그인/로그아웃: 로그인 폼 URL, 성공·실패 처리, 로그아웃 후 쿠키 삭제를 설정한다.</li>
 *   <li>세션: 동일 계정의 동시 로그인을 1개로 제한한다.</li>
 * </ul>
 *
 * <p>로그인 실패 처리 흐름:
 * <ul>
 *   <li>failureHandler가 호출됨 → LoginAttemptService.loginFailed() 로 실패 횟수 누적</li>
 *   <li>5회 이상 실패 시 → /login?blocked=true 로 redirect (15분 잠금)</li>
 *   <li>그 이하 실패 시 → /login?error=true 로 redirect</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    // 로그인 성공·실패 시 실패 횟수를 기록·초기화하기 위해 주입
    private final LoginAttemptService loginAttemptService;

    /**
     * HTTP 보안 규칙을 정의하는 핵심 Bean.
     *
     * <p>SecurityFilterChain은 모든 HTTP 요청이 컨트롤러에 도달하기 전에
     * 거치는 보안 필터 체인이다. 여기서 허용/거부·인증·헤더를 순서대로 설정한다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // 공개 페이지, 정적 리소스, 새가족 폼은 로그인 없이 접근 가능
                .requestMatchers(
                    "/", "/about/**", "/worship/**", "/notice/**", "/location",
                    "/ministry/**", "/new-family/**", "/sermon/**", "/login",
                    "/css/**", "/js/**", "/images/**", "/uploads/**", "/favicon.ico", "/favicon.svg"
                ).permitAll()
                // 관리자 페이지는 ROLE_ADMIN 권한 필수
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // 관리자 외 주소는 모두 공개라 나머지도 허용한다. authenticated()면 없는 주소가 404 대신 로그인으로 간다.
                // 로그인이 필요한 새 기능은 반드시 /admin/** 아래에 둘 것
                .anyRequest().permitAll()
            )
            .headers(headers -> headers
                // iframe 삽입 금지 — 클릭재킹 방어
                .frameOptions(fo -> fo.deny())
                .contentTypeOptions(cto -> {})
                // 외부 링크 클릭 시 Referer 헤더를 현재 출처만 전송
                .referrerPolicy(rp -> rp
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // CSP: 허용된 출처의 스크립트·스타일·이미지만 로드 허용
                // kakao.com은 지도 API, youtube.com은 설교 영상 iframe 때문에 허용
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(
                        "default-src 'self'; " +
                        // 카카오 SDK는 지도 본체·주소 검색 스크립트를 t1.kakaocdn.net에서 받는다 (daumcdn은 구 주소)
                        "script-src 'self' 'unsafe-inline' dapi.kakao.com https://t1.kakaocdn.net http://t1.kakaocdn.net https://t1.daumcdn.net http://t1.daumcdn.net; " +
                        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                        "font-src 'self' https://fonts.gstatic.com; " +
                        "img-src 'self' data: blob: *; " +
                        "frame-src 'self' www.youtube.com youtube.com; " +
                        // 카카오 지오코더(services)는 페이지 프로토콜을 따라가므로 dev(http)·운영(https) 모두 허용
                        "connect-src 'self' https://dapi.kakao.com http://dapi.kakao.com https://t1.kakaocdn.net http://t1.kakaocdn.net https://t1.daumcdn.net http://t1.daumcdn.net"
                    )
                )
            )
            .formLogin(form -> form
                .loginPage("/login")
                .permitAll()
                .successHandler((request, response, authentication) -> {
                    String username = authentication.getName();
                    loginAttemptService.loginSucceeded(username); // 실패 기록 초기화
                    log.info("로그인 성공: {}", username);
                    response.sendRedirect("/admin");
                })
                .failureHandler((request, response, exception) -> {
                    String username = request.getParameter("username");
                    if (username != null) {
                        loginAttemptService.loginFailed(username); // 실패 횟수 누적
                    }
                    log.warn("로그인 실패 시도 감지: {}", username);
                    // 5회 이상 실패한 계정이면 잠금 안내 페이지로, 그렇지 않으면 일반 오류 페이지로
                    if (username != null && loginAttemptService.isBlocked(username)) {
                        response.sendRedirect("/login?blocked=true");
                    } else {
                        response.sendRedirect("/login?error=true");
                    }
                })
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?logout=true")
                .invalidateHttpSession(true)   // 서버 세션 즉시 무효화
                .deleteCookies("JSESSIONID")   // 클라이언트 쿠키도 삭제
                .permitAll()
            )
            .sessionManagement(session -> session
                .maximumSessions(1)            // 같은 계정 동시 로그인 1개 제한
                .expiredUrl("/login?expired=true")
            );

        return http.build();
    }

    /**
     * 비밀번호 암호화 Bean.
     * BCrypt는 단방향 해시 함수로, 같은 평문도 실행마다 다른 해시값을 생성한다.
     * matches()로 평문과 해시값의 일치 여부만 확인할 수 있고, 원문은 복원 불가능하다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
