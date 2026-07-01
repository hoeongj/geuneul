package com.geuneul.domain.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * CLI 인제스천 트리거 — 앱 인자로 실행한다:
 *
 *   ./gradlew bootRun --args='--ingest.source=cooling_shelter --ingest.file=/path/무더위쉼터.csv [--ingest.charset=MS949]'
 *
 * 별도 배치 앱 대신 ApplicationRunner를 쓴 이유: 소스 하나(P1)에 배치 프레임워크는 과설계.
 * 소스가 늘어나 스케줄/재시도가 필요해지는 시점(P3+)에 Spring Batch 등을 재검토한다.
 */
@Component
public class IngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionRunner.class);

    private final IngestionService ingestionService;

    public IngestionRunner(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("ingest.source")) {
            return;
        }
        String source = first(args, "ingest.source");
        String file = first(args, "ingest.file");
        if (file == null) {
            throw new IllegalArgumentException("--ingest.file=<csv 경로>가 필요합니다");
        }
        Charset charset = Charset.forName(args.containsOption("ingest.charset")
                ? first(args, "ingest.charset") : "UTF-8");

        switch (source) {
            case "cooling_shelter" -> ingestionService.ingestCoolingShelters(Path.of(file), charset);
            default -> throw new IllegalArgumentException("지원하지 않는 ingest.source: " + source
                    + " (지원: cooling_shelter)");
        }
        log.info("[ingest] 완료 — 앱은 계속 기동 상태를 유지합니다 (서버 모드 겸용)");
    }

    private static String first(ApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
