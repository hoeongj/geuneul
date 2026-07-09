package com.geuneul.domain.photo;

import com.geuneul.domain.photo.dto.PhotoPresignRequest;
import com.geuneul.domain.photo.dto.PhotoPresignResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * presigner 서명 파라미터·검증 로직 단위 테스트. 실 AWS 자격증명·네트워크 없이도 동작한다 —
 * presign은 로컬 서명 연산이라 정적(가짜) 자격증명으로 충분하다(S3에 실제 요청을 보내지 않음).
 */
class PhotoServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-09T12:00:00Z"), ZoneOffset.UTC);

    private static S3Presigner fakePresigner() {
        return S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build();
    }

    private static PhotoService service(String bucket) {
        return new PhotoService(fakePresigner(), bucket, "ap-northeast-2", CLOCK);
    }

    @Test
    @DisplayName("report 용도 · 정상 요청은 presigned URL과 report/ 접두 키를 돌려준다")
    void presignReportSuccess() {
        PhotoService service = service("geuneul-photos-test");
        PhotoPresignResponse res = service.presign(
                new PhotoPresignRequest("image/jpeg", 2_000_000L, "report"), false);

        assertThat(res.key()).startsWith("report/").endsWith(".jpg");
        assertThat(res.uploadUrl()).startsWith("https://geuneul-photos-test.s3.ap-northeast-2.amazonaws.com/");
        assertThat(res.uploadUrl()).contains(res.key());
        assertThat(res.uploadUrl()).contains("X-Amz-Signature=");
        assertThat(res.objectUrl())
                .isEqualTo("https://geuneul-photos-test.s3.ap-northeast-2.amazonaws.com/" + res.key());
        assertThat(res.expiresAt()).isEqualTo(CLOCK.instant().atOffset(ZoneOffset.UTC).plusMinutes(2));
    }

    @Test
    @DisplayName("purpose 미지정이면 기본 report로 취급된다")
    void defaultsToReportPurpose() {
        PhotoPresignResponse res = service("bucket").presign(
                new PhotoPresignRequest("image/png", 100L, null), false);
        assertThat(res.key()).startsWith("report/").endsWith(".png");
    }

    @Test
    @DisplayName("review 용도 · 인증됨이면 review/ 접두 키로 발급된다")
    void presignReviewAuthenticated() {
        PhotoPresignResponse res = service("bucket").presign(
                new PhotoPresignRequest("image/webp", 500L, "review"), true);
        assertThat(res.key()).startsWith("review/").endsWith(".webp");
    }

    @Test
    @DisplayName("review 용도 · 미인증이면 401")
    void presignReviewUnauthenticatedIs401() {
        PhotoService service = service("bucket");
        assertThatThrownBy(() -> service.presign(new PhotoPresignRequest("image/jpeg", 100L, "review"), false))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(UNAUTHORIZED));
    }

    @Test
    @DisplayName("화이트리스트 밖 contentType이면 400")
    void unsupportedContentTypeIs400() {
        PhotoService service = service("bucket");
        assertThatThrownBy(() -> service.presign(new PhotoPresignRequest("image/gif", 100L, "report"), false))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(BAD_REQUEST));
    }

    @Test
    @DisplayName("8MB 초과 contentLength면 400")
    void tooLargeIs400() {
        PhotoService service = service("bucket");
        long tooLarge = 8L * 1024 * 1024 + 1;
        assertThatThrownBy(() -> service.presign(new PhotoPresignRequest("image/jpeg", tooLarge, "report"), false))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(BAD_REQUEST));
    }

    @Test
    @DisplayName("잘못된 purpose 값이면 400")
    void invalidPurposeIs400() {
        PhotoService service = service("bucket");
        assertThatThrownBy(() -> service.presign(new PhotoPresignRequest("image/jpeg", 100L, "flag"), false))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(BAD_REQUEST));
    }

    @Test
    @DisplayName("버킷 미설정이면 IllegalStateException(부팅은 되지만 호출 시점에 실패, JwtService와 동일 패턴)")
    void missingBucketFailsAtCallTime() {
        PhotoService service = service("");
        assertThatThrownBy(() -> service.presign(new PhotoPresignRequest("image/jpeg", 100L, "report"), false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("서로 다른 요청은 서로 다른 키(uuid)를 받는다 — 충돌 없는 랜덤 키")
    void keysAreUnique() {
        PhotoService service = service("bucket");
        PhotoPresignResponse a = service.presign(new PhotoPresignRequest("image/jpeg", 100L, "report"), false);
        PhotoPresignResponse b = service.presign(new PhotoPresignRequest("image/jpeg", 100L, "report"), false);
        assertThat(a.key()).isNotEqualTo(b.key());
    }
}
