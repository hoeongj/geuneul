package com.geuneul.domain.ingest;

import com.geuneul.domain.place.PlaceCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * 공공데이터 인제스천 오케스트레이션. 소스별 파서 → 멱등 배치 upsert → 통계 로그.
 * 전체를 한 트랜잭션으로 묶어 "파일 절반만 반영" 상태를 방지한다(실패 시 전체 롤백 후 재실행).
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    public static final String SOURCE_COOLING_SHELTER = "cooling_shelter_std";

    private final CoolingShelterCsvParser coolingShelterCsvParser;
    private final PlaceBulkUpsertRepository upsertRepository;

    public IngestionService(CoolingShelterCsvParser coolingShelterCsvParser,
                            PlaceBulkUpsertRepository upsertRepository) {
        this.coolingShelterCsvParser = coolingShelterCsvParser;
        this.upsertRepository = upsertRepository;
    }

    public record Report(String source, int totalRecords, int upserted, int skippedNoCoords, long tookMs) {
    }

    @Transactional
    public Report ingestCoolingShelters(Path csvFile, Charset charset) {
        long start = System.currentTimeMillis();
        CoolingShelterCsvParser.Result parsed;
        try {
            parsed = coolingShelterCsvParser.parse(csvFile, charset);
        } catch (IOException e) {
            throw new UncheckedIOException("무더위쉼터 CSV 읽기 실패: " + csvFile, e);
        }
        int upserted = upsertRepository.upsertShelters(
                parsed.rows(), SOURCE_COOLING_SHELTER, PlaceCategory.COOLING_SHELTER);

        Report report = new Report(SOURCE_COOLING_SHELTER, parsed.totalRecords(), upserted,
                parsed.skippedNoCoords(), System.currentTimeMillis() - start);
        log.info("[ingest] source={} total={} upserted={} skippedNoCoords={} took={}ms",
                report.source(), report.totalRecords(), report.upserted(), report.skippedNoCoords(), report.tookMs());
        return report;
    }
}
