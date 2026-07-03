// 아이콘 원본 = 디자인 프로토타입의 svgIcon() (source/Geuneul.dc.html). 24 viewBox 라인 아이콘.
// 여기 한 곳에서 primitive(path/circle)로 정의해 React <Icon> 과 Kakao 마커 data-URI 가 함께 쓴다.

export type IconPrim = { path: string } | { circle: [number, number, number]; fill?: boolean };

// 전체 채움(fill) 아이콘 — 나머지는 stroke.
export const ICON_FILLED = new Set<string>(["nav", "bolt"]);

export const ICONS: Record<string, IconPrim[]> = {
  // 카테고리
  snow: [{ path: "M12 3v18" }, { path: "M3 12h18" }, { path: "M5.3 7 18.7 17" }, { path: "M18.7 7 5.3 17" }],
  toilet: [{ circle: [12, 6, 2.6] }, { path: "M7.4 21c0-5.2 2.1-9.2 4.6-9.2s4.6 4 4.6 9.2" }],
  tree: [{ circle: [12, 8.6, 5] }, { path: "M12 13.6V21" }],
  book: [
    { path: "M12 6.4C10 5 6.5 5 4.5 6v12c2-1 5.5-1 7.5 .4 2-1.4 5.5-1.4 7.5-.4V6c-2-1-5.5-1-7.5 .4z" },
    { path: "M12 6.4V18.4" },
  ],
  civic: [{ path: "M4 9.5 12 5l8 4.5" }, { path: "M5 20h14" }, { path: "M6.5 20v-8" }, { path: "M12 20v-8" }, { path: "M17.5 20v-8" }],
  stairs: [{ path: "M3 19h4v-3.5h4v-3.5h4v-3.5h5" }],
  // droplet: 프로토타입 svgIcon 의 water 키가 비어 있어(원본 누락) 표준 물방울 path 로 보강.
  droplet: [{ path: "M12 3.4c3.4 4 5.4 6.9 5.4 9.4a5.4 5.4 0 0 1-10.8 0c0-2.5 2-5.4 5.4-9.4z" }],
  dots: [{ circle: [5.5, 12, 1.7], fill: true }, { circle: [12, 12, 1.7], fill: true }, { circle: [18.5, 12, 1.7], fill: true }],
  // 피처
  plug: [{ path: "M9 3.5v3.2" }, { path: "M15 3.5v3.2" }, { path: "M6.6 6.7h10.8v2.4a5.4 5.4 0 0 1-10.8 0z" }, { path: "M12 14.5V21" }],
  wifi: [{ path: "M3 9.5a13 13 0 0 1 18 0" }, { path: "M6 12.7a8.5 8.5 0 0 1 12 0" }, { path: "M9 16a4 4 0 0 1 6 0" }, { circle: [12, 19, 1], fill: true }],
  seat: [{ path: "M5 12v6" }, { path: "M19 12v6" }, { path: "M5 14.2h14" }, { path: "M7 12V9.4A2.4 2.4 0 0 1 9.4 7h5.2A2.4 2.4 0 0 1 17 9.4V12" }],
  eyeoff: [
    { path: "M4 4l16 16" },
    { path: "M9.6 5.4A9.4 9.4 0 0 1 12 5.1c6 0 9.6 6.9 9.6 6.9a16 16 0 0 1-2.6 3.4" },
    { path: "M6.3 6.8A16 16 0 0 0 2.4 12S6 18.9 12 18.9a9.4 9.4 0 0 0 3.9-.8" },
    { path: "M9.9 9.9a3 3 0 0 0 4.2 4.2" },
  ],
  umbrella: [{ path: "M12 3.2a9 9 0 0 1 9 8.8H3a9 9 0 0 1 9-8.8z" }, { path: "M12 12v6.6a2.2 2.2 0 0 0 4.4 0" }],
  // UI
  chev: [{ path: "M9 5l7 7-7 7" }],
  chevLeft: [{ path: "M15 5l-7 7 7 7" }],
  mapicon: [{ path: "M9 4.5 3.6 6.8v12.6L9 17.1l6 2.3 5.4-2.3V6.8L15 9.1" }, { path: "M9 4.5v12.6" }, { path: "M15 9.1v10.3" }],
  bolt: [{ path: "M13 2.5 4.5 13.5H10l-1 8 9.5-11.5H13z" }],
  pen: [{ path: "M4.5 20h15" }, { path: "M14.8 4.7l4.5 4.5L9 19.5l-5 .9 1-4.9z" }],
  search: [{ circle: [11, 11, 7] }, { path: "M20.5 20.5 16 16" }],
  filter: [{ path: "M3.5 5.5h17l-6.6 7.7v5.3l-3.8-2v-3.3z" }],
  locate: [{ circle: [12, 12, 3] }, { circle: [12, 12, 7.5] }, { path: "M12 2.5V5" }, { path: "M12 19v2.5" }, { path: "M2.5 12H5" }, { path: "M19 12h2.5" }],
  nav: [{ path: "M12 2.5 5 20l7-3.2L19 20z" }],
  share: [{ circle: [18, 5, 2.5] }, { circle: [6, 12, 2.5] }, { circle: [18, 19, 2.5] }, { path: "M8.2 13.3l7.6 4.4" }, { path: "M15.8 6.3 8.2 10.7" }],
};
