"use client";

import { useMemo, useState } from "react";
import { usePopularTimes } from "@/lib/queries";
import type { CongestionLevel, PopularTimesSlot } from "@/types/popular";

const DOW_LABELS = ["일", "월", "화", "수", "목", "금", "토"];

// 혼잡 등급 색 — 발산형(diverging) 인코딩: 한산(자리 있음=좋음, teal) ↔ 붐빔(amber, §6 경고 적색 아님),
// 보통은 중립 회색. dataviz 검증(validate_palette): 두 극 CVD ΔE 19.8(>12 안전). 색만으로 구분하지 않게
// 범례 + 셀 aria/title 라벨을 함께 제공한다(대비 WARN 완화 요건 충족). 데이터 없음=surface에 가까운 faint.
const LEVEL_STYLE: Record<CongestionLevel, { bg: string; label: string }> = {
  QUIET: { bg: "#2E9E86", label: "한산" },
  MODERATE: { bg: "#C2C7CD", label: "보통" },
  BUSY: { bg: "#E0972E", label: "붐빔" },
  UNKNOWN: { bg: "#ECEAE4", label: "정보 없음" },
};

const HOUR_TICKS = [0, 6, 12, 18];

function kstNow(): { dow: number; hour: number } {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Seoul",
    weekday: "short",
    hour: "2-digit",
    hour12: false,
  }).formatToParts(new Date());
  const wd = parts.find((p) => p.type === "weekday")?.value ?? "Sun";
  const hourStr = parts.find((p) => p.type === "hour")?.value ?? "0";
  const dow = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].indexOf(wd);
  // 24시(자정)를 0으로 정규화 — 일부 런타임이 24를 낸다.
  const hour = parseInt(hourStr, 10) % 24;
  return { dow: dow < 0 ? 0 : dow, hour };
}

// 시간대별 혼잡 파생(자체 popular-times, A5 · ADR-0005 §④). 요일 선택 + 선택 요일의 24시간 혼잡 스트립.
// 데이터가 희소한 UGC라 7×24 히트맵(168칸, 대부분 공백)이 아니라 "하루치 시간 스트립"으로 압축한다
// — 모바일 상세에 맞고, 빈칸을 잔뜩 그리지 않는다(간판 안 가림 §0-9). 강도는 셀 색(히트맵 인코딩).
export function PopularTimes({ placeId }: { placeId: number }) {
  const { data, isLoading, isError } = usePopularTimes(placeId);
  const today = useMemo(() => kstNow(), []);
  const [selectedDow, setSelectedDow] = useState(today.dow);

  // (dow,hour) → slot 조회 맵 + 요일별 데이터 유무.
  const { byKey, daysWithData, total } = useMemo(() => {
    const byKey = new Map<string, PopularTimesSlot>();
    const daysWithData = new Set<number>();
    let total = 0;
    for (const s of data ?? []) {
      if (s.sampleCount > 0) {
        byKey.set(`${s.dow}-${s.hour}`, s);
        daysWithData.add(s.dow);
        total += s.sampleCount;
      }
    }
    return { byKey, daysWithData, total };
  }, [data]);

  // 로딩/에러/무데이터는 조용히 접는다(간판 우선 — 상세를 이 살 섹션으로 채우지 않는다).
  if (isLoading || isError || total === 0) {
    return null;
  }

  const hours = Array.from({ length: 24 }, (_, h) => h);

  return (
    <section className="rounded-[14px] border border-line-cream bg-white p-3.5">
      <div className="mb-2.5 flex items-center gap-2">
        <h3 className="text-[14px] font-extrabold text-ink-2">시간대별 혼잡</h3>
        <span className="rounded-full bg-mint-3 px-2 py-0.5 text-[10px] font-bold text-teal-deep">제보 이력</span>
      </div>

      {/* 요일 선택 — 데이터 있는 요일은 진하게, 없는 요일은 흐리게(선택은 가능) */}
      <div className="no-scrollbar mb-3 flex gap-1.5 overflow-x-auto">
        {DOW_LABELS.map((label, dow) => {
          const active = dow === selectedDow;
          const has = daysWithData.has(dow);
          return (
            <button
              key={dow}
              type="button"
              onClick={() => setSelectedDow(dow)}
              aria-pressed={active}
              className={
                "h-7 w-7 shrink-0 rounded-full text-[12px] font-bold " +
                (active
                  ? "bg-forest text-cream"
                  : has
                    ? "bg-mint-2 text-teal-deep"
                    : "bg-white text-muted-3")
              }
            >
              {label}
            </button>
          );
        })}
      </div>

      {/* 선택 요일 24시간 스트립 — 셀 색이 혼잡 강도(히트맵 인코딩) */}
      <div className="flex gap-[2px]" role="img" aria-label={`${DOW_LABELS[selectedDow]}요일 시간대별 혼잡`}>
        {hours.map((hour) => {
          const slot = byKey.get(`${selectedDow}-${hour}`);
          const level: CongestionLevel = slot?.level ?? "UNKNOWN";
          const style = LEVEL_STYLE[level];
          const isNow = selectedDow === today.dow && hour === today.hour;
          const count = slot?.sampleCount ?? 0;
          return (
            <div
              key={hour}
              title={`${hour}시 · ${style.label}${count > 0 ? ` (제보 ${count}건)` : ""}`}
              aria-label={`${hour}시 ${style.label}${count > 0 ? `, 제보 ${count}건` : ""}`}
              className={"h-8 flex-1 rounded-[3px]" + (isNow ? " ring-2 ring-forest ring-offset-1" : "")}
              style={{ background: style.bg }}
            />
          );
        })}
      </div>

      {/* 시간 눈금(0/6/12/18) */}
      <div className="mt-1 flex text-[9px] text-muted-3">
        {hours.map((hour) => (
          <span key={hour} className="flex-1 text-center">
            {HOUR_TICKS.includes(hour) ? hour : ""}
          </span>
        ))}
      </div>

      {/* 범례 — 색만으로 구분하지 않게(접근성) */}
      <div className="mt-2.5 flex items-center gap-3 text-[11px] text-muted">
        {(["QUIET", "MODERATE", "BUSY"] as const).map((lvl) => (
          <span key={lvl} className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-[2px]" style={{ background: LEVEL_STYLE[lvl].bg }} />
            {LEVEL_STYLE[lvl].label}
          </span>
        ))}
      </div>
    </section>
  );
}
