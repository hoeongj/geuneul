import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import { SplashScreen } from "@/components/shell/SplashScreen";
import { Providers } from "./providers";
import "./globals.css";

// Pretendard Variable self-host(오프라인 PWA 셸·CSP 안정성). CDN 미사용.
const pretendard = localFont({
  src: "../public/fonts/PretendardVariable.woff2",
  display: "swap",
  weight: "100 900",
  variable: "--font-pretendard",
});

export const metadata: Metadata = {
  applicationName: "그늘",
  title: {
    default: "그늘 — 여름 생존 지도",
    template: "%s · 그늘",
  },
  description: "지금 쉬어갈 그늘을 찾아드립니다. 폭염·장마철 무더위쉼터·화장실·음수대·도서관을 현재 위치에서.",
  manifest: "/manifest.webmanifest",
  appleWebApp: {
    capable: true,
    statusBarStyle: "default",
    title: "그늘",
  },
  formatDetection: { telephone: false },
  icons: {
    icon: [
      { url: "/favicon.ico", sizes: "48x48", type: "image/x-icon" },
      { url: "/icon-192.png", sizes: "192x192", type: "image/png" },
      { url: "/icon-512.png", sizes: "512x512", type: "image/png" },
    ],
    apple: [{ url: "/apple-touch-icon.png", sizes: "180x180", type: "image/png" }],
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#f5f3ec",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko" className={pretendard.variable}>
      <body>
        <Providers>{children}</Providers>
        <SplashScreen />
      </body>
    </html>
  );
}
