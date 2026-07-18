package com.geuneul.domain.ingest;

import com.sun.net.httpserver.HttpServer;
import com.geuneul.domain.ingest.openapi.PublicLibraryIngestionService;
import com.geuneul.domain.ingest.safetydata.ShelterIngestionService;
import com.geuneul.domain.ingest.storeapi.StoreIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionRunnerTest {

    @TempDir
    Path tempDir;

    private IngestionService ingestionService;
    private PublicLibraryIngestionService libraryService;
    private StoreIngestionService storeService;
    private ShelterIngestionService shelterService;
    private IngestBatchLock batchLock;
    private IngestRunLedger ledger;
    private IngestionRunner runner;

    @BeforeEach
    void setUp() {
        ingestionService = mock(IngestionService.class);
        libraryService = mock(PublicLibraryIngestionService.class);
        storeService = mock(StoreIngestionService.class);
        shelterService = mock(ShelterIngestionService.class);
        batchLock = mock(IngestBatchLock.class);
        ledger = mock(IngestRunLedger.class);
        runner = new IngestionRunner(ingestionService, libraryService, storeService, shelterService,
                batchLock, ledger, mock(ConfigurableApplicationContext.class));
    }

    @Test
    @DisplayName("scheduled 도서관 실행의 정규화 요약과 retry 계보를 원장에 완료 기록한다")
    void recordsScheduledLibraryRun() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID retryOf = UUID.randomUUID();
        when(ledger.start(eq("library_api"), anyString(), eq(IngestRunLedger.Trigger.SCHEDULED), eq(retryOf)))
                .thenReturn(runId);
        when(libraryService.ingestAll(true)).thenReturn(
                new PublicLibraryIngestionService.LibraryIngestSummary(
                        30, 30, true, 2, 29, 0, 1, 0, 0, 2, 7));
        doAnswer(invocation -> {
            Callable<Void> action = invocation.getArgument(0);
            action.call();
            return true;
        }).when(batchLock).runExclusive(any());

        runner.run(new DefaultApplicationArguments(
                "--ingest.source=library",
                "--ingest.trigger=scheduled",
                "--ingest.retry-of=" + retryOf,
                "--ingest.deactivate-stale=true"));

        verify(ledger).complete(eq(runId), argThat(result -> result.totalRecords() == 30
                && result.backfilledRecords() == 7 && !result.partial()), anyLong());
        verify(ledger, never()).fail(any(), any(), anyLong());
    }

    @Test
    @DisplayName("advisory lock을 못 얻은 실행은 성공 종료하되 SKIPPED로 남긴다")
    void recordsSkippedRun() throws Exception {
        UUID runId = UUID.randomUUID();
        when(ledger.start(eq("library_api"), anyString(), eq(IngestRunLedger.Trigger.MANUAL), eq(null)))
                .thenReturn(runId);
        when(batchLock.runExclusive(any())).thenReturn(false);

        runner.run(new DefaultApplicationArguments("--ingest.source=library"));

        verify(ledger).skip(eq(runId), anyLong());
        verify(libraryService, never()).ingestAll(anyBoolean());
    }

    @Test
    @DisplayName("수집 예외는 원장에 FAILED로 기록하고 서버 유지 모드에서는 원래 예외를 전파한다")
    void recordsFailureAndRethrows() throws Exception {
        UUID runId = UUID.randomUUID();
        IllegalStateException failure = new IllegalStateException("외부 응답 실패");
        when(ledger.start(eq("library_api"), anyString(), eq(IngestRunLedger.Trigger.MANUAL), eq(null)))
                .thenReturn(runId);
        when(libraryService.ingestAll(false)).thenThrow(failure);
        doAnswer(invocation -> {
            Callable<Void> action = invocation.getArgument(0);
            return action.call();
        }).when(batchLock).runExclusive(any());

        var args = new DefaultApplicationArguments("--ingest.source=library");

        assertThatThrownBy(() -> runner.run(args)).isSameAs(failure);
        verify(ledger).fail(eq(runId), eq(failure), anyLong());
    }

    @Test
    @DisplayName("ingest.source가 없으면 원장과 수집기를 건드리지 않는다")
    void ignoresNormalServerStartup() throws Exception {
        runner.run(new DefaultApplicationArguments());

        verify(ledger, never()).start(any(), any(), any(), any());
        verify(batchLock, never()).runExclusive(any());
    }

    @Test
    @DisplayName("retry 입력 fingerprint는 같은 의미에 결정적이고 stale/bbox 조건 변화에 민감하다")
    void fingerprintsNormalizedInputSemantics() throws Exception {
        var library = new DefaultApplicationArguments("--ingest.source=library");
        var libraryAgain = new DefaultApplicationArguments("--ingest.source=library");
        var libraryDeactivate = new DefaultApplicationArguments(
                "--ingest.source=library", "--ingest.deactivate-stale=true");
        var storesA = new DefaultApplicationArguments(
                "--ingest.source=stores", "--ingest.radius=1500",
                "--ingest.bbox=126.76,37.42,127.18,37.70");
        var storesB = new DefaultApplicationArguments(
                "--ingest.source=stores", "--ingest.radius=2000",
                "--ingest.bbox=126.76,37.42,127.18,37.70");

        assertThat(IngestionRunner.inputFingerprint(library))
                .matches("[0-9a-f]{64}")
                .isEqualTo(IngestionRunner.inputFingerprint(libraryAgain))
                .isNotEqualTo(IngestionRunner.inputFingerprint(libraryDeactivate));
        assertThat(IngestionRunner.inputFingerprint(storesA))
                .isNotEqualTo(IngestionRunner.inputFingerprint(storesB));
    }

    @Test
    @DisplayName("CSV retry fingerprint는 경로가 아니라 실제 내용과 contract version을 고정한다")
    void fingerprintsCsvSnapshotContent() throws Exception {
        Path first = tempDir.resolve("first.csv");
        Path second = tempDir.resolve("second.csv");
        Files.writeString(first, "name,address\nA,Seoul\n", StandardCharsets.UTF_8);
        Files.writeString(second, "name,address\nA,Seoul\n", StandardCharsets.UTF_8);

        var firstArgs = new DefaultApplicationArguments(
                "--ingest.source=public_toilet", "--ingest.file=" + first);
        var sameContentOtherPath = new DefaultApplicationArguments(
                "--ingest.source=public_toilet", "--ingest.file=" + second);

        assertThat(IngestionRunner.inputFingerprint(firstArgs))
                .isEqualTo(IngestionRunner.inputFingerprint(sameContentOtherPath));

        Files.writeString(first, "name,address\nB,Busan\n", StandardCharsets.UTF_8);
        assertThat(IngestionRunner.inputFingerprint(firstArgs))
                .isNotEqualTo(IngestionRunner.inputFingerprint(sameContentOtherPath));
    }

    @Test
    @DisplayName("원격 CSV는 기대 SHA-256을 fingerprint로 사용하고 누락·형식 오류를 거부한다")
    void fingerprintsRemoteSnapshotByExpectedDigest() throws Exception {
        String digestA = "a".repeat(64);
        String digestB = "b".repeat(64);
        var urlA = new DefaultApplicationArguments(
                "--ingest.source=public_toilet", "--ingest.url=https://example.test/a.csv",
                "--ingest.sha256=" + digestA);
        var urlB = new DefaultApplicationArguments(
                "--ingest.source=public_toilet", "--ingest.url=https://mirror.test/b.csv",
                "--ingest.sha256=" + digestA);
        var changedDigest = new DefaultApplicationArguments(
                "--ingest.source=public_toilet", "--ingest.url=https://example.test/a.csv",
                "--ingest.sha256=" + digestB);

        assertThat(IngestionRunner.inputFingerprint(urlA))
                .isEqualTo(IngestionRunner.inputFingerprint(urlB))
                .isNotEqualTo(IngestionRunner.inputFingerprint(changedDigest));
        assertThatThrownBy(() -> IngestionRunner.inputFingerprint(new DefaultApplicationArguments(
                "--ingest.source=public_toilet", "--ingest.url=https://example.test/a.csv")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ingest.sha256");
        assertThatThrownBy(() -> IngestionRunner.inputFingerprint(new DefaultApplicationArguments(
                "--ingest.source=public_toilet", "--ingest.url=https://example.test/a.csv",
                "--ingest.sha256=not-a-digest")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64자리");
    }

    @Test
    @DisplayName("원격 CSV는 사용 전에 실제 바이트 SHA-256이 일치해야 한다")
    void verifiesDownloadedSnapshotDigest() throws Exception {
        byte[] snapshot = "name,address\nA,Seoul\n".getBytes(StandardCharsets.UTF_8);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(snapshot));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/snapshot.csv", exchange -> {
            exchange.sendResponseHeaders(200, snapshot.length);
            try (var body = exchange.getResponseBody()) {
                body.write(snapshot);
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/snapshot.csv";

        try {
            Path downloaded = IngestionRunner.download(url, digest);
            try {
                assertThat(Files.readAllBytes(downloaded)).isEqualTo(snapshot);
            } finally {
                Files.deleteIfExists(downloaded);
            }
            assertThatThrownBy(() -> IngestionRunner.download(url, "0".repeat(64)))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("SHA-256")
                    .hasMessageNotContaining(url);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("로컬 CSV가 fingerprint 계산 뒤 교체되면 immutable snapshot 대조에서 실패한다")
    void rejectsLocalSnapshotTimeOfCheckTimeOfUseChange() throws Exception {
        Path source = tempDir.resolve("toilets.csv");
        Files.writeString(source, "name,address\nA,Seoul\n", StandardCharsets.UTF_8);
        UUID runId = UUID.randomUUID();
        when(ledger.start(eq("public_toilet_std"), anyString(), eq(IngestRunLedger.Trigger.MANUAL), eq(null)))
                .thenAnswer(invocation -> {
                    Files.writeString(source, "name,address\nB,Busan\n", StandardCharsets.UTF_8);
                    return runId;
                });
        doAnswer(invocation -> {
            Callable<Void> action = invocation.getArgument(0);
            return action.call();
        }).when(batchLock).runExclusive(any());

        var args = new DefaultApplicationArguments(
                "--ingest.source=public_toilet", "--ingest.file=" + source);

        assertThatThrownBy(() -> runner.run(args))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("fingerprint");
        verify(ingestionService, never()).ingest(any(), any(), any(), anyBoolean());
        verify(ledger).fail(eq(runId), any(java.io.IOException.class), anyLong());
    }
}
