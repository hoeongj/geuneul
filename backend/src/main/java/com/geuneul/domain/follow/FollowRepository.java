package com.geuneul.domain.follow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    /** 팔로워 수(공개 카운트) — followee 기준. idx_follows_followee 경로. */
    long countByFolloweeId(long followeeId);

    /** 뷰어가 이 작성자를 이미 팔로우 중인지(팔로우 버튼 상태). */
    boolean existsByFollowerIdAndFolloweeId(long followerId, long followeeId);

    /** 언팔로우(멱등) — 없으면 0건 삭제. 호출부 @Transactional 필요(FollowService). */
    long deleteByFollowerIdAndFolloweeId(long followerId, long followeeId);

    /**
     * 내 팔로잉 목록(N7, "나만" 봄) — follows × users(followee) 조인, 최신순.
     * 팔로우한 작성자의 닉네임·신뢰도·프로필로 재방문한다. 팔로워 목록 쿼리는 의도적으로 만들지 않는다(§0-9).
     */
    @Query(value = """
            SELECT u.id AS userId, u.nickname AS nickname, u.profile_image AS profileImage,
                   u.trust_score AS trustScore, f.created_at AS followedAt
            FROM follows f
            JOIN users u ON u.id = f.followee_id
            WHERE f.follower_id = :followerId
            ORDER BY f.created_at DESC
            LIMIT 100
            """, nativeQuery = true)
    List<FollowingView> findMyFollowing(@Param("followerId") long followerId);
}
