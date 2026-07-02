package com.geuneul.domain.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * 공공데이터 인제스천 오케스트레이션. 소스별 스펙 → 범용 파서 → 멱등 배치 upsert → 통계 로그.
 * 전체를 한 트랜잭션으로 묶어 "파일 절반만 반영" 상태를 방지한다(실패 시 전체 롤백 후 재실행).
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final StandardCsvParser parser;
    private final PlaceBulkUpsertRepository upsertRepository;

    public IngestionService(StandardCsvParser parser, PlaceBulkUpsertRepository upsertRepository) {
        this.parser = parser;
        this.upsertRepository = upsertRepository;
    }

    public record Report(String source, int totalRecords, int upserted, int skippedNoCoords, long tookMs) {
    }

    @Transactional
    public Report ingest(SourceSpec spec, Path csvFile, Charset charset) {
        long start = System.currentTimeMillis();
        StandardCsvParser.Result parsed;
        try {
            parsed = parser.parse(csvFile, charset, spec);
        } catch (IOException e) {
            throw new UncheckedIOException("CSV 읽기 실패(" + spec.cliName() + "): " + csvFile, e);
        }
        int upserted = upsertRepository.upsertPlaces(parsed.rows(), spec.sourceKey(), spec.category());

        Report report = new Report(spec.sourceKey(), parsed.totalRecords(), upserted,
                parsed.skippedNoCoords(), System.currentTimeMillis() - start);
        log.info("[ingest] source={} total={} upserted={} skippedNoCoords={} took={}ms",
                report.source(), report.totalRecords(), report.upserted(), report.skippedNoCoords(), report.tookMs());
        return report;
    }
}
