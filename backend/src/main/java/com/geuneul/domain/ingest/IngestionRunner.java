package com.geuneul.domain.ingest;

import com.geuneul.domain.ingest.openapi.PublicLibraryIngestionService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * CLI 인제스천 트리거.
 *
 * CSV 소스(쉼터/화장실):
 *   ./gradlew bootRun --args='--ingest.source=public_toilet --ingest.file=/path/toilets.csv --ingest.charset=MS949'
 * JSON 오픈API 소스(도서관, ADR-0006) — 파일 불필요, 페이지네이션으로 전량 자체 수집:
 *   ./gradlew bootRun --args='--ingest.source=library --ingest.deactivate-stale=true'
 * 반경 오픈API 소스(상권정보 CAFE+STUDY_CAFE 동시, ADR-0006, 계약 검증 완료 TS-026):
 *   한 지역: ./gradlew bootRun --args='--ingest.source=stores --ingest.lat=37.4962 --ingest.lng=126.9573 --ingest.radius=1500'
 *   넓은 지역(bbox 격자, 한 실행으로 서울 등 커버):
 *     ./gradlew bootRun --args='--ingest.source=stores --ingest.bbox=126.76,37.42,127.18,37.70 --ingest.radius=1500'
 *   ingestRegion이 CAFE·STUDY_CAFE를 서버측 업종코드 필터로 함께 수집한다(soft-delete는 이 경로에 없음 — 클래스 주석 참고).
 *   레거시 별칭 study_cafe/cafe도 같은 핸들러로 라우팅한다.
 * 운영:  ECS one-off task(RunTask 커맨드 오버라이드)로 실행 — RDS가 프라이빗 서브넷이라
 *        로컬에서 직접 붙을 수 없고, 같은 VPC 안에서 돌리는 것이 정석 (DEPLOY.md §운영 인제스천).
 *        CSV 파일은 --ingest.url(GitHub Release 자산 등)로 받는다.
 * 옵션:  --ingest.exit-after=true → 인제스천 후 프로세스 종료(one-off task용, 기본 false=서버 유지).
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
    private static final String STORES_CLI_NAME = "stores";
    private static final String STUDY_CAFE_CLI_NAME = "study_cafe";
    private static final String CAFE_CLI_NAME = "cafe";

    private final IngestionService ingestionService;
    private final PublicLibraryIngestionService libraryIngestionService;
    private final StoreIngestionService storeIngestionService;
    private final IngestBatchLock batchLock;
    private final ConfigurableApplicationContext context;

    public IngestionRunner(IngestionService ingestionService,
                           PublicLibraryIngestionService libraryIngestionService,
                           StoreIngestionService storeIngestionService,
                           IngestBatchLock batchLock,
                           ConfigurableApplicationContext context) {
        this.ingestionService = ingestionService;
        this.libraryIngestionService = libraryIngestionService;
        this.storeIngestionService = storeIngestionService;
        this.batchLock = batchLock;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("ingest.source")) {
            return;
        }
        int exitCode = 0;
        try {
            boolean acquired = batchLock.runExclusive(() -> {
                dispatch(args);
                return null;
            });
            if (!acquired) {
                // IngestBatchLock이 이미 warn 로그를 남긴다 — 실패가 아니라 "건너뜀"이라 exitCode=0 유지.
                log.info("[ingest] 이번 실행은 건너뜀(동시 실행 방지) — exitCode=0으로 정상 종료 처리");
            }
        } catch (Exception e) {
            // RuntimeException(도메인 서비스)과 dispatch()의 체크 IOException(다운로드 실패 등) 둘 다 여기로 —
            // run()이 ApplicationRunner 계약대로 Exception을 던질 수 있어 굳이 종류별로 나눌 이유가 없다.
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

    private void dispatch(ApplicationArguments args) throws IOException {
        String source = first(args, "ingest.source");
        boolean deactivateStale = args.containsOption("ingest.deactivate-stale")
                && "true".equalsIgnoreCase(first(args, "ingest.deactivate-stale"));

        if (LIBRARY_CLI_NAME.equals(source)) {
            // JSON 오픈API 경로 — 파일/URL 불필요, 서비스가 자체 페이지네이션으로 전량 수집(ADR-0006).
            // P3 무인 스케줄 대상: serviceKey(DATA_GO_KR_SERVICE_KEY)만으로 다운로드까지 자족 실행.
            libraryIngestionService.ingestAll(deactivateStale);
        } else if (STORES_CLI_NAME.equals(source) || STUDY_CAFE_CLI_NAME.equals(source)
                || CAFE_CLI_NAME.equals(source)) {
            // 반경/격자 오픈API 경로 — CAFE+STUDY_CAFE 동시 수집, soft-delete 미지원(StoreIngestionService 주석).
            int radius = Integer.parseInt(require(args, "ingest.radius"));
            if (args.containsOption("ingest.bbox")) {
                double[] bbox = parseBbox(require(args, "ingest.bbox"));
                storeIngestionService.ingestArea(bbox[0], bbox[1], bbox[2], bbox[3], radius);
            } else {
                double lat = Double.parseDouble(require(args, "ingest.lat"));
                double lng = Double.parseDouble(require(args, "ingest.lng"));
                storeIngestionService.ingestRegion(lat, lng, radius);
            }
        } else {
            SourceSpec spec = SourceSpec.fromCliName(source);
            Charset charset = Charset.forName(args.containsOption("ingest.charset")
                    ? first(args, "ingest.charset") : "UTF-8");
            Path file = resolveFile(args);
            ingestionService.ingest(spec, file, charset, deactivateStale);
        }
    }

    private Path resolveFile(ApplicationArguments args) throws IOException {
        if (args.containsOption("ingest.file")) {
            return Path.of(first(args, "ingest.file"));
        }
        if (args.containsOption("ingest.url")) {
            return download(first(args, "ingest.url"));
        }
        throw new IllegalArgumentException("--ingest.file=<경로> 또는 --ingest.url=<주소>가 필요합니다");
    }

    /** 데이터 스냅샷 URL(GitHub Release 자산 등)을 임시파일로 내려받는다. */
    private static Path download(String url) throws IOException {
        log.info("[ingest] 다운로드: {}", url);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        try {
            HttpResponse<InputStream> response = client.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("다운로드 실패 HTTP " + response.statusCode() + ": " + url);
            }
            Path tmp = Files.createTempFile("ingest-", ".csv");
            try (InputStream in = response.body()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("[ingest] 다운로드 완료: {} ({} bytes)", tmp, Files.size(tmp));
            return tmp;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("다운로드 중단", e));
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
