"use client";

import { useEffect, useState } from "react";

// 브랜드 스플래시(로딩 화면) — 사용자가 만든 로딩화면 참고. 크림 배경 위 캐릭터가 부채/바람개비를 든 모습에서
// 가운데 요소를 실제로 회전시킨다(참고 이미지의 pinwheel). 최초 로드 시 잠깐 보이고 페이드아웃된다.
export function SplashScreen() {
  const [phase, setPhase] = useState<"show" | "fade" | "gone">("show");

  useEffect(() => {
    // 최소 노출(브랜딩) 후 페이드아웃 → 언마운트. 회전 모션은 CSS라 JS 없이도 동작.
    const fade = setTimeout(() => setPhase("fade"), 1000);
    const gone = setTimeout(() => setPhase("gone"), 1420);
    return () => {
      clearTimeout(fade);
      clearTimeout(gone);
    };
  }, []);

  if (phase === "gone") return null;

  return (
    <div
      aria-hidden
      className="fixed inset-0 z-[100] flex flex-col items-center justify-center transition-opacity duration-[400ms]"
      style={{
        background: "#FFFBEB", // 캐릭터 에셋 배경과 동일 크림 → 이음새 없음
        opacity: phase === "fade" ? 0 : 1,
        pointerEvents: phase === "fade" ? "none" : "auto",
      }}
    >
      <div className="relative w-[min(56vw,240px)]">
        {/* eslint-disable-next-line @next/next/no-img-element -- 스플래시 히어로(로컬 브랜드 에셋) */}
        <img src="/brand/character.png" alt="그늘" className="w-full select-none" draggable={false} />
        {/* 가운데 회전 모션 — 캐릭터의 손/무릎 위치(~68%)에 얹은 부채/바람개비 */}
        <div className="absolute left-1/2 top-[68%] w-[30%] -translate-x-1/2 -translate-y-1/2">
          <Pinwheel />
        </div>
      </div>

      <div className="mt-5 text-center">
        <div className="text-[26px] font-extrabold tracking-[-0.5px] text-teal">그늘</div>
        <div className="mt-1.5 text-[12.5px] font-semibold text-muted">활성화된 그늘 지역을 찾고 있습니다…</div>
      </div>
    </div>
  );
}

// 흰 원반 + 민트 부채살 + 티일 허브. animate-spin으로 회전(참고 이미지의 바람개비/부채 감성).
function Pinwheel() {
  return (
    <svg
      viewBox="0 0 100 100"
      className="w-full animate-spin drop-shadow-sm"
      style={{ animationDuration: "2.4s" }}
      role="img"
      aria-label="로딩 중"
    >
      <circle cx="50" cy="50" r="47" fill="#ffffff" stroke="#cfe9df" strokeWidth="2" />
      {Array.from({ length: 12 }).map((_, i) => (
        <path
          key={i}
          transform={`rotate(${i * 30} 50 50)`}
          d="M50 50 L55 11 Q50 7 45 11 Z"
          fill="#8fcdb9"
          opacity={0.3 + 0.7 * (i / 12)}
        />
      ))}
      <circle cx="50" cy="50" r="7.5" fill="#5bb298" />
    </svg>
  );
}
