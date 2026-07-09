package com.geuneul.domain.ingest;

/**
 * 인제스천이 자동 백필하는 place_features 1건(ADR-0006). value·confidence는 항상 낮은 신뢰도의
 * PUBLIC 소스로 심고, UGC(제보/후기)가 값을 채우면 그쪽이 우선한다(백필은 ON CONFLICT DO NOTHING).
 */
public record FeatureSpec(String featureType, String value, double confidence) {
}
