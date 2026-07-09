package com.geuneul.domain.photo;

import com.geuneul.domain.photo.dto.PhotoPresignRequest;
import com.geuneul.domain.photo.dto.PhotoPresignResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 제보/후기 사진 업로드 — S3 presigned PUT URL 발급(CLAUDE.md §7·§9 POST /photos/presign).
 * 백엔드는 파일 바이트를 절대 거치지 않는다: 브라우저가 이 서비스가 서명한 URL로 S3에 직접 PUT한다.
 *
 * <p>제약(둘 다 presign 시점에 강제 — 업로드 후가 아니라 업로드 전에 걸러진다):
 * <ul>
 *   <li><b>타입</b>: {@link #ALLOWED_CONTENT_TYPES} 화이트리스트만. 서명된 Content-Type 헤더라
 *       브라우저가 실제 PUT에서 다른 타입을 보내면 S3가 서명 불일치로 거부한다.</li>
 *   <li><b>크기</b>: {@link #MAX_UPLOAD_BYTES} 초과면 presign 자체를 거부. 서명에 Content-Length를
 *       실어(PutObjectRequest#contentLength) 실제 PUT이 다른 크기를 보내면 마찬가지로 거부된다.</li>
 * </ul>
 * purpose=REVIEW는 후기(§9 POST /reviews)와 동일하게 로그인을 요구한다(휴대폰 카메라로 즉시 찍는
 * 제보와 달리 후기는 이미 로그인 UX를 통과한 뒤라 마찰 비용이 없다 — WORKLOG 근거).
 */
@Service
public class PhotoService {

    /** MIME 타입 → 확장자. 휴대폰 카메라/일반 이미지 편집기가 흔히 만드는 3종으로 제한(HEIC 등은 클라 변환 후 업로드). */
    static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    /** 8MB — 휴대폰 사진 1장 기준 여유치. presigned PUT 크기 상한은 서명된 Content-Length로 강제(클래스 주석). */
    static final long MAX_UPLOAD_BYTES = 8L * 1024 * 1024;

    /** 짧게: presign 발급 직후 바로 업로드하는 흐름이라 길게 열어둘 이유가 없다(탈취된 URL의 악용 창 최소화). */
    private static final Duration SIGNATURE_DURATION = Duration.ofMinutes(2);

    private final S3Presigner presigner;
    private final String bucket;
    private final String region;
    private final Clock clock;

    public PhotoService(S3Presigner presigner,
                        @Value("${aws.s3.bucket:}") String bucket,
                        @Value("${aws.s3.region:ap-northeast-2}") String region,
                        Clock clock) {
        this.presigner = presigner;
        this.bucket = bucket;
        this.region = region;
        this.clock = clock;
    }

    /** @param authenticated 요청에 유효 JWT가 있었는지 — purpose=REVIEW면 필수. */
    public PhotoPresignResponse presign(PhotoPresignRequest request, boolean authenticated) {
        String extension = ALLOWED_CONTENT_TYPES.get(request.contentType());
        if (extension == null) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "지원하지 않는 이미지 타입이에요. image/jpeg, image/png, image/webp만 업로드할 수 있어요.");
        }
        if (request.contentLength() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "사진이 너무 커요. 최대 " + (MAX_UPLOAD_BYTES / 1024 / 1024) + "MB까지 업로드할 수 있어요.");
        }

        PhotoPurpose purpose = PhotoPurpose.fromValue(request.purpose());
        if (purpose == PhotoPurpose.REVIEW && !authenticated) {
            throw new ResponseStatusException(UNAUTHORIZED, "후기 사진은 로그인 후 업로드할 수 있어요.");
        }
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalStateException(
                    "S3_BUCKET_NAME이 설정되지 않았습니다. 규칙 D: env/SSM으로만 주입.");
        }

        String key = purpose.prefix() + "/" + UUID.randomUUID() + "." + extension;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(request.contentType())
                .contentLength(request.contentLength())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(SIGNATURE_DURATION)
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);

        // 버킷은 비공개(퍼블릭 액세스 블록) — MVP는 오브젝트 URL을 그대로 저장한다(스키마 기존 photo_url/photos_json
        // 그대로 재사용). presigned GET/CloudFront로 실제 뷰잉을 여는 건 스코프 밖(HANDOFF 다음 조각).
        String objectUrl = "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(SIGNATURE_DURATION);

        return new PhotoPresignResponse(presigned.url().toExternalForm(), objectUrl, key, expiresAt);
    }
}
