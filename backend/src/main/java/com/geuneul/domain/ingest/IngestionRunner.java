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
 * 반경 오픈API 소스(상권정보 STUDY_CAFE/CAFE, ADR-0006, ⚠️계약 미검증) — 지역 중심좌표+반경 필요:
 *   ./gradlew bootRun --args='--ingest.source=study_cafe --ingest.lat=37.4962 --ingest.lng=126.9573 --ingest.radius=2000'
 *   전국 커버리지는 prod-ingest 쪽이 격자 좌표를 순회해 여러 번 호출한다(soft-delete는 이 경로에 없음 — 클래스 주석 참고).
 * 운영:  ECS one-off task(RunTask 커맨드 오버라이드)로 실행 — RDS가 프라이빗 서브넷이라
 *        로컬에서 직접 붙을 수 없고, 같은 VPC 안에서 돌리는 것이 정석 (DEPLOY.md §운영 인제스천).
 *        CSV 파일은 --ingest.url(GitHub Release 자산 등)로 받는다.
 * 옵션:  --ingest.exit-after=true → 인제스천 후 프로세스 종료(one-off task용, 기본 false=서버 유지).
 *        --ingest.deactivate-stale=true → 스냅샷에서 사라진 기존 행을 soft-delete(ADR-0006).
 *        전량 스냅샷 소스(도서관 등)에서만 켠다 — 부분 파일 재실행 소스(쉼터 샘플 등)는 기본(false) 유지.
 */
@Component
public class IngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionRunner.class);
    private static final String LIBRARY_CLI_NAME = "library";
    private static final String STUDY_CAFE_CLI_NAME = "study_cafe";
    private static final String CAFE_CLI_NAME = "cafe";

    private final IngestionService ingestionService;
    private final PublicLibraryIngestionService libraryIngestionService;
    private final StoreIngestionService storeIngestionService;
    private final ConfigurableApplicationContext context;

    public IngestionRunner(IngestionService ingestionService,
                           PublicLibraryIngestionService libraryIngestionService,
                           StoreIngestionService storeIngestionService,
                           ConfigurableApplicationContext context) {
        this.ingestionService = ingestionService;
        this.libraryIngestionService = libraryIngestionService;
        this.storeIngestionService = storeIngestionService;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!args.containsOption("ingest.source")) {
            return;
        }
        int exitCode = 0;
        try {
            String source = first(args, "ingest.source");
            boolean deactivateStale = args.containsOption("ingest.deactivate-stale")
                    && "true".equalsIgnoreCase(first(args, "ingest.deactivate-stale"));

            if (LIBRARY_CLI_NAME.equals(source)) {
                // JSON 오픈API 경로 — 파일/URL 불필요, 서비스가 자체 페이지네이션으로 전량 수집(ADR-0006).
                libraryIngestionService.ingestAll(deactivateStale);
            } else if (STUDY_CAFE_CLI_NAME.equals(source) || CAFE_CLI_NAME.equals(source)) {
                // 반경 오픈API 경로 — 지역 중심좌표+반경 필요, soft-delete 미지원(StoreIngestionService 주석).
                double lat = Double.parseDouble(require(args, "ingest.lat"));
                double lng = Double.parseDouble(require(args, "ingest.lng"));
                int radius = Integer.parseInt(require(args, "ingest.radius"));
                storeIngestionService.ingestRegion(lat, lng, radius);
            } else {
                SourceSpec spec = SourceSpec.fromCliName(source);
                Charset charset = Charset.forName(args.containsOption("ingest.charset")
                        ? first(args, "ingest.charset") : "UTF-8");
                Path file = resolveFile(args);
                ingestionService.ingest(spec, file, charset, deactivateStale);
            }
        } catch (RuntimeException e) {
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
}
