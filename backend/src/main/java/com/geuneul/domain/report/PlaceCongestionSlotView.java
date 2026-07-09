package com.geuneul.domain.report;

/**
 * 시간대별 혼잡 파생(자체 popular-times, ADR-0005 §④) — 한 장소의 (요일×시간) 슬롯 집계 투영.
 * reports 이력을 KST 기준 요일(0=일~6=토)·시간(0~23)으로 묶은 결과(ReportRepository의 네이티브 쿼리).
 *
 * <p>스코어링(place_report_signals)과 달리 <b>만료 제보도 포함</b>한다 — 급증/최신성은 "지금"을 보지만
 * 혼잡 패턴은 "과거 이력의 요일×시간 분포"를 채굴하기 때문이다(휘발성 규약은 스코어에만 적용, 이력은 살아있다).
 */
public interface PlaceCongestionSlotView {
    /** 요일: 0=일 ... 6=토 (KST). */
    int getDow();

    /** 시간: 0~23 (KST). */
    int getHour();

    /** 이 슬롯의 총 제보 수(활동량 신호). */
    long getSampleCount();

    /** 이 슬롯의 CROWDED('붐벼요') 제보 수. */
    long getCrowdedCount();

    /** 이 슬롯의 SEAT_OK('자리 있어요') 제보 수. */
    long getSeatOkCount();
}
