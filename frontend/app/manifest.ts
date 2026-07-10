import type { MetadataRoute } from "next";

// PWA 매니페스트 → /manifest.webmanifest. 아이콘은 브랜드 캐릭터 PNG(any/maskable). background_color는
// 스플래시(크림)와 동일해 설치형 PWA 런치 시 이음새가 없다.
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "그늘 — 여름 생존 지도",
    short_name: "그늘",
    description: "지금 쉬어갈 그늘을 찾아드립니다. 폭염·장마철 무더위쉼터·화장실·음수대·도서관을 현재 위치에서.",
    start_url: "/",
    scope: "/",
    display: "standalone",
    orientation: "portrait",
    background_color: "#FFFBEB",
    theme_color: "#163c2f",
    lang: "ko",
    categories: ["navigation", "utilities", "lifestyle"],
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
