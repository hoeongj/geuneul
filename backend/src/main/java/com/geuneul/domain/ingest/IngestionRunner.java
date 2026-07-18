package com.geuneul.domain.ingest;

import com.geuneul.domain.ingest.openapi.PublicLibraryIngestionService;
import com.geuneul.domain.ingest.safetydata.ShelterIngestionService;
import com.geuneul.domain.ingest.storeapi.StoreIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CLI 인제스천 트리거.
 *
 * CSV 소스(쉼터/화장실):
 *   ./gradlew bootRun --args='--ingest.source=public_toilet --ingest.file=/path/toilets.csv --ingest.charset=MS949'
 * JSON 오픈API 소스(도서관, ADR-0006) — 파일 불필요, 페이지네이션으로 전량 자체 수집:
 *   ./gradlew bootRun --args='--ingest.source=library --ingest.deactivate-stale=true'
 * JSON 오픈API 소스(무더위쉼터, safetydata.go.kr, TS-027) — SAFETYDATA_SERVICE_KEY 필요(data.go.kr 키와 별개):
 *   ./gradlew bootRun --args='--ingest.source=shelter --ingest.deactivate-stale=true'
 *   deactivate-stale은 전량 수집 완료 시에만 적용(기존 100건 샘플을 실데이터로 대체).
 * 반경 오픈API 소스(상권정보 CAFE+STUDY_CAFE 동시, ADR-0006, 계약 검증 완료 TS-026):
 *   한 지역: ./gradlew bootRun --args='--ingest.source=stores --ingest.lat=37.4962 --ingest.lng=126.9573 --ingest.radius=1500'
 *   넓은 지역(bbox 격자, 한 실행으로 서울 등 커버):
 *     ./gradlew bootRun --args='--ingest.source=stores --ingest.bbox=126.76,37.42,127.18,37.70 --ingest.radius=1500'
 *   ingestRegion이 CAFE·STUDY_CAFE를 서버측 업종코드 필터로 함께 수집한다(soft-delete는 이 경로에 없음 — 클래스 주석 참고).
 *   레거시 별칭 study_cafe/cafe도 같은 핸들러로 라우팅한다.
 * 운영:  ECS one-off task(RunTask 커맨드 오버라이드)로 실행 — RDS가 프라이빗 서브넷이라
 *        로컬에서 직접 붙을 수 없고, 같은 VPC 안에서 돌리는 것이 정석 (DEPLOY.md §운영 인제스천).
 *        CSV 파일은 --ingest.url(GitHub Release 자산 등)과 --ingest.sha256으로 받아 사용 전에 검증한다.
 * 옵션:  --ingest.exit-after=true → 인제스천 후 프로세스 종료(one-off task용, 기본 false=서버 유지).
 *        --ingest.trigger=manual|scheduled|backfill → 운영 원장 실행 경로(기본 manual, ADR-0030).
 *        --ingest.retry-of=&lt;UUID&gt; → 실패/부분 실행을 다시 돌릴 때 이전 실행과 계보 연결.
 *        --ingest.sha256=&lt;64자리 hex&gt; → 원격 CSV 스냅샷의 기대 digest(URL 입력에서 필수).
 *        --ingest.deactivate-stale=true → 스냅샷에서 사라진 기존 행을 soft-delete(ADR-0006).
 *        전량 스냅샷 소스(도서관 등)에서만 켠다 — 부분 파일 재실행 소스(쉼터 샘플 등)는 기본(false) 유지.
 *
 * 무인 스케줄(P3, EventBridge Scheduler → ECS RunTask): 매 실행은 {@link IngestBatchLock}으로
 * Postgres advisory lock을 먼저 얻는다 — 스케줄이 겹치거나 사람이 수동 실행과 동시에 돌려도
 * 나중 실행은 논블로킹으로 즉시 포기(exitCode=0, "건너뜀")하고 다음 스케줄을 기다린다.
 */
