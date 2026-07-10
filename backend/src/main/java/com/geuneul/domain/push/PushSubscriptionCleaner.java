package com.geuneul.domain.push;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소멸된 push 구독 정리(F2, ADR-0022) — {@link PushService}의 <b>비동기</b> 전송 콜백에서 호출된다.
 * 별도 빈으로 분리한 이유: 비동기 콜백은 요청 트랜잭션 밖에서 도므로, @Transactional 프록시가 적용되는
 * 다른 빈을 통해야 native DELETE(@Modifying)가 트랜잭션 안에서 실행된다(self-invocation은 프록시 우회).
 */
@Component
public class PushSubscriptionCleaner {

    private final PushSubscriptionRepository repository;

    public PushSubscriptionCleaner(PushSubscriptionRepository repository) {
        this.repository = repository;
    }

    /** push 서비스가 404/410을 반환한 endpoint 삭제(구독 소멸). */
    @Transactional
    public void remove(String endpoint) {
        repository.deleteByEndpoint(endpoint);
    }
}
