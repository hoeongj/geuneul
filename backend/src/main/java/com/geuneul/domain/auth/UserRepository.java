package com.geuneul.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 소셜 로그인 자연키 조회 — 재로그인 upsert 판정. */
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
