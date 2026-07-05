import { ICON_FILLED, ICONS, type IconName, type IconPrim } from "./icon-paths";
import { GRADE_META } from "./survival";
import type { SurvivalGrade } from "@/types/place";

// 카카오 MapMarker 용 SVG data-URI 생성. 디자인 마커 스펙:
// 흰 원(34px, 선택 38px) + 2.5px 링(survival_score 3색: 초록/노랑/회색, 선택=#163C2F)
// + 카테고리 아이콘(#163C2F, 18) + 아래 삼각 꼬리.
// 링 색 = survival_score 등급(ADR-0007): GOOD 초록 / OKAY 노랑 / UNKNOWN 회색.
const RING_SELECTED = "#163C2F";
const ICON_COLOR = "#163C2F";

function iconInner(name: IconName, color: string): string {
  const prims: IconPrim[] = ICONS[name]; // IconPrim[]로 넓혀 circle 멤버의 fill? 접근 허용
  const filled = ICON_FILLED.has(name);
  return prims
    .map((p) => {
      if ("path" in p) {
        return filled
          ? `<path d="${p.path}" fill="${color}" stroke="none"/>`
          : `<path d="${p.path}" fill="none" stroke="${color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>`;
      }
      const [cx, cy, r] = p.circle;
      const solid = p.fill || filled;
      return solid
        ? `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${color}" stroke="none"/>`
        : `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${color}" stroke-width="1.8"/>`;
    })
    .join("");
}

export interface MarkerImage {
  src: string;
  size: { width: number; height: number };
  options: { offset: { x: number; y: number } };
}

export function markerImage(iconName: IconName, selected = false, grade: SurvivalGrade = "UNKNOWN"): MarkerImage {
  const d = selected ? 38 : 34;
  const tail = 7;
  const w = d;
  const h = d + tail;
  // 선택 시 브랜드 딥그린 링으로 강조, 아니면 등급 3색.
  const ring = selected ? RING_SELECTED : GRADE_META[grade].color;
  const shadow = selected ? 0.24 : 0.14;
  // 아이콘(24 viewBox)을 18px 로 축소해 원 중앙에 배치.
  const iconScale = 18 / 24;
  const iconOffset = (d - 18) / 2;
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">` +
    `<polygon points="${w / 2 - 5},${d - 3} ${w / 2 + 5},${d - 3} ${w / 2},${h - 1}" fill="#fff"/>` +
    `<circle cx="${w / 2}" cy="${d / 2}" r="${d / 2 - 1.5}" fill="#fff" stroke="${ring}" stroke-width="2.5"` +
    ` style="filter:drop-shadow(0 4px 8px rgba(0,0,0,${shadow}))"/>` +
    `<g transform="translate(${iconOffset},${iconOffset}) scale(${iconScale})">${iconInner(iconName, ICON_COLOR)}</g>` +
    `</svg>`;
  return {
    src: `data:image/svg+xml,${encodeURIComponent(svg)}`,
    size: { width: w, height: h },
    options: { offset: { x: w / 2, y: h } }, // 꼬리 끝이 좌표를 가리키도록
  };
}
