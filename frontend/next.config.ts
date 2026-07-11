import { createHash } from "node:crypto";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import withSerwistInit from "@serwist/next";
import type { NextConfig } from "next";

const PUBLIC_PRECACHE_EXCLUDES = [/^geuneul\.apk$/, /^brand(?:\/|$)/, /^sw\.js(?:\.map)?$/, /^swe-worker-.*\.js$/];

function publicPrecacheEntries(): Array<{ url: string; revision: string }> {
  const publicDir = path.join(process.cwd(), "public");
  const entries: Array<{ url: string; revision: string }> = [];

  const walk = (parts: string[] = []) => {
    const dir = path.join(publicDir, ...parts);
    for (const item of readdirSync(dir, { withFileTypes: true })) {
      const rel = [...parts, item.name].join("/");
      if (PUBLIC_PRECACHE_EXCLUDES.some((pattern) => pattern.test(rel))) continue;

      if (item.isDirectory()) {
        walk([...parts, item.name]);
        continue;
      }
      if (!item.isFile()) continue;

      const file = readFileSync(path.join(publicDir, ...parts, item.name));
      entries.push({ url: `/${rel}`, revision: createHash("md5").update(file).digest("hex") });
    }
  };

  walk();
  return entries.sort((a, b) => a.url.localeCompare(b.url));
}

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // 로컬: 상위(홈 디렉터리)의 stray lockfile 때문에 워크스페이스 루트가 오탐되는 것을 방지해
  // frontend/ 를 트레이싱 루트로 고정. Vercel(git build)에서는 rootDirectory 기반 네이티브 처리에
  // 맡긴다 — 여기서 override 하면 .next 출력 경로가 repo 루트로 오배치돼 배포가 ENOENT 로 실패한다.
  ...(process.env.VERCEL ? {} : { outputFileTracingRoot: process.cwd() }),
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Permissions-Policy", value: "geolocation=(self)" },
        ],
      },
    ];
  },
};

// Serwist(next-pwa 후계)는 webpack 플러그인 기반이라 Next 16 기본 Turbopack 과 함께 쓸 수 없다
// (2026 기준 Serwist+Turbopack 은 실험 단계). → 빌드/개발을 webpack 으로 고정(package.json scripts).
// 개발 모드에서는 SW 캐시가 HMR 을 방해하므로 비활성화.
const withSerwist = withSerwistInit({
  swSrc: "app/sw.ts",
  swDest: "public/sw.js",
  disable: process.env.NODE_ENV === "development",
  reloadOnOnline: true,
  exclude: [/^geuneul\.apk$/, /^brand\/.*$/],
  additionalPrecacheEntries: publicPrecacheEntries(),
});

export default withSerwist(nextConfig);
