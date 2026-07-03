import { ICON_FILLED, ICONS } from "./icon-paths";

// 카카오 MapMarker 용 SVG data-URI 생성. 디자인 마커 스펙:
// 흰 원(34px, 선택 38px) + 2.5px 링(정보부족=#9AA6A0 / 선택=#163C2F) + 카테고리 아이콘(#163C2F, 18) + 아래 삼각 꼬리.
// 마커 링은 회색 고정(정보 부족) — survival_score 3색은 아직 슬롯만(Reserved).
const RING_DEFAULT = "#9AA6A0";
const RING_SELECTED = "#163C2F";
const ICON_COLOR = "#163C2F";

function iconInner(name: string, color: string): string {
  const prims = ICONS[name] ?? ICONS.dots;
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

export function markerImage(iconName: string, selected = false): MarkerImage {
  const d = selected ? 38 : 34;
  const tail = 7;
  const w = d;
  const h = d + tail;
  const ring = selected ? RING_SELECTED : RING_DEFAULT;
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
