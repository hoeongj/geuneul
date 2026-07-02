package com.geuneul.domain.ingest;

import com.geuneul.domain.place.PlaceCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 공공데이터 대량 upsert — JPA save() 대신 JDBC 배치를 쓴다 (ADR-0002).
 *
 * - 멱등성: ON CONFLICT (source, source_external_id) DO UPDATE → 같은 파일을 몇 번 넣어도 중복 0.
 * - 성능: 수만 행(공중화장실 60k)을 dirty-checking 없이 배치 INSERT.
 * - geom은 DB에서 ST_SetSRID(ST_MakePoint(lng,lat),4326)로 생성.
 * - geocoded 플래그: 원본 좌표=false, 지오코딩 산출=true — 재적재 시 좌표 재사용 판단에 쓴다 (ADR-0003).
 */
@Repository
public class PlaceBulkUpsertRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO places (name, category, address, geom, source, source_external_id, geocoded)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, ?)
            ON CONFLICT (source, source_external_id) DO UPDATE SET
              name = EXCLUDED.name,
              category = EXCLUDED.category,
              address = EXCLUDED.address,
              geom = EXCLUDED.geom,
              geocoded = EXCLUDED.geocoded,
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
}
