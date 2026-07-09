import type { IconName } from "@/lib/icon-paths";
import type { Category, FeatureType, Scenario } from "@/types/place";

// enum → 아이콘 이름(= icon-paths 키) + 라벨. 라벨은 API의 categoryLabel 을 우선 쓰되,
// 목록/필터처럼 응답이 없는 자리에서는 아래 fallback 라벨을 쓴다.
export const CATEGORY_META: Record<Category, { label: string; icon: IconName }> = {
  COOLING_SHELTER: { label: "무더위쉼터", icon: "snow" },
  TOILET: { label: "공중화장실", icon: "toilet" },
  WATER: { label: "음수대", icon: "droplet" },
  PARK: { label: "공원", icon: "tree" },
  LIBRARY: { label: "도서관", icon: "book" },
  CIVIC: { label: "공공기관", icon: "civic" },
  UNDERGROUND: { label: "지하상가", icon: "stairs" },
  CAFE: { label: "카페", icon: "seat" },
  STUDY_CAFE: { label: "스터디카페", icon: "pen" },
  ETC: { label: "기타", icon: "dots" },
};

export function iconForCategory(cat: Category): IconName {
  return CATEGORY_META[cat]?.icon ?? "dots";
}

export function categoryLabel(cat: Category, apiLabel?: string): string {
  return apiLabel || CATEGORY_META[cat]?.label || "기타";
}

// 홈 지도 필터 칩 순서(전체 + 카테고리 7종). ETC 는 칩에 노출하지 않음(프로토타입과 동일).
export const FILTER_CATEGORIES: Category[] = [
  "COOLING_SHELTER",
  "TOILET",
  "WATER",
  "PARK",
  "LIBRARY",
  "CIVIC",
  "UNDERGROUND",
  "CAFE",
  "STUDY_CAFE",
];

export const FEATURE_META: Record<FeatureType, { label: string; icon: IconName }> = {
  air_conditioned: { label: "냉방", icon: "snow" },
  outlet: { label: "콘센트", icon: "plug" },
  wifi: { label: "와이파이", icon: "wifi" },
  restroom: { label: "화장실", icon: "toilet" },
  water: { label: "음수대", icon: "droplet" },
  seating: { label: "앉을 곳", icon: "seat" },
  no_eyes: { label: "눈치 안 보임", icon: "eyeoff" },
  study_ok: { label: "공부 가능", icon: "pen" },
  quiet: { label: "조용함", icon: "book" },
  noise_level: { label: "소음", icon: "book" },
};

// 급해요 시나리오 표시 메타(제목/부제/아이콘/결과 헤더).
// 카테고리 집합·가중치·랭킹은 백엔드 /recommendations(ADR-0008)가 소유한다 — 프론트는 표시만.
export const SCENARIO_META: Record<
  Scenario,
  { title: string; sub: string; icon: IconName; resultTitle: string }
> = {
  restroom: {
    title: "화장실 급함",
    sub: "가까운 화장실 바로",
    icon: "toilet",
    resultTitle: "화장실 급함 · 지금 갈만한 순",
  },
  rest30: {
    title: "잠깐 쉬어갈 곳",
    sub: "시원한 실내에서 30분",
    icon: "seat",
    resultTitle: "잠깐 쉬어갈 곳 · 지금 갈만한 순",
  },
  rain: {
    title: "비 피할 곳",
    sub: "실내·지하로 대피",
    icon: "umbrella",
    resultTitle: "비 피할 곳 · 지금 갈만한 순",
  },
  focus: {
    title: "집중해서 공부",
    sub: "조용히 오래 앉을 곳",
    icon: "pen",
    resultTitle: "집중하기 좋은 곳 · 지금 갈만한 순",
  },
  longstay: {
    title: "오래 버틸 곳",
    sub: "더위 피해 장시간",
    icon: "seat",
    resultTitle: "오래 머물 곳 · 지금 갈만한 순",
  },
};