@Component
public class IngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionRunner.class);
    private static final String LIBRARY_CLI_NAME = "library";
    private static final String SHELTER_CLI_NAME = "shelter";
    private static final String STORES_CLI_NAME = "stores";
    private static final String STUDY_CAFE_CLI_NAME = "study_cafe";
    private static final String CAFE_CLI_NAME = "cafe";
    private static final String INPUT_CONTRACT_VERSION = "ingest-input-v2";

    private final IngestionService ingestionService;
    private final PublicLibraryIngestionService libraryIngestionService;
    private final StoreIngestionService storeIngestionService;
    private final ShelterIngestionService shelterIngestionService;
    private final IngestBatchLock batchLock;
    private final IngestRunLedger runLedger;
    private final ConfigurableApplicationContext context;

    public IngestionRunner(IngestionService ingestionService,
                           PublicLibraryIngestionService libraryIngestionService,
                           StoreIngestionService storeIngestionService,
                           ShelterIngestionService shelterIngestionService,
                           IngestBatchLock batchLock,
                           IngestRunLedger runLedger,
                           ConfigurableApplicationContext context) {
        this.ingestionService = ingestionService;
        this.libraryIngestionService = libraryIngestionService;
        this.storeIngestionService = storeIngestionService;
        this.shelterIngestionService = shelterIngestionService;
        this.batchLock = batchLock;
        this.runLedger = runLedger;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("ingest.source")) {
            return;
        }
        int exitCode = 0;
        UUID runId = null;
        long startedNanos = System.nanoTime();
        try {
            String source = canonicalSource(first(args, "ingest.source"));
            IngestRunLedger.Trigger trigger = IngestRunLedger.Trigger.parse(first(args, "ingest.trigger"));
            UUID retryOf = optionalUuid(first(args, "ingest.retry-of"));
            String inputFingerprint = inputFingerprint(args);
            runId = runLedger.start(source, inputFingerprint, trigger, retryOf);
            UUID activeRunId = runId;
            AtomicReference<IngestRunResult> result = new AtomicReference<>();
            boolean acquired = batchLock.runExclusive(() -> {
                result.set(dispatch(args, inputFingerprint));
                return null;
            });
            if (!acquired) {
                // IngestBatchLock이 이미 warn 로그를 남긴다 — 실패가 아니라 "건너뜀"이라 exitCode=0 유지.
                runLedger.skip(activeRunId, elapsedMs(startedNanos));
                log.info("[ingest] 이번 실행은 건너뜀(동시 실행 방지) — exitCode=0으로 정상 종료 처리");
            } else {
                runLedger.complete(activeRunId, result.get(), elapsedMs(startedNanos));
            }
        } catch (Exception e) {
            // RuntimeException(도메인 서비스)과 dispatch()의 체크 IOException(다운로드 실패 등) 둘 다 여기로 —
            // run()이 ApplicationRunner 계약대로 Exception을 던질 수 있어 굳이 종류별로 나눌 이유가 없다.
            if (runId != null) {
                try {
                    runLedger.fail(runId, e, elapsedMs(startedNanos));
                } catch (RuntimeException ledgerFailure) {
                    e.addSuppressed(ledgerFailure);
                }
            }
            log.error("[ingest] 실패", e);
            exitCode = 1;
            if (!exitRequested(args)) {
                throw e;
            }
        }
        if (exitRequested(args)) {
            log.info("[ingest] exit-after=true → 종료(code={})", exitCode);
            int code = exitCode;
            System.exit(SpringApplication.exit(context, () -> code));
        }
    }

    private IngestRunResult dispatch(ApplicationArguments args, String expectedInputFingerprint) throws IOException {
        String source = first(args, "ingest.source");
        boolean deactivateStale = args.containsOption("ingest.deactivate-stale")
                && "true".equalsIgnoreCase(first(args, "ingest.deactivate-stale"));

        if (LIBRARY_CLI_NAME.equals(source)) {
            // JSON 오픈API 경로 — 파일/URL 불필요, 서비스가 자체 페이지네이션으로 전량 수집(ADR-0006).
            // P3 무인 스케줄 대상: serviceKey(DATA_GO_KR_SERVICE_KEY)만으로 다운로드까지 자족 실행.
            return IngestRunResult.from(libraryIngestionService.ingestAll(deactivateStale));
        } else if (SHELTER_CLI_NAME.equals(source)) {
            // safetydata.go.kr 무더위쉼터 JSON 오픈API — 전국 페이지네이션 전량 수집(TS-027).
            // 키는 SAFETYDATA_SERVICE_KEY(data.go.kr 키와 별개). deactivate-stale은 전량 수집 완료 시에만
            // 실제 적용(부분 수집 사고 방지, ShelterIngestionService).
            return IngestRunResult.from(shelterIngestionService.ingestAll(deactivateStale));
        } else if (STORES_CLI_NAME.equals(source) || STUDY_CAFE_CLI_NAME.equals(source)
                || CAFE_CLI_NAME.equals(source)) {
            // 반경/격자 오픈API 경로 — CAFE+STUDY_CAFE 동시 수집, soft-delete 미지원(StoreIngestionService 주석).
            int radius = Integer.parseInt(require(args, "ingest.radius"));
            if (args.containsOption("ingest.bbox")) {
                double[] bbox = parseBbox(require(args, "ingest.bbox"));
                return IngestRunResult.from(
                        storeIngestionService.ingestArea(bbox[0], bbox[1], bbox[2], bbox[3], radius));
            } else {
                double lat = Double.parseDouble(require(args, "ingest.lat"));
                double lng = Double.parseDouble(require(args, "ingest.lng"));
                return IngestRunResult.from(storeIngestionService.ingestRegion(lat, lng, radius));
            }
        } else {
            SourceSpec spec = SourceSpec.fromCliName(source);
            Charset charset = Charset.forName(args.containsOption("ingest.charset")
                    ? first(args, "ingest.charset") : "UTF-8");
            Path file = resolveFile(args);
            try {
                if (!expectedInputFingerprint.equals(inputFingerprint(args, file))) {
                    throw new IOException("CSV snapshot이 fingerprint 계산 후 변경되었습니다");
                }
                return IngestRunResult.from(ingestionService.ingest(spec, file, charset, deactivateStale));
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }

    private static String canonicalSource(String source) {
        if (LIBRARY_CLI_NAME.equals(source)) {
            return PublicLibraryIngestionService.SOURCE_KEY;
        }
        if (SHELTER_CLI_NAME.equals(source)) {
            return SourceSpec.COOLING_SHELTER.sourceKey();
        }
        if (STORES_CLI_NAME.equals(source) || STUDY_CAFE_CLI_NAME.equals(source) || CAFE_CLI_NAME.equals(source)) {
            return "stores_api";
        }
        return SourceSpec.fromCliName(source).sourceKey();
    }

    /**
     * retry 계보가 같은 입력 조건만 잇도록 CLI 의미와 CSV 스냅샷 내용을 SHA-256으로 정규화한다.
     * URL·파일 경로·좌표는 원장에 원문으로 남기지 않고 이 digest만 저장한다.
     */
    static String inputFingerprint(ApplicationArguments args) throws IOException {
        return inputFingerprint(args, null);
    }

    private static String inputFingerprint(ApplicationArguments args, Path preparedSnapshot) throws IOException {
        String cliSource = first(args, "ingest.source");
        String source = canonicalSource(cliSource);
        boolean deactivateStale = args.containsOption("ingest.deactivate-stale")
                && "true".equalsIgnoreCase(first(args, "ingest.deactivate-stale"));
        StringBuilder material = new StringBuilder()
                .append("contract=").append(INPUT_CONTRACT_VERSION)
                .append('\n').append("source=").append(source)
                .append('\n').append("deactivateStale=").append(deactivateStale);

        if (STORES_CLI_NAME.equals(cliSource) || STUDY_CAFE_CLI_NAME.equals(cliSource)
                || CAFE_CLI_NAME.equals(cliSource)) {
            material.append('\n').append("radius=")
                    .append(Integer.parseInt(require(args, "ingest.radius")));
            if (args.containsOption("ingest.bbox")) {
                double[] bbox = parseBbox(require(args, "ingest.bbox"));
                material.append('\n').append("mode=bbox");
                for (double coordinate : bbox) {
                    material.append('\n').append(Double.toString(coordinate));
                }
            } else {
                material.append('\n').append("mode=point")
                        .append('\n').append(Double.toString(Double.parseDouble(require(args, "ingest.lat"))))
                        .append('\n').append(Double.toString(Double.parseDouble(require(args, "ingest.lng"))));
            }
        } else if (!LIBRARY_CLI_NAME.equals(cliSource) && !SHELTER_CLI_NAME.equals(cliSource)) {
            material.append('\n').append("charset=")
                    .append(args.containsOption("ingest.charset") ? first(args, "ingest.charset") : "UTF-8");
            if (preparedSnapshot != null) {
                material.append('\n').append("contentSha256=")
                        .append(sha256(preparedSnapshot));
            } else if (args.containsOption("ingest.file")) {
                material.append('\n').append("contentSha256=")
                        .append(sha256(Path.of(require(args, "ingest.file"))));
            } else {
                material.append('\n').append("contentSha256=")
                        .append(requireSha256(args));
            }
        }
        return sha256(material.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static UUID optionalUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--ingest.retry-of는 UUID 형식이어야 합니다", e);
        }
    }

    private static long elapsedMs(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private Path resolveFile(ApplicationArguments args) throws IOException {
        if (args.containsOption("ingest.file")) {
            Path snapshot = Files.createTempFile("ingest-", ".csv");
            try {
                Files.copy(Path.of(first(args, "ingest.file")), snapshot, StandardCopyOption.REPLACE_EXISTING);
                return snapshot;
            } catch (IOException e) {
                Files.deleteIfExists(snapshot);
                throw e;
            }
        }
        if (args.containsOption("ingest.url")) {
            return download(first(args, "ingest.url"), requireSha256(args));
        }
        throw new IllegalArgumentException("--ingest.file=<경로> 또는 --ingest.url=<주소>가 필요합니다");
    }

    /** 기대 SHA-256이 고정된 데이터 스냅샷을 임시파일로 내려받고 사용 전에 검증한다. */
    static Path download(String url, String expectedSha256) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--ingest.url은 유효한 HTTP(S) 주소여야 합니다");
        }
        log.info("[ingest] 검증된 스냅샷 다운로드 시작");
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        Path tmp = null;
        try {
            HttpResponse<InputStream> response = client.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("스냅샷 다운로드 실패 HTTP " + response.statusCode());
                }
                tmp = Files.createTempFile("ingest-", ".csv");
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!expectedSha256.equals(sha256(tmp))) {
                Files.deleteIfExists(tmp);
                throw new IOException("스냅샷 SHA-256 검증 실패");
            }
            log.info("[ingest] 스냅샷 다운로드 및 SHA-256 검증 완료 ({} bytes)", Files.size(tmp));
            return tmp;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("스냅샷 다운로드 중단"));
        } catch (IOException e) {
            if (tmp != null) {
                Files.deleteIfExists(tmp);
            }
            log.warn("[ingest] 스냅샷 다운로드 실패 type={}", e.getClass().getSimpleName());
            String message = e.getMessage();
            boolean knownSafeMessage = "스냅샷 SHA-256 검증 실패".equals(message)
                    || (message != null && message.matches("스냅샷 다운로드 실패 HTTP [0-9]{3}"));
            throw new IOException(knownSafeMessage ? message : "스냅샷 다운로드 I/O 오류");
        }
    }

    private static String requireSha256(ApplicationArguments args) {
        String value = require(args, "ingest.sha256").trim().toLowerCase();
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("--ingest.sha256는 64자리 SHA-256 hex여야 합니다");
        }
        return value;
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = in.read(buffer)) != -1; ) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(byte[] material) {
        return HexFormat.of().formatHex(sha256Digest().digest(material));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);
        }
    }

    private static boolean exitRequested(ApplicationArguments args) {
        return args.containsOption("ingest.exit-after")
                && "true".equalsIgnoreCase(first(args, "ingest.exit-after"));
    }

    private static String first(ApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static String require(ApplicationArguments args, String name) {
        String value = first(args, name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("--" + name + "=<값>이 필요합니다");
        }
        return value;
    }

    /** "minLng,minLat,maxLng,maxLat" → [minLng, minLat, maxLng, maxLat]. */
    private static double[] parseBbox(String raw) {
        String[] parts = raw.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException(
                    "--ingest.bbox=minLng,minLat,maxLng,maxLat 형식이어야 합니다 (4개 값): " + raw);
        }
        double minLng = Double.parseDouble(parts[0].trim());
        double minLat = Double.parseDouble(parts[1].trim());
        double maxLng = Double.parseDouble(parts[2].trim());
        double maxLat = Double.parseDouble(parts[3].trim());
        if (minLng >= maxLng || minLat >= maxLat) {
            throw new IllegalArgumentException("--ingest.bbox min이 max보다 작아야 합니다: " + raw);
        }
        return new double[] {minLng, minLat, maxLng, maxLat};
    }
}
