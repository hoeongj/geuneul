package com.geuneul.domain.auth;

/** 사용자 권한 (users.role 컬럼과 1:1). 모더레이션(2차)은 ADMIN 전용. */
public enum Role {
    USER,
    ADMIN
}
