package com.geuneul.domain.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * 순수 단위 테스트(JDBC를 Mockito로 대체) — 실 Postgres 없이도 "같은 Connection으로
 * lock/unlock을 짝지어 호출한다"는 이 클래스의 핵심 계약(HikariCP 함정 회피)을 검증한다.
 * 진짜 동시성(두 세션이 동시에 advisory lock을 다툴 때 하나만 이긴다)은 Testcontainers 실 PostGIS로
 * {@code IngestBatchLockIT}에서 검증 — 여기는 그 앞단의 배선(호출 순서·예외 전파)만 다룬다.
 */
class IngestBatchLockTest {

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement lockStatement;
    private PreparedStatement unlockStatement;
    private ResultSet lockResultSet;
    private IngestBatchLock batchLock;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        lockStatement = mock(PreparedStatement.class);
        unlockStatement = mock(PreparedStatement.class);
        lockResultSet = mock(ResultSet.class);

        given(dataSource.getConnection()).willReturn(connection);
        given(connection.prepareStatement(eq("SELECT pg_try_advisory_lock(?)"))).willReturn(lockStatement);
        given(connection.prepareStatement(eq("SELECT pg_advisory_unlock(?)"))).willReturn(unlockStatement);
        given(lockStatement.executeQuery()).willReturn(lockResultSet);
        given(lockResultSet.next()).willReturn(true);

        batchLock = new IngestBatchLock(dataSource);
    }

    @Test
    @DisplayName("락 획득 성공 시 action을 실행하고, 같은 커넥션으로 unlock까지 호출한다")
    void acquiresLockRunsActionAndUnlocks() throws Exception {
        given(lockResultSet.getBoolean(1)).willReturn(true);
        AtomicBoolean ran = new AtomicBoolean(false);
        Callable<Void> action = () -> {
            ran.set(true);
            return null;
        };

        boolean acquired = batchLock.runExclusive(action);

        assertThat(acquired).isTrue();
        assertThat(ran).isTrue();
        then(unlockStatement).should(times(1)).execute();
        then(connection).should(times(1)).close(); // try-with-resources — 풀에 반환(물리 세션은 유지)
    }

    @Test
    @DisplayName("락을 이미 다른 실행이 쥐고 있으면 action을 실행하지 않고 false를 반환한다(unlock도 호출 안 함)")
    void lockNotAcquiredSkipsActionAndDoesNotUnlock() throws Exception {
        given(lockResultSet.getBoolean(1)).willReturn(false);
        AtomicBoolean ran = new AtomicBoolean(false);
        Callable<Void> action = () -> {
            ran.set(true);
            return null;
        };

        boolean acquired = batchLock.runExclusive(action);

        assertThat(acquired).isFalse();
        assertThat(ran).isFalse();
        then(connection).should(never()).prepareStatement(eq("SELECT pg_advisory_unlock(?)"));
    }

    @Test
    @DisplayName("action이 예외를 던져도 unlock은 수행되고(finally), 예외는 그대로 전파된다")
    void actionExceptionStillUnlocksAndPropagates() throws Exception {
        given(lockResultSet.getBoolean(1)).willReturn(true);
        RuntimeException boom = new RuntimeException("ingest 실패");
        Callable<Void> action = () -> {
            throw boom;
        };

        assertThatThrownBy(() -> batchLock.runExclusive(action)).isSameAs(boom);

        then(unlockStatement).should(times(1)).execute();
    }

    @Test
    @DisplayName("SQLException(커넥션/락 처리 실패)은 IllegalStateException으로 감싸 전파한다")
    void sqlExceptionIsWrapped() throws SQLException {
        given(dataSource.getConnection()).willThrow(new SQLException("커넥션 획득 실패"));
        Callable<Void> action = () -> null;

        assertThatThrownBy(() -> batchLock.runExclusive(action))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(SQLException.class);
    }
}
