package com.geuneul.domain.ingest;

import com.geuneul.domain.place.PlaceCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 공공데이터 대량 upsert — JPA save() 대신 JDBC 배치를 쓴다 (ADR-0002).
 *
 * - 멱등성: ON CONFLICT (source, source_external_id) DO UPDATE → 같은 파일을 몇 번 넣어도 중복 0.
 * - 성능: 수만 행(공중화장실 60k)을 dirty-checking 없이 배치 INSERT.
 * - geom은 DB에서 ST_SetSRID(ST_MakePoint(lng,lat),4326)로 생성.
 * - geocoded 플래그: 원본 좌표=false, 지오코딩 산출=true — 재적재 시 좌표 재사용 판단에 쓴다 (ADR-0003).
 * - is_commercial: PlaceCategory.commercial()에서 파생 — 카테고리가 결정하는 값이라 입력값 없이 upsert가 채운다(ADR-0006).
 * - deleted_at: 재적재로 스냅샷에 다시 나타난 행은 DO UPDATE가 NULL로 되돌린다("부활" — 폐업 재개업 등).
 */
@Repository
public class PlaceBulkUpsertRepository {

    private static final Logger log = LoggerFactory.getLogger(PlaceBulkUpsertRepository.class);

    private static final String UPSERT_SQL = """
            INSERT INTO places (name, category, address, geom, source, source_external_id, geocoded, is_commercial)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, ?, ?)
            ON CONFLICT (source, source_external_id) DO UPDATE SET
              name = EXCLUDED.name,
              category = EXCLUDED.category,
              address = EXCLUDED.address,
              geom = EXCLUDED.geom,
              geocoded = EXCLUDED.geocoded,
              is_commercial = EXCLUDED.is_commercial,
              deleted_at = NULL,
              updated_at = now()
            """;

    private static final int BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbcTemplate;

    public PlaceBulkUpsertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return upsert된 행 수. batchUpdate가 BATCH_SIZE 단위로 내부 청킹한다. */
    @Transactional
    public int upsertPlaces(List<PlaceRow> rows, String source, PlaceCategory category, boolean geocoded) {
        int[][] results = jdbcTemplate.batchUpdate(UPSERT_SQL, rows, BATCH_SIZE, (ps, row) -> {
            ps.setString(1, row.name());
            ps.setString(2, category.name());
            ps.setString(3, row.address());
            ps.setDouble(4, row.lng()); // ST_MakePoint(x=lng, y=lat) — 순서 주의
            ps.setDouble(5, row.lat());
            ps.setString(6, source);
            ps.setString(7, row.externalId());
            ps.setBoolean(8, geocoded);
            ps.setBoolean(9, category.commercial());
        });
        int affected = 0;
        for (int[] batch : results) {
            for (int r : batch) {
                affected += Math.max(r, 0);
            }
        }
        return affected;
    }

    /**
     * 지오코딩 재사용 판단용 — 이미 지오코딩된 행의 (external_id → 주소) 맵.
     * 주소가 안 바뀐 행은 재지오코딩하지 않는다(멱등 + rate limit 회피, ADR-0003).
     */
    public Map<String, String> findGeocodedAddresses(String source) {
        Map<String, String> map = new HashMap<>();
        jdbcTemplate.query(
                "SELECT source_external_id, address FROM places WHERE source = ? AND geocoded = true",
                rs -> {
                    map.put(rs.getString(1), rs.getString(2));
                },
                source);
        return map;
    }

    /**
     * 스냅샷에서 사라진 행 soft-delete (ADR-0006, 로드맵 P3 "스냅샷 diff 비활성화" 훅).
     *
     * 이번 인제스천 실행이 파싱한 전체 external_id 집합({@code currentExternalIds})에 없는
     * 기존 활성 행(같은 source, deleted_at IS NULL)을 단일 set-based UPDATE로 비활성화한다.
     * 지오코딩 실패로 upsert되지 못한 행도 currentExternalIds에는 포함되어야 한다("소스에는 있지만
     * 좌표를 못 구한" 것과 "소스에서 아예 사라진" 것을 구분 — 후자만 soft-delete 대상).
     *
     * <p><b>안전장치</b>: currentExternalIds가 비어 있으면 아무것도 하지 않는다 — 파싱 실패·부분 파일
     * 재적재로 스냅샷이 통째로 비면, 그걸 "전부 사라짐"으로 오독해 전체 소스를 비활성화하는 사고를 막는다.
     * 호출부(IngestionService)도 opt-in 플래그로만 호출한다 — 부분 샘플 파일(현재 쉼터/화장실 재실행 등)이
     * 실수로 나머지 데이터를 지우지 않도록 기본은 비활성.
     *
     * @return 이번 호출로 새로 비활성화된 행 수.
     */
    @Transactional
    public int deactivateStale(String source, Set<String> currentExternalIds) {
        if (currentExternalIds.isEmpty()) {
            log.warn("[ingest] deactivateStale 스킵 — source={} 스냅샷이 비어 있어 전체 비활성화 사고 방지", source);
            return 0;
        }
        String[] ids = currentExternalIds.toArray(new String[0]);
        int affected = jdbcTemplate.update(
                """
                UPDATE places SET deleted_at = now(), updated_at = now()
                WHERE source = ? AND deleted_at IS NULL AND NOT (source_external_id = ANY (?))
                """,
                ps -> {
                    ps.setString(1, source);
                    ps.setArray(2, ps.getConnection().createArrayOf("text", ids));
                });
        if (affected > 0) {
            log.info("[ingest] deactivateStale source={} 비활성화 {}건 (스냅샷 {}건)", source, affected, ids.length);
        }
        return affected;
    }

    /**
     * 신규 카테고리(LIBRARY/STUDY_CAFE)의 "공부 가능" 기본 feature 백필(ADR-0006) — set-based, 멱등.
     * 이 소스로 upsert된 장소(source + external_id 집합)에 낮은 confidence의 PUBLIC feature를 심는다.
     * {@code ON CONFLICT (place_id, feature_type) DO NOTHING} — 이미 UGC(제보/후기)가 값을 채웠으면
     * 덮어쓰지 않는다(자동 백필이 유저 신호를 밀어내지 않게).
     */
    @Transactional
    public int backfillFeatures(String source, Set<String> externalIds, List<FeatureSpec> features) {
        if (externalIds.isEmpty() || features.isEmpty()) {
            return 0;
        }
        String[] ids = externalIds.toArray(new String[0]);
        int total = 0;
        for (FeatureSpec f : features) {
            total += jdbcTemplate.update(
                    """
                    INSERT INTO place_features (place_id, feature_type, value, source, confidence)
                    SELECT id, ?, ?, 'PUBLIC', ?
                    FROM places
                    WHERE source = ? AND source_external_id = ANY (?) AND deleted_at IS NULL
                    ON CONFLICT (place_id, feature_type) DO NOTHING
                    """,
                    ps -> {
                        ps.setString(1, f.featureType());
                        ps.setString(2, f.value());
                        ps.setDouble(3, f.confidence());
                        ps.setString(4, source);
                        ps.setArray(5, ps.getConnection().createArrayOf("text", ids));
                    });
        }
        return total;
    }
}
