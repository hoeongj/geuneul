package com.geuneul.domain.alert;

import com.geuneul.domain.alert.dto.SurgeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE 구독자 레지스트리(ADR-0016 §3) — GET /alerts/stream 으로 붙은 브라우저 EventSource들을 담고,
 * 급증 이벤트를 브로드캐스트한다. 이 인스턴스에 붙은 구독자만 관리하고, 다른 인스턴스가 처리한 제보는
 * LISTEN/NOTIFY(ReportNotificationListener)를 통해 이 인스턴스로도 전달돼 여기서 밀어 넣는다.
 *
 * <p>스레드 안전: 구독 등록(요청 스레드)·브로드캐스트(리스너 스레드)·정리가 동시에 일어나므로
 * CopyOnWrite 성격의 concurrent set을 쓴다. 죽은 emitter(전송 실패·완료·타임아웃)는 즉시 제거해 누수 방지.
 */
@Component
public class SurgeEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SurgeEmitterRegistry.class);

    // 브라우저 EventSource는 연결이 끊기면 자동 재연결하므로, 서버 타임아웃은 넉넉히(30분) 두고 주기 재연결에 맡긴다.
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;
    static final int DEFAULT_MAX_CONNECTIONS = 1_000;

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final AtomicInteger connectionCount = new AtomicInteger();
    private final int maxConnections;

    public SurgeEmitterRegistry() {
        this(DEFAULT_MAX_CONNECTIONS);
    }

    SurgeEmitterRegistry(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    /** 새 SSE 구독을 등록하고 emitter를 돌려준다. 완료/타임아웃/에러 시 스스로 레지스트리에서 빠진다. */
    public SseEmitter register() {
        return tryRegister().orElseThrow(() -> new IllegalStateException("SSE connection limit exceeded"));
    }

    /** 새 SSE 구독을 등록한다. 전역 상한 초과 시 빈 Optional을 반환한다. */
    public Optional<SseEmitter> tryRegister() {
        if (connectionCount.incrementAndGet() > maxConnections) {
            connectionCount.decrementAndGet();
            return Optional.empty();
        }
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> remove(emitter));
        emitter.onTimeout(() -> remove(emitter));
        emitter.onError(e -> remove(emitter));
        return Optional.of(emitter);
    }

    /** 모든 구독자에게 급증 이벤트를 민다. 전송 실패한 emitter는 제거한다. */
    public void broadcast(SurgeInfo surge) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("surge").data(surge));
            } catch (IOException | IllegalStateException e) {
                // 구독자가 이미 끊김(브라우저 탭 종료 등) — 조용히 제거(급증 알림은 부가 기능, 실패가 치명적 아님).
                remove(emitter);
            }
        }
    }

    /** 현재 구독자 수(관측/테스트용). */
    public int subscriberCount() {
        return connectionCount.get();
    }

    /** 앱 종료 시 남은 emitter를 정리(선택적 — 서버가 내려가면 어차피 클라가 재연결 시도). */
    public void completeAll() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (RuntimeException e) {
                log.debug("[alert] emitter complete 중 무시 가능한 예외", e);
            } finally {
                remove(emitter);
            }
        }
    }

    private void remove(SseEmitter emitter) {
        if (emitters.remove(emitter)) {
            connectionCount.decrementAndGet();
        }
    }
}
