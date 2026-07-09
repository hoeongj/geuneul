package com.geuneul.global.security;

import com.geuneul.domain.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 스테이트리스 JWT 보안. **라이브 앱이라 기존 공개 엔드포인트를 절대 막지 않는다** —
 * /me·POST /reviews만 인증을 요구하고 그 외 전부 permitAll(default-permit). 로그인 필요한
 * 신규 기능이 늘어나면 그때 requestMatchers로 명시 추가한다.
 *
 * - CSRF 비활성(토큰 기반 무상태 API, 쿠키 세션 없음), 세션 STATELESS.
 * - 미인증 보호 경로는 401(로그인 페이지 리다이렉트 아님 — API).
 * - formLogin/httpBasic 비활성(소셜 로그인만).
 * - POST /reviews만 보호(작성=로그인 필요, §1 UGC 2단 구조) — GET /places/{id}/reviews(조회)는
 *   공개라 anyRequest().permitAll()에 그대로 걸린다(메서드 미지정 매처와 달리 POST만 좁게 잠금).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/reviews").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
