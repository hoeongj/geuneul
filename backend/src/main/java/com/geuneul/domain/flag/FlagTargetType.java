package com.geuneul.domain.flag;

/**
 * 신고 대상 종류 (docs/SPEC.md §8 flags.target_type). 제보(휘발성)·후기(영구 평판) 둘 다 허위/명예훼손
 * 신고 대상이 될 수 있다 — §0-7. 다형 참조라 강제 FK 대신 이 enum + target_id로 대상을 가리킨다.
 */
public enum FlagTargetType {
    REPORT,
    REVIEW
}
