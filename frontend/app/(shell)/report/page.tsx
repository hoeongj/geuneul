"use client";

import { useRef, useState } from "react";
import { PlaceRow } from "@/components/place/PlaceRow";
import { Icon } from "@/components/ui/Icon";
import { IconChip } from "@/components/ui/IconChip";
import { ApiError } from "@/lib/api";
import { iconForCategory } from "@/lib/categories";
import { useGeo } from "@/lib/context/geo";
import { useToast } from "@/lib/context/toast";
import { DEFAULT_RADIUS } from "@/lib/geo";
import { usePhotoUpload } from "@/lib/hooks";
import { useCreateReport, useNearestPlace, useRadiusPlaces } from "@/lib/queries";
import { REPORT_GRID, REPORT_META } from "@/lib/reports";
import type { Place, ReportTypeKey } from "@/types/place";

// 제보 대상 장소 선택 시트 — 현재 위치 반경(기본) 리스트에서 고른다.
function PlacePicker({
  coords,
  onPick,
  onClose,
}: {
  coords: { lat: number; lng: number };
  onPick: (p: Place) => void;
  onClose: () => void;
}) {
  const { data, isLoading } = useRadiusPlaces(coords, DEFAULT_RADIUS);
  return (
    <div className="absolute inset-0 z-40 flex flex-col justify-end" role="dialog" aria-label="제보할 장소 선택">
      <button type="button" className="flex-1 bg-black/25" onClick={onClose} aria-label="닫기" />
      <div className="gn-overlay max-h-[70%] rounded-t-[22px] bg-white shadow-sheet">
        <div className="flex w-full justify-center pt-2.5 pb-1">
          <span className="h-[5px] w-[38px] rounded-full" style={{ background: "var(--color-sheet-handle)" }} />
        </div>
        <div className="px-4 pb-2 text-[15px] font-extrabold text-ink">어디에 대한 제보인가요?</div>
        <div className="overflow-y-auto px-4 pb-5" style={{ maxHeight: "50dvh" }}>
          {isLoading ? (
            <div className="py-8 text-center text-[13px] text-muted">주변 장소를 불러오는 중…</div>
          ) : (data ?? []).length === 0 ? (
            <div className="py-8 text-center text-[13px] text-muted">반경 {DEFAULT_RADIUS}m에 장소가 없어요.</div>
          ) : (
            (data ?? []).map((p) => (
              <PlaceRow key={p.id} place={p} onClick={() => onPick(p)} compact showStatus={false} />
            ))
          )}
        </div>
      </div>
    </div>
  );
}

