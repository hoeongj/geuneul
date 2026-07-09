"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { presignPhoto, uploadPhotoToS3 } from "./api";
import { ALLOWED_PHOTO_TYPES, MAX_PHOTO_BYTES, type PhotoPurpose } from "@/types/photo";

// 뷰포트 idle 재조회 등 과호출 방지용 디바운스 콜백.
export function useDebouncedCallback<A extends unknown[]>(fn: (...args: A) => void, delay: number) {
  const fnRef = useRef(fn);
  useEffect(() => {
    fnRef.current = fn;
  }, [fn]);

  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  return useCallback(
    (...args: A) => {
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => fnRef.current(...args), delay);
    },
    [delay],
  );
}

export type PhotoUploadState = "idle" | "uploading" | "done" | "error";

/**
 * 제보/후기 사진 슬롯 공용 훅 — presign 발급 → S3 직접 PUT → objectUrl 확보까지 한 번에 처리한다
 * (report/page.tsx, ReviewsSection.tsx 둘 다 이 훅을 쓴다). purpose로 report/review를 구분(백엔드가
 * review는 로그인을 요구 — 401이면 error 상태로 메시지를 남긴다).
 */
export function usePhotoUpload(purpose: PhotoPurpose) {
  const [state, setState] = useState<PhotoUploadState>("idle");
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const reset = useCallback(() => {
    setState("idle");
    setPreviewUrl(null);
    setObjectUrl(null);
    setErrorMessage(null);
  }, []);

  const pick = useCallback(
    async (file: File) => {
      if (!ALLOWED_PHOTO_TYPES.includes(file.type as (typeof ALLOWED_PHOTO_TYPES)[number])) {
        setState("error");
        setErrorMessage("jpg·png·webp 사진만 올릴 수 있어요");
        return;
      }
      if (file.size > MAX_PHOTO_BYTES) {
        setState("error");
        setErrorMessage("사진은 최대 8MB까지 올릴 수 있어요");
        return;
      }
      setState("uploading");
      setErrorMessage(null);
      setPreviewUrl(URL.createObjectURL(file));
      try {
        const presigned = await presignPhoto({ contentType: file.type, contentLength: file.size, purpose });
        await uploadPhotoToS3(presigned, file);
        setObjectUrl(presigned.objectUrl);
        setState("done");
      } catch {
        setState("error");
        setErrorMessage(purpose === "review" ? "로그인 후 다시 시도해 주세요" : "사진 업로드에 실패했어요");
      }
    },
    [purpose],
  );

  return { state, previewUrl, objectUrl, errorMessage, pick, reset };
}
