package com.geuneul.domain.ingest;

import com.geuneul.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실 PostGIS로 검증하는 진짜 동시성 시나리오 — P3 EventBridge Scheduler가 겹치거나 사람이 수동
 * {@code prod-ingest.sh}를 스케줄과 동시에 돌려도 "딱 한 실행만" 배치를 수행함을 확인한다.
 * IngestBatchLockTest(Mockito)는 호출 순서·예외 전파만 검증하고, 여기서만 실제 두 세션이
 * 같은 advisory lock 키를 다투는 상황을 재현한다(Mockito로는 재현 불가 — 진짜 DB 세션이 필요).
 */
class IngestBatchLockIT extends AbstractIntegrationTest {

    @Autowired
    IngestBatchLock batchLock;

    @Test
    @DisplayName("동시에 두 실행이 락을 다투면 하나만 성공하고, 하나는 즉시 포기(블로킹 없음)한다")
    void onlyOneConcurrentRunWinsTheLock() throws Exception {
        CountDownLatch holderStarted = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        AtomicInteger executedCount = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // 먼저 락을 쥐고 releaseHolder가 열릴 때까지 붙잡고 있는 "홀더" 실행.
            Future<Boolean> holder = pool.submit(() -> batchLock.runExclusive(() -> {
                executedCount.incrementAndGet();
                holderStarted.countDown();
                releaseHolder.await(5, TimeUnit.SECONDS);
                return null;
            }));

            assertThat(holderStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // 홀더가 아직 락을 쥔 상태에서 두 번째 실행 시도 — 논블로킹으로 즉시 false를 받아야 한다.
            Future<Boolean> challenger = pool.submit(() -> batchLock.runExclusive(() -> {
                executedCount.incrementAndGet();
                return null;
            }));

            assertThat(challenger.get(5, TimeUnit.SECONDS)).isFalse();

            releaseHolder.countDown();
            assertThat(holder.get(5, TimeUnit.SECONDS)).isTrue();

            // 챌린저는 락을 못 얻어 action을 실행하지 않았으므로 딱 1번만 실행됨.
            assertThat(executedCount.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("락 해제 후 재시도하면 정상적으로 다시 획득한다(누수 없음)")
    void lockIsReleasedForNextRun() throws Exception {
        AtomicInteger executed = new AtomicInteger();

        boolean first = batchLock.runExclusive(() -> {
            executed.incrementAndGet();
            return null;
        });
        boolean second = batchLock.runExclusive(() -> {
            executed.incrementAndGet();
            return null;
        });

        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(executed.get()).isEqualTo(2);
    }
}