export default function ReportPage() {
  const geo = useGeo();
  const { show } = useToast();
  const coords = { lat: geo.lat, lng: geo.lng };

  // 기본 제보 장소 = 현재 위치 최근접. "변경"으로 반경 내 다른 장소 선택.
  const nearest = useNearestPlace(coords);
  const [picked, setPicked] = useState<Place | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const target = picked ?? nearest.data ?? null;

  const [status, setStatus] = useState<ReportTypeKey | null>(null);
  const [comment, setComment] = useState("");
  const [anon, setAnon] = useState(true);
  const [done, setDone] = useState(false);

  const mutation = useCreateReport();
  const photo = usePhotoUpload("report");
  const fileInputRef = useRef<HTMLInputElement>(null);

  // done 상태의 라벨은 제출 버튼 JSX에서 별도 처리 → 여기선 미제출 경로만.
  const submitLabel = mutation.isPending
    ? "보내는 중…"
    : status
      ? "제보 보내기"
      : "지금 상태를 골라주세요";

  const onSubmit = () => {
    if (!status || !target || done || mutation.isPending) return;
    mutation.mutate(
      {
        placeId: target.id,
        reportType: status,
        comment: comment.trim() || undefined,
        photoUrl: photo.objectUrl ?? undefined,
        anonymous: anon,
        // 실측 GPS일 때만 좌표를 실어 보낸다(폴백 센터는 실위치가 아니라 verified 오판 방지).
        // 장소 100m 이내면 백엔드가 "방문 인증"(verified) 처리(ADR-0005 §④).
        ...(!geo.isFallback ? { lat: coords.lat, lng: coords.lng } : {}),
      },
      {
        onSuccess: () => {
          setDone(true);
          show("고마워요! 지금 상태가 반영됐어요");
        },
        onError: (err) => {
          if (err instanceof ApiError && err.status === 429) {
            show("제보가 너무 잦아요 · 잠시 후 다시 시도해 주세요");
          } else {
            show("제보 전송에 실패했어요 · 다시 시도해 주세요");
          }
        },
      },
    );
  };

  const resetForNext = () => {
    setDone(false);
    setStatus(null);
    setComment("");
    photo.reset();
    mutation.reset();
  };

  return (
    <div className="relative h-full overflow-y-auto px-4 pt-5 pb-6">
      <header className="mb-4">
        <h1 className="text-[23px] font-extrabold tracking-[-0.4px] text-ink">제보하기</h1>
        <p className="mt-1 text-[13px] text-ink-3">한 탭이면 끝. 지금 상태를 알려주세요.</p>
      </header>

      {/* 장소 컨텍스트 */}
      <div className="mb-4 flex items-center gap-3 rounded-[14px] border border-line-cream bg-white px-3.5 py-3">
        <IconChip icon={target ? iconForCategory(target.category) : "locate"} size={42} iconSize={20} />
        <div className="min-w-0 flex-1">
          <div className="text-[11px] text-muted">제보할 장소{geo.isFallback ? " · 위치 권한을 켜면 더 정확해요" : ""}</div>
          <div className="truncate text-[15px] font-bold text-ink">
            {target
              ? target.name
              : nearest.isLoading
                ? "주변 장소 찾는 중…"
                : nearest.isError
                  ? "장소를 못 불러왔어요 · ‘변경’으로 선택"
                  : "주변에 장소가 없어요 · ‘변경’으로 선택"}
          </div>
        </div>
        <button
          type="button"
          onClick={() => setPickerOpen(true)}
          className="min-h-[44px] shrink-0 px-1 text-[13px] font-bold text-teal"
        >
          변경
        </button>
      </div>

      {/* 상태 이모지 그리드 */}
      <div className="mb-4 grid grid-cols-3 gap-2.5">
        {REPORT_GRID.map((k) => {
          const meta = REPORT_META[k];
          const active = status === k;
          return (
            <button
              key={k}
              type="button"
              onClick={() => {
                setStatus(k);
                setDone(false);
              }}
              aria-pressed={active}
              className={
                "flex flex-col items-center gap-1.5 rounded-[16px] border py-4 " +
                (active ? "border-teal bg-mint-3 text-teal-deep" : "border-line-cream bg-white text-ink-2")
              }
            >
              <span className="text-[26px] leading-none">{meta.emoji}</span>
              <span className="text-[12px] font-bold">{meta.label}</span>
            </button>
          );
        })}
      </div>

      {/* 사진 + 코멘트 */}
      <div className="mb-4 flex gap-3">
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          hidden
          onChange={(e) => {
            const file = e.target.files?.[0];
            e.target.value = ""; // 같은 파일 재선택도 change가 뜨도록
            if (file) photo.pick(file);
          }}
        />
        <button
          type="button"
          onClick={() => (photo.state === "done" ? photo.reset() : fileInputRef.current?.click())}
          className="relative flex h-[76px] w-[76px] shrink-0 flex-col items-center justify-center gap-1 overflow-hidden rounded-[14px] border border-dashed border-line-dashed bg-white text-muted"
          aria-label={photo.state === "done" ? "사진 제거" : "사진 추가"}
        >
          {photo.previewUrl ? (
            // eslint-disable-next-line @next/next/no-img-element -- presigned S3 오브젝트라 next/image 도메인 화이트리스트 불필요한 blob/원격 URL 혼재
            <img src={photo.previewUrl} alt="" className="h-full w-full object-cover" />
          ) : (
            <>
              <Icon name="camera" size={20} />
              <span className="text-[10px] font-semibold">사진</span>
            </>
          )}
          {photo.state === "uploading" && (
            <span className="absolute inset-0 flex items-center justify-center bg-black/40 text-[10px] font-bold text-cream">
              올리는 중…
            </span>
          )}
          {photo.state === "done" && (
            <span className="absolute inset-0 flex items-center justify-center bg-black/40 text-[10px] font-bold text-cream">
              탭해서 제거
            </span>
          )}
        </button>
        <input
          value={comment}
          onChange={(e) => setComment(e.target.value.slice(0, 120))}
          placeholder="한 줄 코멘트 (선택)"
          aria-label="한 줄 코멘트"
          maxLength={120}
          className="h-[76px] flex-1 rounded-[14px] border border-line-cream bg-white px-3.5 text-[14px] text-ink placeholder:text-muted-2 focus:border-teal focus:outline-none"
        />
      </div>
      {photo.state === "error" && photo.errorMessage && (
        <p className="-mt-2.5 mb-4 text-[11.5px] text-red-500">{photo.errorMessage}</p>
      )}

      {/* 익명 토글 */}
      <div className="mb-5 flex items-center justify-between rounded-[14px] border border-line-cream bg-white px-3.5 py-3">
        <div className="min-w-0">
          <div className="text-[14px] font-bold text-ink">익명으로 제보</div>
          <div className="text-[11px] text-muted">로그인하면 신뢰도 배지가 붙어요 · P2</div>
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={anon}
          onClick={() => setAnon((v) => !v)}
          className="relative h-7 w-12 shrink-0 rounded-full transition-colors"
          style={{ background: anon ? "var(--color-teal)" : "var(--color-toggle-off)" }}
        >
          <span
            className="absolute top-0.5 h-6 w-6 rounded-full bg-white shadow transition-transform"
            style={{ left: 2, transform: anon ? "translateX(20px)" : "translateX(0)" }}
          />
        </button>
      </div>

      {/* 제출 — 사진이 올라가는 중이면 잠깐 막는다(빈 photoUrl로 먼저 나가버리는 것을 방지) */}
      <button
        type="button"
        onClick={done ? resetForNext : onSubmit}
        disabled={(!status || !target || mutation.isPending || photo.state === "uploading") && !done}
        className="h-[52px] w-full rounded-[14px] text-[15px] font-bold text-cream disabled:text-ink-3"
        style={{
          background:
            done ? "var(--color-teal)" : status && target && !mutation.isPending ? "var(--color-forest)" : "var(--color-btn-disabled)",
        }}
      >
        {done ? "고마워요! 제보 완료 · 한 번 더" : photo.state === "uploading" ? "사진 올리는 중…" : submitLabel}
      </button>
      <p className="mt-2.5 text-center text-[11px] text-muted">휘발성 제보예요 · 시간이 지나면 자동으로 사라져요</p>

      {pickerOpen && (
        <PlacePicker
          coords={coords}
          onClose={() => setPickerOpen(false)}
          onPick={(p) => {
            setPicked(p);
            setPickerOpen(false);
            setDone(false);
          }}
        />
      )}
    </div>
  );
}
