package com.geuneul.domain.follow;

import java.time.Instant;

/**
 * 내 팔로잉 목록 투영(N7) — follows × users(followee) 조인. 내가 팔로우한 작성자를 다시 찾아가기 위한 정보.
 * "나만" 보는 목록이다(§0-9 — 팔로워 목록은 없음). TIMESTAMPTZ는 {@link Instant}로 받는다(TS-016).
 */
public interface FollowingView {

    Long getUserId();

    String getNickname();

    String getProfileImage();

    Double getTrustScore();

    Instant getFollowedAt();
}
