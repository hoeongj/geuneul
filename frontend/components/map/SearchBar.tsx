"use client";

import { useEffect, useRef, useState } from "react";
import { Icon } from "@/components/ui/Icon";
import { searchPlaces } from "@/lib/api";
import type { PlaceSearchResult } from "@/types/search";

// 지정 장소 검색(N5) — 카카오 키워드(백엔드 BFF). 입력 디바운스 300ms, 선택 시 지도 recenter는 부모가 처리.
// 반경 마커는 지도 이동 후 bounds 조회가 기존대로 채운다(BACKLOG N5).
export function SearchBar({
  coords,
  onSelect,
}: {
  coords: { lat: number; lng: number } | null;
  onSelect: (r: PlaceSearchResult) => void;
}) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<PlaceSearchResult[]>([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reqIdRef = useRef(0);
  const boxRef = useRef<HTMLDivElement>(null);

  // 디바운스 검색(2자 이상). 상태 변경은 전부 타임아웃 콜백 안에서만 한다(effect 본문 동기 setState 회피).
  // 2자 미만은 드롭다운 자체가 렌더에서 숨겨지므로(showDropdown 가드) 별도 초기화가 필요 없다.
  // 늦게 도착한 응답이 최신 입력을 덮지 않게 요청 id로 가드한다.
  useEffect(() => {
    const q = query.trim();
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (q.length < 2) return;
    debounceRef.current = setTimeout(async () => {
      const myId = ++reqIdRef.current;
      setLoading(true);
      try {
        const r = await searchPlaces(q, coords);
        if (myId === reqIdRef.current) {
          setResults(r);
          setSearched(true);
        }
      } catch {
        if (myId === reqIdRef.current) {
          setResults([]);
          setSearched(true);
        }
      } finally {
        if (myId === reqIdRef.current) setLoading(false);
      }
    }, 300);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query, coords]);

  // 바깥 탭/클릭이면 드롭다운 닫기(pointerdown으로 터치까지 커버).
  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onDown);
    return () => document.removeEventListener("pointerdown", onDown);
  }, [open]);

  const pick = (r: PlaceSearchResult) => {
    onSelect(r);
    setQuery(r.name);
    setOpen(false);
  };

  const clear = () => {
    setQuery("");
    setResults([]);
    setSearched(false);
    setOpen(false);
  };

  const showDropdown = open && query.trim().length >= 2;

  return (
    <div ref={boxRef} className="relative">
      <div className="flex h-11 w-full items-center gap-2.5 rounded-[14px] bg-white px-3.5 shadow-search">
        <span className="text-ink-3">
          <Icon name="search" size={18} />
        </span>
        <input
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          placeholder="지역·장소 검색"
          aria-label="지역·장소 검색"
          enterKeyHint="search"
          className="min-w-0 flex-1 bg-transparent text-[14px] text-ink placeholder:text-muted-2 focus:outline-none"
        />
        {query && (
          <button
            type="button"
            onClick={clear}
            aria-label="검색어 지우기"
            className="flex h-6 w-6 items-center justify-center rounded-full text-[13px] text-muted-2"
          >
            ✕
          </button>
        )}
      </div>

      {showDropdown && (
        <div className="absolute inset-x-0 top-[calc(100%+6px)] max-h-[46dvh] overflow-y-auto rounded-[14px] bg-white p-1.5 shadow-sheet">
          {loading || !searched ? (
            <p className="px-3 py-3 text-[13px] text-muted">검색 중…</p>
          ) : results.length === 0 ? (
            <p className="px-3 py-3 text-[13px] text-muted">검색 결과가 없어요</p>
          ) : (
            <ul className="flex flex-col">
              {results.map((r, i) => (
                <li key={`${r.name}-${r.lat}-${i}`}>
                  <button
                    type="button"
                    onClick={() => pick(r)}
                    className="flex w-full flex-col items-start gap-0.5 rounded-[10px] px-3 py-2.5 text-left active:bg-cream"
                  >
                    <span className="text-[14px] font-bold text-ink">{r.name}</span>
                    <span className="flex min-w-0 items-center gap-1.5 text-[11.5px] text-muted">
                      {r.category && (
                        <span className="shrink-0 rounded-full bg-mint px-1.5 py-px text-[10px] font-bold text-teal-deep">
                          {r.category}
                        </span>
                      )}
                      <span className="truncate">{r.roadAddress || r.address || ""}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
