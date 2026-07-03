import withSerwistInit from "@serwist/next";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // 로컬: 상위(홈 디렉터리)의 stray lockfile 때문에 워크스페이스 루트가 오탐되는 것을 방지해
  // frontend/ 를 트레이싱 루트로 고정. Vercel(git build)에서는 rootDirectory 기반 네이티브 처리에
  // 맡긴다 — 여기서 override 하면 .next 출력 경로가 repo 루트로 오배치돼 배포가 ENOENT 로 실패한다.
  ...(process.env.VERCEL ? {} : { outputFileTracingRoot: process.cwd() }),
};

// Serwist(next-pwa 후계)는 webpack 플러그인 기반이라 Next 16 기본 Turbopack 과 함께 쓸 수 없다
// (2026 기준 Serwist+Turbopack 은 실험 단계). → 빌드/개발을 webpack 으로 고정(package.json scripts).
// 개발 모드에서는 SW 캐시가 HMR 을 방해하므로 비활성화.
const withSerwist = withSerwistInit({
  swSrc: "app/sw.ts",
  swDest: "public/sw.js",
  disable: process.env.NODE_ENV === "development",
  reloadOnOnline: true,
});

export default withSerwist(nextConfig);
