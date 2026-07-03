package com.geuneul.domain.report;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 익명 제보 레이트리밋의 "클라이언트 신원" 해석 — XFF 신뢰경계 문제(코드리뷰 확정)를 해결한다.
 *
 * <p>배경: 우리 앱은 두 경로로 들어온다. ① 브라우저 → Vercel BFF(서버 프록시) → ALB, ② 공격자 → ALB 직접.
 * ALB는 실 접속 IP를 XFF <b>최우측</b>에 append 하므로, 클라이언트가 보낸 XFF 최좌측은 위조 가능하다.
 * 따라서 "최좌측 XFF를 키로 쓰면" 공격자가 XFF를 회전시켜 무한 키를 만들어 리밋을 우회한다.
 *
 * <p>해결(신뢰경계 명시):
 * <ul>
 *   <li><b>BFF가 공유 시크릿({@code X-Proxy-Auth})으로 자신을 증명</b>하면, BFF가 판정한 실제 클라이언트
 *       IP({@code X-Client-Ip})를 신뢰한다 → BFF 경로의 유저별 리밋(다중 유저 정상 동작).</li>
 *   <li>시크릿이 설정돼 있는데 증명이 없으면(=ALB 직접 타격) ALB가 append 한 <b>최우측 XFF</b>(위조 불가)로
 *       키잉 → 직접 남용은 실 IP당 하드 리밋.</li>
 *   <li>시크릿 <b>미설정(현재/개발)</b>이면 기존 호환(최좌측 XFF) — 회귀 없음. 활성화는 배포 후 config 한 번.</li>
 * </ul>
 * 순수 오버로드({@link #resolve(String, String, String, String)})로 단위 테스트한다.
 */
@Component
public class ProxyClientResolver {

    private final String proxySecret;

    public ProxyClientResolver(@Value("${geuneul.proxy-secret:}") String proxySecret) {
        this.proxySecret = proxySecret;
    }

    public String resolve(HttpServletRequest http) {
        return resolve(
                http.getHeader("X-Proxy-Auth"),
                http.getHeader("X-Client-Ip"),
                http.getHeader("X-Forwarded-For"),
                http.getRemoteAddr());
    }

    /**
     * @param proxyAuth  BFF가 보낸 공유 시크릿 헤더
     * @param clientIp   BFF가 판정한 실 클라이언트 IP 헤더
     * @param xff        X-Forwarded-For 원문(콤마 구분, ALB가 최우측에 실 접속 IP append)
     * @param remoteAddr TCP 피어(ALB 뒤에선 ALB 노드 IP)
     * @return 레이트리밋 키(네임스페이스 접두사로 신뢰수준 구분)
     */
    String resolve(String proxyAuth, String clientIp, String xff, String remoteAddr) {
        boolean secretConfigured = StringUtils.hasText(proxySecret);

        // ① BFF가 시크릿으로 증명 → BFF가 준 실 클라이언트 IP 신뢰
        if (secretConfigured && proxySecret.equals(proxyAuth) && StringUtils.hasText(clientIp)) {
            return "c:" + clientIp.strip();
        }

        // ② XFF 기반 — 시크릿이 켜져 있으면 위조 불가한 최우측(ALB append), 아니면 기존 호환 최좌측
        if (StringUtils.hasText(xff)) {
            String[] hops = xff.split(",");
            String token = secretConfigured ? hops[hops.length - 1] : hops[0];
            if (StringUtils.hasText(token)) {
                return "x:" + token.strip();
            }
        }

        // ③ 최후: TCP 피어
        return "x:" + (remoteAddr == null ? "unknown" : remoteAddr);
    }
}
