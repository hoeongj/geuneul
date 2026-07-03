import type { MetadataRoute } from "next";

// PWA 매니페스트 → /manifest.webmanifest. 아이콘은 SVG(any/maskable) — 설치 가능·해상도 독립.
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "그늘 — 여름 생존 지도",
    short_name: "그늘",
    description: "지금 쉬어갈 그늘을 찾아드립니다. 폭염·장마철 무더위쉼터·화장실·음수대·도서관을 현재 위치에서.",
    start_url: "/",
    scope: "/",
    display: "standalone",
    orientation: "portrait",
    background_color: "#f5f3ec",
    theme_color: "#163c2f",
    lang: "ko",
    categories: ["navigation", "utilities", "lifestyle"],
    icons: [
      { src: "/icons/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" },
      { src: "/icons/icon-maskable.svg", sizes: "any", type: "image/svg+xml", purpose: "maskable" },
    ],
  };
}
