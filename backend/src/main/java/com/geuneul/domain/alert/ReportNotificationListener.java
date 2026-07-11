package com.geuneul.domain.alert;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Postgres LISTEN/NOTIFY 백그라운드 리스너(ADR-0016 §2·§4) — 실시간 제보 급증 전파의 수신 측.
 *
 * <p>전용 커넥션으로 {@code LISTEN geuneul_report_surge} 하다가, 다른(또는 같은) 인스턴스의 제보 INSERT
 * 트리거(V9)가 쏜 NOTIFY(place_id)를 받으면 → 그 장소의 급증 여부를 시공간 SQL로 재확인
 * ({@link ReportSurgeService#surgeForPlace}) → 급증이면 이 인스턴스의 SSE 구독자에게 브로드캐스트
 * ({@link SurgeEmitterRegistry}). ECS 오토스케일링(min1/max3, ADR-0013)에서 insert 인스턴스 ≠ 구독
 * 인스턴스일 수 있으므로, 인프로세스 이벤트가 아니라 DB 브로드캐스트로 전 인스턴스가 알림을 받는다.
 *
 * <p><b>왜 전용 커넥션을 물고 있나(HikariCP 함정, IngestBatchLock과 같은 이유)</b>: LISTEN 등록은
 * 세션(커넥션) 수준이다. JdbcTemplate처럼 매번 열고-닫으면 LISTEN이 걸린 세션이 풀로 반환돼 알림을
 * 못 받는다. 그래서 {@link DataSource#getConnection()}으로 얻은 커넥션 하나를 리스너 수명 내내 물고
 * {@code getNotifications(timeout)}로 블로킹 폴링한다. 커넥션이 끊기면 백오프 후 재연결한다.
 *
 * <p><b>안전장치</b>: {@code geuneul.realtime.enabled=false}면 빈으로 등록되지 않아(테스트/로컬에서 끔)
 * CI가 백그라운드 스레드에 의존하지 않는다. 리스너가 실패해도 핵심 제보/조회 경로는 무영향이다
 * (SSE·푸시는 부가 기능, 폴백 폴링 {@code GET /alerts/surge}가 있음).
 */
@Component
@ConditionalOnProperty(name = "geuneul.realtime.enabled", havingValue = "true", matchIfMissing = true)
public class ReportNotificationListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ReportNotificationListener.class);

    private static final String CHANNEL = "geuneul_report_surge";
    private static final long POLL_TIMEOUT_MS = 10_000;   // getNotifications 블로킹 상한 — 이 주기로 running 플래그도 재확인
    private static final long RECONNECT_BACKOFF_MS = 5_000; // 커넥션 끊김 시 재연결 대기(재연결 폭주 방지)

    private final DataSource dataSource;
    private final ReportSurgeService surgeService;
    private final SurgeEmitterRegistry registry;
    private final com.geuneul.domain.notification.NotificationService notificationService;

    private volatile boolean running = false;
    private volatile Connection listenConnection;
    private Thread worker;

    public ReportNotificationListener(DataSource dataSource, ReportSurgeService surgeService,
                                      SurgeEmitterRegistry registry,
                                      com.geuneul.domain.notification.NotificationService notificationService) {
        this.dataSource = dataSource;
        this.surgeService = surgeService;
        this.registry = registry;
        this.notificationService = notificationService;
    }

    @Override
    public void start() {
        running = true;
        worker = new Thread(this::listenLoop, "report-surge-listener");
        worker.setDaemon(true);   // 앱 종료를 막지 않는다
        worker.start();
        log.info("[alert] 제보 급증 LISTEN 리스너 시작 (채널={})", CHANNEL);
    }

    @Override
    public void stop() {
        running = false;
        closeQuietly(listenConnection);   // 블로킹 중인 getNotifications를 깨운다
        if (worker != null) {
            worker.interrupt();
        }
        registry.completeAll();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 리스너는 웹 트래픽을 받기 시작하는 시점 근처면 충분 — 기본 phase로 둔다. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    private void listenLoop() {
        while (running) {
            try (Connection conn = dataSource.getConnection()) {
                this.listenConnection = conn;
                try (Statement st = conn.createStatement()) {
                    st.execute("LISTEN " + CHANNEL);
                }
                PGConnection pg = conn.unwrap(PGConnection.class);
                log.info("[alert] LISTEN {} 연결 확립", CHANNEL);
                pollUntilClosed(pg);
            } catch (SQLException e) {
                if (running) {
                    log.warn("[alert] LISTEN 커넥션 오류 — {}ms 후 재연결 시도", RECONNECT_BACKOFF_MS, e);
                    sleep(RECONNECT_BACKOFF_MS);
                }
            } finally {
                this.listenConnection = null;
            }
        }
        log.info("[alert] 제보 급증 LISTEN 리스너 종료");
    }

    /** 한 커넥션이 살아있는 동안 알림을 폴링한다. 커넥션이 끊기면 SQLException으로 빠져나가 재연결한다. */
    private void pollUntilClosed(PGConnection pg) throws SQLException {
        while (running) {
            PGNotification[] notifications = pg.getNotifications((int) POLL_TIMEOUT_MS);
            if (notifications == null) {
                continue;   // 타임아웃 — running 재확인 후 계속
            }
            for (PGNotification n : notifications) {
                handle(n.getParameter());
            }
        }
    }

    /**
     * NOTIFY 페이로드(place_id 문자열) 처리. 두 갈래를 <b>독립적으로</b> 예외 격리한다(한쪽 실패가 다른쪽·루프를 안 죽임):
     * (1) 급증이면 SSE 배지 브로드캐스트 + 급증 알림 규칙 평가(ADR-0018), (2) 급증 여부와 무관하게 관심 장소 단건
     * 상태 알림(침수·미끄럼)을 무조건 평가(C3·ADR-0026 — 단건은 급증 게이트 밖).
     */
    private void handle(String payload) {
        long placeId;
        try {
            placeId = Long.parseLong(payload.trim());
        } catch (NumberFormatException e) {
            log.warn("[alert] 알 수 없는 NOTIFY 페이로드(무시): {}", payload);
            return;
        }
        try {
            surgeService.surgeForPlace(placeId).ifPresent(surge -> {
                registry.broadcast(surge);       // A4 지도 배지(SSE)
                notificationService.onSurge(surge); // B1 알림 규칙 평가·발송(ADR-0018)
            });
        } catch (RuntimeException e) {
            log.warn("[alert] 급증 처리 실패(무시하고 계속): placeId={}", placeId, e);
        }
        try {
            notificationService.onBookmarkStatus(placeId); // C3 관심 장소 단건 상태 알림(급증 게이트 밖·무조건)
        } catch (RuntimeException e) {
            log.warn("[alert] 관심장소 상태 알림 실패(무시하고 계속): placeId={}", placeId, e);
        }
    }

    private static void closeQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.close();
        } catch (SQLException ignored) {
            // 재연결 로직이 커버 — 조용히 무시
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
