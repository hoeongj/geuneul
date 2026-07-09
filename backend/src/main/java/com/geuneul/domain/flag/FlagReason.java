package com.geuneul.domain.flag;

/** 신고 사유 (CLAUDE.md §8 flags.reason). 자유 텍스트는 {@code detail}로 별도 수용. */
public enum FlagReason {
    SPAM("스팸/광고"),
    FALSE_INFO("허위 정보"),
    OFFENSIVE("불쾌감/명예훼손"),
    OTHER("기타");

    private final String label;

    FlagReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
