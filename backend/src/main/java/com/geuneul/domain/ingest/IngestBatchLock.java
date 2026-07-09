package com.geuneul.domain.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Callable;

/**
 * 배치 인제스천 동시 실행 방지 가드 (P3, EventBridge Scheduler → ECS RunTask 무인화).
 *
 * Postgres 세션 수준 advisory lock({@code pg_try_advisory_lock})으로 구현한다 — 월 1회 저빈도
 * 스케줄이 새 인프라(SQS 잠금 서비스 등) 없이 "이미 있는 DB 하나"로 상호배제를 얻을 수 있어
 * CLAUDE.md §0.2(과설계 금지)와 정합한다. 스케줄이 겹치거나(예: 실행 지연) 사람이 수동으로
 * {@code prod-ingest.sh}를 동시에 돌려도, 나중 실행은 즉시(논블로킹) 포기하고 다음 스케줄을 기다린다
 * — "실패"가 아니라 "건너뜀"으로 취급해 exitCode=0 유지(불필요한 배포 알림/재시도 노이즈 방지).
 *
 * <p><b>주의(HikariCP 함정)</b>: {@link javax.sql.DataSource#getConnection()}으로 얻은 커넥션을
 * {@code JdbcTemplate}처럼 매번 열고-닫으면, 커넥션 풀은 물리 커넥션(=Postgres 백엔드 세션)을 재사용
 * 하므로 {@code pg_try_advisory_lock}이 건 세션 수준 락이 살아있는 채로 풀에 반환된다. 그래서 락을
 * 건 것과 정확히 같은 {@link Connection} 객체를 잠금 해제({@code pg_advisory_unlock})까지 계속
 * 물고 있어야 한다 — 이 클래스가 커넥션을 직접 관리하는 이유.
 *
 * <p>크래시 안전성: JVM이 배치 도중 죽어도(ECS 태스크 강제 종료 등) 그 물리 커넥션 자체가 함께
 * 끊기므로 Postgres가 세션 종료 시 advisory lock을 자동 해제한다 — 명시적 unlock을 못 타도
 * 영구 데드락으로 남지 않는다.
 */
@Component
public class IngestBatchLock {

    private static final Logger log = LoggerFactory.getLogger(IngestBatchLock.class);

    // 임의 상수 — 이 값 자체엔 의미가 없고, 그늘의 "배치 인제스천" 전용 락 키임을 프로세스 전역에서
    // 유일하게 식별하기만 하면 된다(다른 advisory lock 용도가 생기면 값을 바꿔 충돌 방지).
    private static final long LOCK_KEY = 0x67_65_75_6E_65_75_6CL; // "geuneul"의 앞 7바이트를 hex로

    private final DataSource dataSource;

    public IngestBatchLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 락을 얻으면 action을 실행하고 true, 이미 다른 실행이 진행 중이면 action을 실행하지 않고
     * false를 즉시 반환한다(블로킹 대기 없음 — {@code pg_try_advisory_lock}).
     *
     * @throws Exception action이 던진 예외를 그대로 전파(체크 예외 포함 — 호출부 IngestionRunner가
     *                    IOException 등을 그대로 다루던 기존 계약을 유지하기 위해 Callable을 씀).
     */
    public boolean runExclusive(Callable<Void> action) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            if (!tryLock(conn)) {
                log.warn("[ingest] 다른 배치 인제스천이 이미 실행 중 — 이번 실행은 건너뜁니다(동시 실행 방지 가드)");
                return false;
            }
            try {
                action.call();
            } finally {
                unlock(conn);
            }
            return true;
        } catch (SQLException e) {
            throw new IllegalStateException("[ingest] advisory lock 처리 실패", e);
        }
    }

    private boolean tryLock(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, LOCK_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private void unlock(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            ps.setLong(1, LOCK_KEY);
            ps.execute();
        } catch (SQLException e) {
            // 명시적 해제 실패는 로그만 — 이 메서드를 호출한 커넥션이 곧 close()되며(try-with-resources),
            // 풀이 물리 커넥션을 재사용하는 한 최악의 경우 다음 실행이 그 커넥션을 다시 집을 때까지
            // 락이 남을 수 있지만 영구 누수는 아니다(위 클래스 주석의 크래시 안전성과 동일 논리).
            log.warn("[ingest] advisory unlock 실패(무시 가능) — 커넥션 회수 시 정리됨", e);
        }
    }
}
