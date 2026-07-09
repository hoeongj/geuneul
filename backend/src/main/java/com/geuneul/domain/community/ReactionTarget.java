package com.geuneul.domain.community;

/** 리액션 대상 종류(다형 target). 존재 검증은 ReactionService가 종류별 리포지토리로 한다. */
public enum ReactionTarget {
    REVIEW, REPORT, COMMENT
}
