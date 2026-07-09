package com.geuneul.domain.photo;

import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 사진 업로드 용도 — S3 오브젝트 키 prefix 겸 인증 요구사항을 가른다(CLAUDE.md §1 UGC 2단 구조).
 * REPORT(제보): 익명 허용, 남용은 레이트리밋으로 방어. REVIEW(후기): 로그인 필요(§9 POST /reviews와 동일 정책).
 */
public enum PhotoPurpose {
    REPORT("report"),
    REVIEW("review");

    private final String prefix;

    PhotoPurpose(String prefix) {
        this.prefix = prefix;
    }

    /** S3 키 접두사(예: "report/{uuid}.jpg") — 용도별로 분리해두면 이후 라이프사이클/모더레이션 정책도 나눌 수 있다. */
    public String prefix() {
        return prefix;
    }

    /** 미지정(null/blank)이면 기본 REPORT — 제보 사진 슬롯이 더 빈번한 경로라 기본값으로 삼는다. */
    public static PhotoPurpose fromValue(String value) {
        if (value == null || value.isBlank()) {
            return REPORT;
        }
        return switch (value.trim().toLowerCase()) {
            case "report" -> REPORT;
            case "review" -> REVIEW;
            default -> throw new ResponseStatusException(BAD_REQUEST,
                    "purpose는 report 또는 review만 가능합니다: " + value);
        };
    }
}
