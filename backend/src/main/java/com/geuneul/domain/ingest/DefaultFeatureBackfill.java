package com.geuneul.domain.ingest;

import com.geuneul.domain.place.PlaceCategory;

import java.util.List;
import java.util.Map;

/**
 * 카테고리별 인제스천 기본 feature 백필 규칙(ADR-0006 §3): "카페 study_ok = 전량 적재 + UGC 태깅"의
 * 반대편 — 독서실/스터디카페는 그 자체가 카테고리 정의이므로 낮은 confidence의 study_ok/quiet를
 * 자동 부여해 콜드스타트에서도 필터가 바로 동작하게 한다. CAFE는 공공데이터에 '공부 가능' 기준이
 * 없어 의도적으로 비워 UGC(제보/후기)로만 채운다.
 *
 * <p><b>LIBRARY는 여기 없다</b> — 전국도서관표준 오픈API(실측 확인, tn_pubr_public_lbrry_api)가
 * 레코드마다 열람좌석수(seatCo)를 직접 제공해, ADR-0006 원안 그대로 "열람좌석수&gt;0"인 개별 도서관만
 * 정밀 백필할 수 있다(균일 카테고리 규칙이 아니라 레코드 조건부) → {@code PublicLibraryIngestionService}가
 * 이 클래스를 거치지 않고 자체적으로 처리한다.
 */
public final class DefaultFeatureBackfill {

    private static final Map<PlaceCategory, List<FeatureSpec>> RULES = Map.of(
            PlaceCategory.STUDY_CAFE, List.of(
                    new FeatureSpec("study_ok", "true", 0.6),
                    new FeatureSpec("quiet", "true", 0.5)));

    private DefaultFeatureBackfill() {
    }

    public static List<FeatureSpec> forCategory(PlaceCategory category) {
        return RULES.getOrDefault(category, List.of());
    }
}
