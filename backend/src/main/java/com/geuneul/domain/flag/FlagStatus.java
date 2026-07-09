package com.geuneul.domain.flag;

/** 신고 처리 상태(라이프사이클). 접수는 항상 PENDING — 관리자가 RESOLVED/DISMISSED로 전이시킨다. */
public enum FlagStatus {
    PENDING,
    RESOLVED,
    DISMISSED
}
